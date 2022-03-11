package cn.kungreat.boot.impl;

import cn.kungreat.boot.NioWorkServerSocket;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NioWorkServerSocketImpl implements NioWorkServerSocket {
    private NioWorkServerSocketImpl(){}
    private final static ThreadGroup threadGroup = new WorkThreadGroup("workServer");
    private final static AtomicInteger atomicInteger = new AtomicInteger(0);


    private final TreeMap<Integer,ByteBuffer> treeMap = new TreeMap<>();
    private final HashMap<SocketOption<?>,Object> optionMap = new HashMap<>();
    private Thread workThreads;
    private Selector selector;

    public static NioWorkServerSocket create(){
        return new NioWorkServerSocketImpl();
    }

    private static String getThreadName() {
        int i = atomicInteger.addAndGet(1);
        return "work-thread-" + i;
    }

    @Override
    public <T> NioWorkServerSocket setOption​(SocketOption<T> name, T value) throws IOException {
        optionMap.put(name,value);
        return this;
    }

    @Override
    public void setOption​(SocketChannel channel) throws IOException {
        Set<Map.Entry<SocketOption<?>, Object>> entries = optionMap.entrySet();
        for(Map.Entry<SocketOption<?>, Object> entry: entries){
            SocketOption name = entry.getKey();
            channel.setOption(name,entry.getValue());
        }
    }

    @Override
    public NioWorkServerSocket buildThread() {
        this.workThreads = new Thread(NioWorkServerSocketImpl.threadGroup, new NioWorkServerSocketImpl.WorkRunable(), getThreadName());
        return this;
    }

    @Override
    public NioWorkServerSocket buildSelector() throws IOException {
        this.selector = Selector.open();
        return this;
    }

    @Override
    public NioWorkServerSocket start() {
        this.workThreads.start();
        return this;
    }

    public Thread getWorkThreads() {
        return workThreads;
    }

    public Selector getSelector() {
        return selector;
    }


    private final class WorkRunable implements Runnable{
        @Override
        public void run() {
            System.out.println("work 启动");
        }
    }

}
