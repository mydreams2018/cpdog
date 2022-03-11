package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.NioWorkServerSocket;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NioWorkServerSocketImpl implements NioWorkServerSocket {
    private NioWorkServerSocketImpl(){}
    private final static ThreadGroup threadGroup = new WorkThreadGroup("workServer");
    private final static AtomicInteger atomicInteger = new AtomicInteger(0);

    private final List<ChannelInHandler<?,?>> channelInHandlers = new ArrayList<>();
    private final TreeMap<Integer,ByteBuffer> treeMap = new TreeMap<>();
    private final HashMap<SocketOption<?>,Object> optionMap = new HashMap<>();
    private Thread workThreads;
    private Selector selector;
    {
        channelInHandlers.add(new CacheChannelInHandler());
    }

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
            try{
                while(NioWorkServerSocketImpl.this.selector.select()>=0){
                    Set<SelectionKey> selectionKeys = NioWorkServerSocketImpl.this.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey next = iterator.next();
                        iterator.remove();
                        handler(next);
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        public void handler(SelectionKey next){
            SocketChannel clientChannel =null;
            try{
                clientChannel = (SocketChannel) next.channel();
                if(next.isValid() && next.isReadable()){
                    if(!clientChannel.isConnectionPending()){
                        System.out.println("注意客户端服务关掉了");
                        clientChannel.close();
                        return;
                    }
                    //每一个SocketChannel对应一个唯一的缓冲区
                    //todo 缓冲区大小问题 一次能不能读完的问题
                    ByteBuffer byteBuffer = treeMap.get(clientChannel.hashCode());
                    if(byteBuffer == null){
                        byteBuffer = ByteBuffer.allocate(8192);
                        treeMap.put(clientChannel.hashCode(),byteBuffer);
                    }
                    int read = clientChannel.read(byteBuffer);
                    // read > 0 有数据  ==-1 表示流关闭  ==0 不管
                    while(read > 0){
                        read = clientChannel.read(byteBuffer);
                    }
                    if(read == -1){
                        clientChannel.close();
                    }


                }else{
                    System.out.println(clientChannel.getRemoteAddress()+":客户端监听类型异常");
                }
            }catch (Exception e){
                e.printStackTrace();
                if(clientChannel!=null){
                    try {
                        clientChannel.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
                treeMap.remove(clientChannel.hashCode());
            }
        }

    }

}
