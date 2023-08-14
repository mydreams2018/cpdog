package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChooseWorkServer;
import cn.kungreat.boot.NioBossServerSocket;
import cn.kungreat.boot.NioWorkServerSocket;
import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.tls.ShakeHands;
import cn.kungreat.boot.tls.TLSSocketLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class NioBossServerSocketImpl implements NioBossServerSocket {
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private static final ThreadGroup BOSS_THREAD_GROUP = new BossThreadGroup("bossServer");
    private Thread bossThreads;
    private final static AtomicInteger ATOMIC_INTEGER = new AtomicInteger(0);
    private NioWorkServerSocket[] workServerSockets;
    private ChooseWorkServer chooseWorkServer;
    private static final Logger LOGGER = LoggerFactory.getLogger(NioBossServerSocketImpl.class);
    private NioBossServerSocketImpl() {
    }

    /*
     *@Description 创建BOSS对象
     *@Date 2022/2/28
     *@Time 16:46
     */
    public static NioBossServerSocket create() {
        return new NioBossServerSocketImpl();
    }

    private static String getThreadName() {
        int i = ATOMIC_INTEGER.addAndGet(1);
        return "boss-thread-" + i;
    }

    @Override
    public NioBossServerSocket buildChannel() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        this.selector = Selector.open();
        this.serverSocketChannel = serverSocketChannel;
        return this;
    }

    @Override
    public <T> NioBossServerSocket setOption(SocketOption<T> name, T value) throws IOException {
        this.serverSocketChannel.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return this.serverSocketChannel.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return this.serverSocketChannel.supportedOptions();
    }

    @Override
    public NioBossServerSocketImpl buildThread() {
        this.bossThreads = new Thread(BOSS_THREAD_GROUP, new BossRunnable(), getThreadName());
        this.bossThreads.setPriority(Thread.MAX_PRIORITY);
        return this;
    }

    @Override
    public NioBossServerSocketImpl start(SocketAddress local, int backlog, NioWorkServerSocket[] workServerSockets, ChooseWorkServer chooseWorkServer) throws IOException {
        this.workServerSockets=workServerSockets;
        this.chooseWorkServer=chooseWorkServer;
        this.serverSocketChannel.bind(local, backlog);
        this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        this.bossThreads.start();
        return this;
    }

    @Override
    public NioBossServerSocketImpl start(SocketAddress local, NioWorkServerSocket[] workServerSockets, ChooseWorkServer chooseWorkServer) throws IOException {
        this.workServerSockets=workServerSockets;
        this.chooseWorkServer=chooseWorkServer;
        this.serverSocketChannel.bind(local);
        this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        this.bossThreads.start();
        return this;
    }

    private final class BossRunnable implements Runnable{

        private void accept(ServerSocketChannel serChannel){
            SocketChannel accept = null;
            try{
                accept = serChannel.accept();
                if(accept != null && accept.finishConnect()){
                    NioWorkServerSocket choose = NioBossServerSocketImpl.this.chooseWorkServer.choose(NioBossServerSocketImpl.this.workServerSockets);
                    accept.configureBlocking(false);
                    choose.setOption(accept);
                    accept.register(choose.getSelector(),SelectionKey.OP_READ);
                    choose.getSelector().wakeup();
                    NioBossServerSocketImpl.LOGGER.info("连接成功{}",accept.getRemoteAddress());
                    Thread.State state = choose.getWorkThreads().getState();
                    if(state.equals(Thread.State.NEW)){
                        choose.getWorkThreads().start();
                        NioBossServerSocketImpl.LOGGER.info("启动{}",choose.getWorkThreads().getName());
                    }
                }else if(accept != null){
                    accept.close();
                    NioBossServerSocketImpl.LOGGER.info("连接失败{}",accept.getRemoteAddress());
                }
            }catch (Exception e){
                e.printStackTrace();
                NioBossServerSocketImpl.LOGGER.error("连接失败{}",e.getLocalizedMessage());
                if(accept != null){
                    try {
                        accept.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                        NioBossServerSocketImpl.LOGGER.error("close失败{}",ioException.getLocalizedMessage());
                    }
                }
            }
        }

        @Override
        public void run() {
            NioBossServerSocketImpl.LOGGER.info("boss服务启动");
            try {
                while(NioBossServerSocketImpl.this.selector.select() >= 0){
                    Set<SelectionKey> selectionKeys = NioBossServerSocketImpl.this.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey next = iterator.next();
                        iterator.remove();
                        SelectableChannel channel = next.channel();
                        if(next.isValid() && next.isAcceptable()){
                            ServerSocketChannel serChannel = (ServerSocketChannel) channel;
//                            accept(serChannel);
                            addTask(serChannel);
                        }else{
                            NioBossServerSocketImpl.LOGGER.info("Boss事件类型错误");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                NioBossServerSocketImpl.LOGGER.error("Boss线程挂掉");
                NioBossServerSocketImpl.LOGGER.error(e.getLocalizedMessage());
            }
            run();
        }

        private void addTask(ServerSocketChannel serverChannel) throws IOException {
            SocketChannel accept = serverChannel.accept();
            if(accept != null){
                ShakeHands.THREAD_POOL_EXECUTOR.execute(new TLSRunnable(accept));
            }
        }
    }

    private final class TLSRunnable implements Runnable{

        private final SocketChannel tlsSocketChannel;

        private TLSRunnable(SocketChannel channel){
            this.tlsSocketChannel=channel;
        }

        @Override
        public void run() {
            TLSSocketLink sslEngine;
            try{
                if(this.tlsSocketChannel.finishConnect()){
                    NioWorkServerSocket choose = NioBossServerSocketImpl.this.chooseWorkServer.choose(NioBossServerSocketImpl.this.workServerSockets);
                    this.tlsSocketChannel.configureBlocking(false);
                    choose.setOption(this.tlsSocketChannel);
                    //TLS握手
                    sslEngine = CpDogSSLContext.getSSLEngine(this.tlsSocketChannel);
                    if(sslEngine != null){
                        SelectionKey selectionKey = this.tlsSocketChannel.register(choose.getSelector(), SelectionKey.OP_READ, sslEngine);
                        if(sslEngine.getInSrc().position() >0){
                            //说明有多的数据需要处理.添加到work的初始化队列中去.
                            choose.getTlsInitKey().add(selectionKey);
                        }
                        NioBossServerSocketImpl.LOGGER.info("连接成功{}",this.tlsSocketChannel.getRemoteAddress());
                        Thread.State state = choose.getWorkThreads().getState();
                        if(state.equals(Thread.State.NEW)){
                            choose.getWorkThreads().start();
                            NioBossServerSocketImpl.LOGGER.info("启动{}",choose.getWorkThreads().getName());
                        }
                        choose.getSelector().wakeup();
                    }
                }else{
                    this.tlsSocketChannel.close();
                    NioBossServerSocketImpl.LOGGER.info("连接失败{}",this.tlsSocketChannel.getRemoteAddress());
                }
            }catch (Exception e){
                e.printStackTrace();
                NioBossServerSocketImpl.LOGGER.error("连接失败{}",e.getLocalizedMessage());
                    try {
                        this.tlsSocketChannel.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                        NioBossServerSocketImpl.LOGGER.error("close失败{}",ioException.getLocalizedMessage());
                    }
            }
        }
    }
}
