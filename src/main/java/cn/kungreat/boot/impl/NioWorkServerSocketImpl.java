package cn.kungreat.boot.impl;

import cn.kungreat.boot.*;
import cn.kungreat.boot.em.ProtocolState;
import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.tls.InitLinkedList;
import cn.kungreat.boot.tls.TLSSocketLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(NioWorkServerSocketImpl.class);
    private NioWorkServerSocketImpl(){}
    private final static ThreadGroup threadGroup = new WorkThreadGroup("workServer");
    private final static AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final List<ChannelInHandler<?,?>> channelInHandlers = new ArrayList<>();
    private static final List<ChannelOutHandler<?,?>> channelOutHandlers = new ArrayList<>();
    private static ChannelProtocolHandler channelProtocolHandler ;
    public final LinkedList<SelectionKey> tlsInitKey = new InitLinkedList();
    private final ByteBuffer outBuf = ByteBuffer.allocate(8192);
    private final HashMap<SocketOption<?>,Object> optionMap = new HashMap<>();
    private Thread workThreads;
    private Selector selector;
    //清理缓冲区 不存在的channel对象 172800000  48小时清理一次
    private int clearCount = 1;
    private final long curTimes = System.currentTimeMillis();

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
    public LinkedList<SelectionKey> getTlsInitKey(){
        return this.tlsInitKey;
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

    public ByteBuffer getOutBuf() {
        return outBuf;
    }

    private final class WorkRunable implements Runnable{

        private Exception exception = null;

        @Override
        public void run() {
            if(CpdogMain.THREAD_LOCAL.get() == null){
                CpdogMain.THREAD_LOCAL.set(ByteBuffer.allocate(32768));
            }
            try{
                while(NioWorkServerSocketImpl.this.selector.select()>=0){
                    Set<SelectionKey> selectionKeys = NioWorkServerSocketImpl.this.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey next = iterator.next();
                        iterator.remove();
                        handler(next);
                    }
                    runTlsInit();
                    clearBuffer();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            run();
        }
        /* TLS握手完后 可能有读取多的没有用完的数据、 需要在此触发一次*/
        private void runTlsInit(){
            SelectionKey peekFirst = tlsInitKey.peekFirst();
            while(peekFirst != null){
                tlsInitKey.removeFirst();
                if(peekFirst.isValid()){
                    InitHandler(peekFirst);
                }
                peekFirst = tlsInitKey.peekFirst();
            }
        }
        public void InitHandler(SelectionKey next){
            SocketChannel clientChannel = null;
            CpdogMain.THREAD_LOCAL.get().clear();
            try{
                clientChannel = (SocketChannel) next.channel();
                baseHandler(next, clientChannel);
            }catch (Exception e){
                if(clientChannel!=null){
                    try {
                        clientChannel.close();
                    } catch(IOException ioException) {
                        ioException.printStackTrace();
                    }
                    baseClear(clientChannel);
                }else{
                    next.cancel();
                }
                e.printStackTrace();
            }
        }

        private void baseHandler(SelectionKey next, SocketChannel clientChannel) throws Exception {
            TLSSocketLink attachment = (TLSSocketLink)next.attachment();
            ByteBuffer byteBuffer = attachment.getInSrcDecode();
            ByteBuffer inSrc = attachment.getInSrc();
            int read = clientChannel.read(inSrc);
            while(read > 0){
                read = clientChannel.read(inSrc);
            }
            inSrc.flip();
            ByteBuffer inDecode = CpDogSSLContext.inDecode(attachment, byteBuffer,6);
            attachment.getInSrc().compact();
            if(byteBuffer != inDecode){
                //说明扩容了 byteBuffer
                byteBuffer = inDecode;
                attachment.setInSrcDecode(byteBuffer);
            }
            if(channelProtocolHandler != null && attachment.getProtocolState() == null
                    || attachment.getProtocolState() != ProtocolState.FINISH){
                //协议处理
                if(channelProtocolHandler.handlers(clientChannel, byteBuffer)){
                    attachment.setProtocolState(ProtocolState.FINISH);
                }
            }else{
                Object inEnd = runInHandlers(clientChannel, byteBuffer);
                runOutHandlers(clientChannel,inEnd);
            }
            if(!clientChannel.isOpen()){
                baseClear(clientChannel);
            } else if(read == -1){
                clientChannel.close();
                baseClear(clientChannel);
            }
        }

        private void baseClear(final SocketChannel clientChannel){
            int channelHash = clientChannel.hashCode();
            CpDogSSLContext.reuserTLSSocketLink(channelHash);
            clearHandlerLink(clientChannel);
        }

        public void handler(SelectionKey next){
            if(next.isValid() && next.isReadable()){
                InitHandler(next);
            }else{
                logger.error("客户端监听类型异常:");
            }
        }

        private Object runInHandlers(final SocketChannel clientChannel,final ByteBuffer byteBuffer) {
            Object linkIn = byteBuffer;
            for(ChannelInHandler<?, ?> channelInHandler: channelInHandlers){
                if(clientChannel.isOpen()){
                    try {
                        if(this.exception==null){

                            Class<?> channelInHandlerClass = channelInHandler.getClass();
                            Method before = channelInHandlerClass.getMethod("before", SocketChannel.class, channelInHandler.getInClass());
                            before.invoke(channelInHandler,clientChannel,linkIn);
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class, channelInHandler.getInClass());
                            Object invoke = handler.invoke(channelInHandler, clientChannel, linkIn);
                            Method after = channelInHandlerClass.getMethod("after", SocketChannel.class, channelInHandler.getInClass());
                            after.invoke(channelInHandler,clientChannel,linkIn);
                            linkIn = invoke;
                        }else{

                            Object handlerIn = channelInHandler.exception(this.exception, clientChannel, linkIn);
                            Class<?> channelInHandlerClass = channelInHandler.getClass();
                            Method handler = channelInHandlerClass.getMethod("handler", SocketChannel.class, channelInHandler.getInClass());
                            linkIn = handler.invoke(channelInHandler,clientChannel,handlerIn);
                            this.exception = null;
                        }
                    }catch(Exception e){
                        this.exception = e;
                        e.printStackTrace();
                    }
                }else{
                    logger.error("channel-close");
                    break;
                }
            }
            return linkIn;
        }

        private Object runOutHandlers(final SocketChannel clientChannel,final Object in) throws IOException {
            ByteBuffer outBuf = getOutBuf();
            outBuf.clear();
            Object linkIn = in;
            for (ChannelOutHandler<?, ?> channelOutHandler : channelOutHandlers) {
                if (clientChannel.isOpen()) {
                    try {
                        if (this.exception == null) {
                            Class<?> channelOutHandlerClass = channelOutHandler.getClass();
                            Method before = channelOutHandlerClass.getMethod("before", SocketChannel.class, channelOutHandler.getInClass());
                            before.invoke(channelOutHandler, clientChannel, linkIn);
                            Method handler = channelOutHandlerClass.getMethod("handler", ByteBuffer.class, SocketChannel.class, channelOutHandler.getInClass());
                            Object invoke = handler.invoke(channelOutHandler, outBuf, clientChannel, linkIn);
                            Method after = channelOutHandlerClass.getMethod("after", SocketChannel.class, channelOutHandler.getInClass());
                            after.invoke(channelOutHandler, clientChannel, linkIn);
                            linkIn = invoke;
                        } else {

                            Object handlerIn = channelOutHandler.exception(this.exception, clientChannel, linkIn);
                            Class<?> channelOutHandlerClass = channelOutHandler.getClass();
                            Method handler = channelOutHandlerClass.getMethod("handler", ByteBuffer.class, SocketChannel.class, channelOutHandler.getInClass());
                            linkIn = handler.invoke(channelOutHandler, outBuf, clientChannel, handlerIn);
                            this.exception = null;
                        }
                    } catch (Exception e) {
                        this.exception = e;
                        e.printStackTrace();
                    }
                } else {
                    logger.error("channel-close");
                    break;
                }
            }
//            走完链路还存在异常时 关闭连接
            if(this.exception != null){
                this.exception=null;
                clientChannel.close();
                logger.error("走完链路.还存在异常时 关闭连接");
            }
            return linkIn;
        }
//清理特殊情况下 断开的channel USER_UUIDS-clear  todo
        private void clearBuffer(){
            if(System.currentTimeMillis() > curTimes + (clearCount * 172800000L)){
                clearCount++;

            }
        }
//管道关闭时清理后续 链路. 链路需要自已实现清理方法 注意可能出现的异常情况
        private void clearHandlerLink(final SocketChannel socketChannel){
            try {
                channelInHandlers.forEach(e-> e.clearBuffers(socketChannel));
                channelOutHandlers.forEach(e-> e.clearBuffers(socketChannel));
            }catch (Exception e){
                logger.error("clearHandlerLink-error{}",e.getMessage());
            }
        }
    }

}