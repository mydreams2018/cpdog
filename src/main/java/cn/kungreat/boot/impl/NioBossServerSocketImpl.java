package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChooseWorkServer;
import cn.kungreat.boot.NioBossServerSocket;
import cn.kungreat.boot.NioWorkServerSocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class NioBossServerSocketImpl implements NioBossServerSocket {
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private ThreadGroup threadGroup;
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
        BossThreadGroup bossServer = new BossThreadGroup("bossServer");
        this.bossThreads = new Thread(bossServer, new BossRunable(), getThreadName());
        this.threadGroup = bossServer;
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
        @Override
        public void run() {
            try {
                while(selector.select() >= 0){
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey next = iterator.next();
                        iterator.remove();
                        NioWorkServerSocket choose = NioBossServerSocketImpl.this.chooseWorkServer.choose(NioBossServerSocketImpl.this.workServerSockets);

                        //TODO
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
