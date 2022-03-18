package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.NioWorkServerSocket;

import java.io.IOException;
import java.lang.reflect.Method;
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
    private int bufferSize;
    private static final List<ChannelInHandler<?,?>> channelInHandlers = new ArrayList<>();
    private final TreeMap<Integer,ByteBuffer> treeMap = new TreeMap<>();
    private final ByteBuffer outBuf = ByteBuffer.allocate(8192);
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

    public static void addChannelInHandlers(ChannelInHandler<?,?> channelInHandler){
        channelInHandlers.add(channelInHandler);
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
    public NioWorkServerSocket buildSelector(int bufferSize) throws IOException {
        this.selector = Selector.open();
        this.bufferSize = bufferSize;
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

    public ByteBuffer getOutBuf() {
        return outBuf;
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
            run();
        }

        public void handler(SelectionKey next){
            SocketChannel clientChannel = null;
            try{
                clientChannel = (SocketChannel) next.channel();
                if(next.isValid() && next.isReadable()){
                    ByteBuffer byteBuffer = treeMap.get(clientChannel.hashCode());
                    if(byteBuffer == null){
                        byteBuffer = ByteBuffer.allocate(NioWorkServerSocketImpl.this.bufferSize);
                        treeMap.put(clientChannel.hashCode(),byteBuffer);
                    }
                    int read = clientChannel.read(byteBuffer);
                    while(read > 0){
                        read = clientChannel.read(byteBuffer);
                    }
                    if(read == -1){
                        System.out.println(clientChannel.getRemoteAddress()+"自动关闭了");
                        clientChannel.close();
                    }
                    if(!byteBuffer.hasRemaining()){
                        throw new RuntimeException(clientChannel.getRemoteAddress()+"没有了缓存空间了,强制关掉连接");
                    }
                    Object o = runInHandlers(clientChannel, byteBuffer);

                }else{
                    System.out.println(clientChannel.getRemoteAddress()+":客户端监听类型异常");
                }
            }catch (Exception e){
                if(clientChannel!=null){
                    try {
                        clientChannel.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
                e.printStackTrace();
                treeMap.remove(clientChannel.hashCode());
            }
        }

        private Object runInHandlers(final SocketChannel clientChannel,final ByteBuffer byteBuffer) {
            Object linkIn = byteBuffer;
            Exception exception = null;
            for(int x=0;x<channelInHandlers.size();x++){
                if(clientChannel.isOpen()){
                    try {
                        if(exception==null){
                            ChannelInHandler<?, ?> channelInHandler = channelInHandlers.get(x);
                            Class<? extends ChannelInHandler> channelInHandlerClass = channelInHandler.getClass();
                            Method before = channelInHandlerClass.getMethod("before", SocketChannel.class, channelInHandler.getInClass());
                            before.invoke(channelInHandler,clientChannel,linkIn);
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class, channelInHandler.getInClass());
                            Object invoke = handler.invoke(channelInHandler, clientChannel, linkIn);
                            Method after = channelInHandlerClass.getMethod("after", SocketChannel.class, channelInHandler.getInClass());
                            after.invoke(channelInHandler,clientChannel,linkIn);
                            linkIn = invoke;
                        }else{
                            ChannelInHandler<?, ?> channelInHandler = channelInHandlers.get(x);
                            channelInHandler.exception(exception,clientChannel,linkIn);
                            Class<? extends ChannelInHandler> channelInHandlerClass = channelInHandler.getClass();
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class, channelInHandler.getInClass());
                            linkIn = handler.invoke(channelInHandler,clientChannel,linkIn);
                            exception = null;
                        }
                    }catch(Exception e){
                        exception = e;
                        e.printStackTrace();
                    }
                }else{
                    System.out.println("客户端close");
                }
            }
            return linkIn;
        }

    }

}
