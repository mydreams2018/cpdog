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
import java.nio.channels.SelectableChannel;
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
    private final TreeMap<Integer,ProtocolState> protocolStateMap = new TreeMap<>();
    private final ByteBuffer outBuf = ByteBuffer.allocate(8192);
    private final HashMap<SocketOption<?>,Object> optionMap = new HashMap<>();
    private Thread workThreads;
    private Selector selector;
    //清理缓冲区 不存在的channel对象 172800000  48小时清理一次
    private int clearCount = 1;
    private long curTimes = System.currentTimeMillis();

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

        private Exception exception = null;

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
                    clearBuffer();
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
                        Object inEnd = runInHandlers(clientChannel, byteBuffer);
                        runOutHandlers(clientChannel,inEnd);
                    }
                    if(!clientChannel.isOpen()){
                        treeMap.remove(channelHash);
                        protocolStateMap.remove(channelHash);
                    }
                    if(read == -1){
                        System.out.println(clientChannel.getRemoteAddress()+"自动关闭了");
                        clientChannel.close();
                        treeMap.remove(channelHash);
                        protocolStateMap.remove(channelHash);
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
                }else{
                    next.cancel();
                }
                treeMap.remove(clientChannel.hashCode());
                protocolStateMap.remove(clientChannel.hashCode());
                e.printStackTrace();
            }
        }

        private Object runInHandlers(final SocketChannel clientChannel,final ByteBuffer byteBuffer) {
            Object linkIn = byteBuffer;
            for(int x=0;x<channelInHandlers.size();x++){
                if(clientChannel.isOpen()){
                    try {
                        if(this.exception==null){
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
                            Object handlerIn = channelInHandler.exception(this.exception, clientChannel, linkIn);
                            Class<? extends ChannelInHandler> channelInHandlerClass = channelInHandler.getClass();
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class, channelInHandler.getInClass());
                            linkIn = handler.invoke(channelInHandler,clientChannel,handlerIn);
                            this.exception = null;
                        }
                    }catch(Exception e){
                        this.exception = e;
                        e.printStackTrace();
                    }
                }else{
                    System.out.println("channel-close");
                    break;
                }
            }
            return linkIn;
        }

        private Object runOutHandlers(final SocketChannel clientChannel,final Object in) throws IOException {
            ByteBuffer outBuf = getOutBuf();
            outBuf.clear();
            Object linkIn = in;
            for(int x=0;x<channelOutHandlers.size();x++){
                if(clientChannel.isOpen()){
                    try {
                        if(this.exception==null){
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
                            Object handlerIn = channelOutHandler.exception(this.exception, clientChannel, linkIn);
                            Class<? extends ChannelOutHandler> channelOutHandlerClass = channelOutHandler.getClass();
                            Method handler = channelOutHandlerClass.getMethod("handler", ByteBuffer.class, SocketChannel.class, channelOutHandler.getInClass());
                            linkIn = handler.invoke(channelOutHandler, outBuf,clientChannel,handlerIn);
                            this.exception = null;
                        }
                    }catch(Exception e){
                        this.exception = e;
                        e.printStackTrace();
                    }
                }else{
                    System.out.println("channel-close");
                    break;
                }
            }
            this.exception=null;
            return linkIn;
        }
//清理特殊情况关闭的channel
        private void clearBuffer(){
            if(System.currentTimeMillis() > curTimes + (clearCount * 172800000)){
                clearCount++;
                try {
                    Set<SelectionKey> keys = selector.keys();
                    for (SelectionKey ky: keys) {
                        SocketChannel channel = (SocketChannel) ky.channel();

                    }
                }catch (Exception e){
                    e.printStackTrace();
                    System.out.println("clear-channel:error");
                }
            }
        }
    }

}
