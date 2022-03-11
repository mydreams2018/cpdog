package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChooseWorkServer;
import cn.kungreat.boot.NioBossServerSocket;
import cn.kungreat.boot.NioWorkServerSocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NioBossServerSocketImpl implements NioBossServerSocket {
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private static ThreadGroup threadGroup = new BossThreadGroup("bossServer");
    private Thread bossThreads;
    private final static AtomicInteger atomicInteger = new AtomicInteger(0);
    private NioWorkServerSocket[] workServerSockets;
    private ChooseWorkServer chooseWorkServer;
    private Logger logger;
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
        int i = atomicInteger.addAndGet(1);
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
    public <T> NioBossServerSocket setOption​(SocketOption<T> name, T value) throws IOException {
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
        this.bossThreads = new Thread(threadGroup, new BossRunable(), getThreadName());
        this.bossThreads.setPriority(Thread.MAX_PRIORITY);
        return this;
    }

    @Override
    public NioBossServerSocket setLogger(Logger logger) {
        this.logger=logger;
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

    private final class BossRunable implements Runnable{

        private void accept(ServerSocketChannel serChannel){
            SocketChannel accept = null;
            try{
                accept = serChannel.accept();
                if(accept != null && accept.finishConnect()){
                    NioWorkServerSocket choose = NioBossServerSocketImpl.this.chooseWorkServer.choose(NioBossServerSocketImpl.this.workServerSockets);
                    accept.configureBlocking(false);
                    choose.setOption​(accept);
                    accept.register(choose.getSelector(),SelectionKey.OP_READ);
                    NioBossServerSocketImpl.this.logger.log(Level.SEVERE,accept.getRemoteAddress()+"连接成功");
                    Thread.State state = choose.getWorkThreads().getState();
                    if(state.equals(Thread.State.NEW)){
                        choose.getWorkThreads().start();
                        NioBossServerSocketImpl.this.logger.log(Level.INFO,choose.getWorkThreads().getName()+":启动");
                    }
                }else{
                    accept.close();
                    NioBossServerSocketImpl.this.logger.log(Level.WARNING,accept.getRemoteAddress()+"连接失败");
                }
            }catch (Exception e){
                e.printStackTrace();
                NioBossServerSocketImpl.this.logger.log(Level.WARNING,e.getLocalizedMessage()+"连接失败");
                if(accept != null){
                    try {
                        accept.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                        NioBossServerSocketImpl.this.logger.log(Level.WARNING,ioException.getLocalizedMessage()+"close失败");
                    }
                }
            }
        }

        @Override
        public void run() {
            NioBossServerSocketImpl.this.logger.log(Level.INFO,"boss服务启动");
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
                            accept(serChannel);
                        }else{
                            NioBossServerSocketImpl.this.logger.log(Level.WARNING,"Boss事件类型错误");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                NioBossServerSocketImpl.this.logger.log(Level.WARNING,"Boss线程挂掉");
                NioBossServerSocketImpl.this.logger.log(Level.WARNING,e.getLocalizedMessage());
            }
            run();
        }
    }
}
