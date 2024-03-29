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

/*
 * BOSS服务 接受套接字连接
 * */
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
        this.workServerSockets = workServerSockets;
        this.chooseWorkServer = chooseWorkServer;
        this.serverSocketChannel.bind(local, backlog);
        this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        this.bossThreads.start();
        return this;
    }

    @Override
    public NioBossServerSocketImpl start(SocketAddress local, NioWorkServerSocket[] workServerSockets, ChooseWorkServer chooseWorkServer) throws IOException {
        this.workServerSockets = workServerSockets;
        this.chooseWorkServer = chooseWorkServer;
        this.serverSocketChannel.bind(local);
        this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        this.bossThreads.start();
        return this;
    }

    /*
     * 监听OP_ACCEPT事件 等待客户端套接字连接
     * */
    private final class BossRunnable implements Runnable {

        @Override
        public void run() {
            NioBossServerSocketImpl.LOGGER.info("boss服务启动");
            try {
                while (NioBossServerSocketImpl.this.selector.select() >= 0) {
                    Set<SelectionKey> selectionKeys = NioBossServerSocketImpl.this.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey next = iterator.next();
                        iterator.remove();
                        if (next.isValid() && next.isAcceptable()) {
                            ServerSocketChannel serChannel = (ServerSocketChannel) next.channel();
                            addTask(serChannel);
                        } else {
                            NioBossServerSocketImpl.LOGGER.info("Boss事件类型错误");
                        }
                    }
                }
            } catch (Exception e) {
                NioBossServerSocketImpl.LOGGER.error("Boss线程挂掉", e);
            }
        }

        private void addTask(ServerSocketChannel serverChannel) throws IOException {
            SocketChannel accept = serverChannel.accept();
            if (accept != null) {
                ShakeHands.THREAD_POOL_EXECUTOR.execute(new TLSRunnable(accept));
            }
        }
    }

    /*
     * 1.完成客户端套接字连接
     * 2.完成TLS握手的过程
     * 3.选择一个WORK服务对象注册OP_READ事件
     * 4.启动WORK服务监听套接字的OP_READ事件
     * */
    private final class TLSRunnable implements Runnable {

        private final SocketChannel tlsSocketChannel;

        private TLSRunnable(SocketChannel channel) {
            this.tlsSocketChannel = channel;
        }

        @Override
        public void run() {
            TLSSocketLink sslEngine;
            try {
                if (this.tlsSocketChannel.isConnected() || this.tlsSocketChannel.finishConnect()) {
                    NioWorkServerSocket choose = NioBossServerSocketImpl.this.chooseWorkServer.choose(NioBossServerSocketImpl.this.workServerSockets);
                    this.tlsSocketChannel.configureBlocking(false);
                    choose.setOption(this.tlsSocketChannel);
                    //TLS握手
                    sslEngine = CpDogSSLContext.getSSLEngine(this.tlsSocketChannel);
                    if (sslEngine != null) {
                        SelectionKey selectionKey = this.tlsSocketChannel.register(choose.getSelector(), SelectionKey.OP_READ, sslEngine);
                        if (sslEngine.getInSrc().position() > 0) {
                            //说明有多的数据需要处理.添加到work的初始化队列中去
                            choose.getTlsInitKey().add(selectionKey);
                        }
                        NioBossServerSocketImpl.LOGGER.info("连接成功{}", this.tlsSocketChannel.getRemoteAddress());
                        Thread.State state = choose.getWorkThreads().getState();
                        if (state == Thread.State.NEW) {
                            choose.runWorkThread();
                            NioBossServerSocketImpl.LOGGER.info("启动{}", choose.getWorkThreads().getName());
                        }
                        choose.getSelector().wakeup();
                    }
                } else {
                    this.tlsSocketChannel.close();
                    NioBossServerSocketImpl.LOGGER.info("连接失败{}", this.tlsSocketChannel.getRemoteAddress());
                }
            } catch (Exception e) {
                NioBossServerSocketImpl.LOGGER.error("连接失败", e);
                try {
                    this.tlsSocketChannel.close();
                } catch (IOException ioException) {
                    NioBossServerSocketImpl.LOGGER.error("close失败{}", ioException.getLocalizedMessage());
                }
            }
        }
    }
}
