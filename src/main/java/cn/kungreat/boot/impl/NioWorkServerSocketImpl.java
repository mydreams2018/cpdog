package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.ChannelOutHandler;
import cn.kungreat.boot.ChannelProtocolHandler;
import cn.kungreat.boot.NioWorkServerSocket;
import cn.kungreat.boot.em.ProtocolState;

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
    private static final List<ChannelOutHandler<?,?>> channelOutHandlers = new ArrayList<>();
    private static ChannelProtocolHandler channelProtocolHandler ;
    private final TreeMap<Integer,ByteBuffer> treeMap = new TreeMap<>();
    private final TreeMap<Integer,ByteBuffer> convertTreeMap = new TreeMap<>();
    private final TreeMap<Integer,ProtocolState> protocolStateMap = new TreeMap<>();
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

    public static void addChannelOutHandlers(ChannelOutHandler<?,?> channelOutHandler){
        channelOutHandlers.add(channelOutHandler);
    }

    public static void addChannelProtocolHandler(ChannelProtocolHandler protocolHandler) {
        NioWorkServerSocketImpl.channelProtocolHandler = protocolHandler;
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
        this.bufferSize = Math.max(bufferSize,2048);
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
                    int channelHash = clientChannel.hashCode();
                    ByteBuffer byteBuffer = treeMap.get(channelHash);
                    if(byteBuffer == null){
                        byteBuffer = ByteBuffer.allocate(NioWorkServerSocketImpl.this.bufferSize);
                        treeMap.put(channelHash,byteBuffer);
                        convertTreeMap.put(channelHash,ByteBuffer.allocate(NioWorkServerSocketImpl.this.bufferSize));
                    }
                    int read = clientChannel.read(byteBuffer);
                    while(read > 0){
                        read = clientChannel.read(byteBuffer);
                    }
                    if(protocolStateMap.get(channelHash) == null
                            || protocolStateMap.get(channelHash) != ProtocolState.FINISH){
                        //协议处理
                        if(channelProtocolHandler.handlers(clientChannel, byteBuffer)){
                            protocolStateMap.put(channelHash,ProtocolState.FINISH);
                        }
                    }else{
                        ByteBuffer tarBuffer = convertTreeMap.get(channelHash);
                        Object inEnd = runInHandlers(clientChannel, byteBuffer,tarBuffer);
                        runOutHandlers(clientChannel,inEnd);
                    }
                    if(!clientChannel.isOpen()){
                        treeMap.remove(channelHash);
                        protocolStateMap.remove(channelHash);
                        convertTreeMap.remove(channelHash);
                    }
                    if(read == -1){
                        System.out.println(clientChannel.getRemoteAddress()+"自动关闭了");
                        clientChannel.close();
                        treeMap.remove(channelHash);
                        protocolStateMap.remove(channelHash);
                        convertTreeMap.remove(channelHash);
                    }
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
                treeMap.remove(clientChannel.hashCode());
                protocolStateMap.remove(clientChannel.hashCode());
                convertTreeMap.remove(clientChannel.hashCode());
                e.printStackTrace();
            }
        }

        private Object runInHandlers(final SocketChannel clientChannel,final ByteBuffer byteBuffer,final ByteBuffer tarBuffer) {
            Object linkIn = tarBuffer;
            Exception exception = null;
            for(int x=0;x<channelInHandlers.size();x++){
                if(clientChannel.isOpen()){
                    try {
                        if(exception==null){
                            ChannelInHandler<?, ?> channelInHandler = channelInHandlers.get(x);
                            Class<? extends ChannelInHandler> channelInHandlerClass = channelInHandler.getClass();
                            Method before = channelInHandlerClass.getMethod("before", SocketChannel.class,ByteBuffer.class, channelInHandler.getInClass());
                            before.invoke(channelInHandler,clientChannel,byteBuffer,linkIn);
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class,ByteBuffer.class, channelInHandler.getInClass());
                            Object invoke = handler.invoke(channelInHandler, clientChannel,byteBuffer, linkIn);
                            Method after = channelInHandlerClass.getMethod("after", SocketChannel.class,ByteBuffer.class, channelInHandler.getInClass());
                            after.invoke(channelInHandler,clientChannel,byteBuffer,linkIn);
                            linkIn = invoke;
                        }else{
                            ChannelInHandler<?, ?> channelInHandler = channelInHandlers.get(x);
                            channelInHandler.exception(exception,clientChannel,byteBuffer,linkIn);
                            Class<? extends ChannelInHandler> channelInHandlerClass = channelInHandler.getClass();
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class,ByteBuffer.class, channelInHandler.getInClass());
                            linkIn = handler.invoke(channelInHandler,clientChannel,byteBuffer,linkIn);
                            exception = null;
                        }
                    }catch(Exception e){
                        exception = e;
                        e.printStackTrace();
                    }
                }else{
                    System.out.println("客户端close");
                    break;
                }
            }
            return linkIn;
        }

        private Object runOutHandlers(final SocketChannel clientChannel,final Object in) {
            ByteBuffer outBuf = getOutBuf();
            outBuf.clear();
            Object linkIn = in;
            Exception exception = null;
            for(int x=0;x<channelOutHandlers.size();x++){
                if(clientChannel.isOpen()){
                    try {
                        if(exception==null){
                            ChannelOutHandler<?, ?> channelOutHandler = channelOutHandlers.get(x);
                            Class<? extends ChannelOutHandler> channelOutHandlerClass = channelOutHandler.getClass();
                            Method before = channelOutHandlerClass.getMethod("before", SocketChannel.class, channelOutHandler.getInClass());
                            before.invoke(channelOutHandler,clientChannel,linkIn);
                            Method handler = channelOutHandlerClass.getMethod("handler",ByteBuffer.class, SocketChannel.class, channelOutHandler.getInClass());
                            Object invoke = handler.invoke(channelOutHandler, outBuf,clientChannel, linkIn);
                            Method after = channelOutHandlerClass.getMethod("after", SocketChannel.class, channelOutHandler.getInClass());
                            after.invoke(channelOutHandler,clientChannel,linkIn);
                            linkIn = invoke;
                        }else{
                            ChannelOutHandler<?, ?> channelOutHandler = channelOutHandlers.get(x);
                            channelOutHandler.exception(exception,clientChannel,linkIn);
                            Class<? extends ChannelOutHandler> channelOutHandlerClass = channelOutHandler.getClass();
                            Method handler = channelOutHandlerClass.getMethod("handler", ByteBuffer.class, SocketChannel.class, channelOutHandler.getInClass());
                            linkIn = handler.invoke(channelOutHandler, outBuf,clientChannel,linkIn);
                            exception = null;
                        }
                    }catch(Exception e){
                        exception = e;
                        e.printStackTrace();
                    }
                }else{
                    System.out.println("客户端close");
                    break;
                }
            }
            return linkIn;
        }
    }

}
