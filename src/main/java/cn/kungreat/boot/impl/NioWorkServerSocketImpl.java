package cn.kungreat.boot.impl;

import cn.kungreat.boot.*;
import cn.kungreat.boot.em.ProtocolState;
import cn.kungreat.boot.handler.WebSocketConvertData;
import cn.kungreat.boot.handler.WebSocketConvertDataOut;
import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.tls.InitLinkedList;
import cn.kungreat.boot.tls.TLSSocketLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NioWorkServerSocketImpl implements NioWorkServerSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(NioWorkServerSocketImpl.class);

    private NioWorkServerSocketImpl() {
    }

    private final static ThreadGroup WORK_THREAD_GROUP = new WorkThreadGroup("workServer");
    private final static AtomicInteger ATOMIC_INTEGER = new AtomicInteger(0);
    private static ChannelProtocolHandler channelProtocolHandler;
    /*
       可能和TLS握手的线程池并发
       TLS握手完后有多的数据,多的数据可能就是一条完整的信息了,
       如果没有下次信息的触发,会倒置此次信息不会触发,所以这里有数据时会在流程中自动触发一次
    */
    public final LinkedList<SelectionKey> tlsInitKey = new InitLinkedList();
    /*
     * 出站的源始数据
     * */
    private final ByteBuffer outBuf = ByteBuffer.allocate(8192);
    private final HashMap<SocketOption<?>, Object> optionMap = new HashMap<>();
    //websocket入站解决方案
    private final WebSocketConvertData webSocketConvertData = new WebSocketConvertData();
    //websocket出站解决方案
    private final static WebSocketConvertDataOut WEB_SOCKET_CONVERT_DATA_OUT = new WebSocketConvertDataOut();
    //websocket过滤器链路
    private final static List<FilterInHandler> FILTER_IN_CHAINS = new ArrayList<>();
    private Thread workThreads;
    private Selector selector;
    //清理缓冲区 不存在的channel对象 172800000  48小时清理一次
    private int clearCount = 1;
    private final long curTimes = System.currentTimeMillis();

    public static NioWorkServerSocket create() {
        return new NioWorkServerSocketImpl();
    }

    private static String getThreadName() {
        int i = ATOMIC_INTEGER.addAndGet(1);
        return "work-thread-" + i;
    }

    public static void addFilterChain(FilterInHandler filter) {
        FILTER_IN_CHAINS.add(filter);
    }

    public static void addChannelProtocolHandler(ChannelProtocolHandler protocolHandler) {
        NioWorkServerSocketImpl.channelProtocolHandler = protocolHandler;
    }

    @Override
    public LinkedList<SelectionKey> getTlsInitKey() {
        return this.tlsInitKey;
    }

    @Override
    public <T> NioWorkServerSocket setOption(SocketOption<T> name, T value) {
        optionMap.put(name, value);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setOption(SocketChannel channel) throws IOException {
        Set<SocketOption<?>> supportedOptions = channel.supportedOptions();
        Set<Map.Entry<SocketOption<?>, Object>> entries = optionMap.entrySet();
        for (Map.Entry<SocketOption<?>, Object> entry : entries) {
            SocketOption<?> name = entry.getKey();
            if (supportedOptions.contains(name)) {
                if (name.type() == Integer.class) {
                    SocketOption<Integer> socketKey = (SocketOption<Integer>) name;
                    Integer socketValue = (Integer) entry.getValue();
                    channel.setOption(socketKey, socketValue);
                } else if (name.type() == Boolean.class) {
                    SocketOption<Boolean> socketKey = (SocketOption<Boolean>) name;
                    Boolean socketValue = (Boolean) entry.getValue();
                    channel.setOption(socketKey, socketValue);
                } else if (name.type() == NetworkInterface.class) {
                    SocketOption<NetworkInterface> socketKey = (SocketOption<NetworkInterface>) name;
                    NetworkInterface socketValue = (NetworkInterface) entry.getValue();
                    channel.setOption(socketKey, socketValue);
                } else {
                    LOGGER.error("没有配置的SocketOption类型{}", name);
                }
            } else {
                LOGGER.error("不支持的SocketOption类型{}", name);
            }
        }
    }

    @Override
    public NioWorkServerSocket buildThread() {
        this.workThreads = new Thread(NioWorkServerSocketImpl.WORK_THREAD_GROUP, new WorkRunnable(), getThreadName());
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

    private final class WorkRunnable implements Runnable {

        @Override
        public void run() {
            if (CpdogMain.THREAD_LOCAL.get() == null) {
                CpdogMain.THREAD_LOCAL.set(ByteBuffer.allocate(32768));
            }
            try {
                while (NioWorkServerSocketImpl.this.selector.select() >= 0) {
                    runTlsInit();
                    clearBuffer();
                    Set<SelectionKey> selectionKeys = NioWorkServerSocketImpl.this.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey next = iterator.next();
                        iterator.remove();
                        handler(next);
                    }
                }
            } catch (Exception e) {
                NioWorkServerSocketImpl.LOGGER.error(e.getMessage());
            }
        }

        /* TLS握手完后 可能有读取多的没有用完的数据、 需要在此触发一次*/
        private void runTlsInit() {
            SelectionKey peekFirst = tlsInitKey.peekFirst();
            while (peekFirst != null) {
                tlsInitKey.removeFirst();
                if (peekFirst.isValid()) {
                    InitHandler(peekFirst);
                }
                peekFirst = tlsInitKey.peekFirst();
            }
        }

        public void InitHandler(SelectionKey next) {
            SocketChannel clientChannel = null;
            try {
                clientChannel = (SocketChannel) next.channel();
                baseHandler(next, clientChannel);
            } catch (Exception e) {
                if (clientChannel != null) {
                    try {
                        clientChannel.close();
                        baseClear(clientChannel);
                    } catch (IOException ioException) {
                        NioWorkServerSocketImpl.LOGGER.error(ioException.getMessage());
                    }
                }
                next.cancel();
                NioWorkServerSocketImpl.LOGGER.error(e.getMessage());
            }
        }

        private void baseHandler(SelectionKey next, SocketChannel clientChannel) throws Exception {
            TLSSocketLink attachment = (TLSSocketLink) next.attachment();
            ByteBuffer inSrc = attachment.getInSrc();
            int read = clientChannel.read(inSrc);
            inSrc.flip();
            CpDogSSLContext.inDecode(attachment, 6);
            attachment.getInSrc().compact();
            ByteBuffer inSrcDecode = attachment.getInSrcDecode();
            if (channelProtocolHandler != null && ProtocolState.FINISH != attachment.getProtocolState()) {
                CpdogMain.THREAD_LOCAL.get().clear();
                //websocket协议处理
                if (channelProtocolHandler.handlers(clientChannel, inSrcDecode)) {
                    attachment.setProtocolState(ProtocolState.FINISH);
                }
            } else {
                List<WebSocketConvertData.WebSocketData> socketDataList = websocketConvert(clientChannel, inSrcDecode);
                if (socketDataList != null) {
                    Iterator<WebSocketConvertData.WebSocketData> webSocketDataIterator = socketDataList.iterator();
                    while (webSocketDataIterator.hasNext()) {
                        WebSocketConvertData.WebSocketData webSocketDataNext = webSocketDataIterator.next();
                        if ((webSocketDataNext.getType() == 1 && webSocketDataNext.isDone())
                                || webSocketDataNext.getType() == 8) {
                            runFilterChain(webSocketDataNext);
                            runConvertDataOut(webSocketDataNext, clientChannel);
                            webSocketDataIterator.remove();
                        } else if (webSocketDataNext.getType() == 2 && webSocketDataNext.isConvert()) {
                            runFilterChain(webSocketDataNext);
                            runConvertDataOut(webSocketDataNext, clientChannel);
                            if (webSocketDataNext.isDone()) {
                                webSocketDataIterator.remove();
                            }
                        }
                    }
                }
            }
            if (!clientChannel.isOpen()) {
                baseClear(clientChannel);
                next.cancel();
            } else if (read == -1) {
                baseClear(clientChannel);
                clientChannel.close();
                next.cancel();
            }
        }

        private void baseClear(final SocketChannel clientChannel) {
            int channelHash = clientChannel.hashCode();
            CpDogSSLContext.reuseTLSSocketLink(channelHash);
        }

        public void handler(SelectionKey next) {
            if (next.isValid() && next.isReadable()) {
                InitHandler(next);
            } else {
                next.cancel();
                LOGGER.error("客户端监听类型异常:");
            }
        }

        private List<WebSocketConvertData.WebSocketData> websocketConvert(final SocketChannel socketChannel, final ByteBuffer byteBuffer) {
            try {
                NioWorkServerSocketImpl.this.webSocketConvertData.before(socketChannel, byteBuffer);
                List<WebSocketConvertData.WebSocketData> handler = NioWorkServerSocketImpl.this.webSocketConvertData.handler(socketChannel);
                NioWorkServerSocketImpl.this.webSocketConvertData.after(socketChannel, byteBuffer);
                return handler;
            } catch (Exception e) {
                NioWorkServerSocketImpl.this.webSocketConvertData.exception(e, socketChannel);
            }
            return null;
        }

        private void runFilterChain(WebSocketConvertData.WebSocketData webSocketData) {
            for (FilterInHandler filterInChain : FILTER_IN_CHAINS) {
                try {
                    filterInChain.init(webSocketData);
                    boolean filter = filterInChain.filter(webSocketData);
                    if (!filter) {
                        break;
                    }
                } catch (Exception e) {
                    filterInChain.exception(e, webSocketData);
                    break;
                }
            }
        }

        private void runConvertDataOut(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) {
            ByteBuffer outBuf = getOutBuf();
            outBuf.clear();
            CpdogMain.THREAD_LOCAL.get().clear();
            try {
                WEB_SOCKET_CONVERT_DATA_OUT.before(webSocketData, socketChannel);
                WEB_SOCKET_CONVERT_DATA_OUT.handler(webSocketData, outBuf, socketChannel);
                WEB_SOCKET_CONVERT_DATA_OUT.after(webSocketData, socketChannel, outBuf);
            } catch (Exception e) {
                WEB_SOCKET_CONVERT_DATA_OUT.exception(e, socketChannel);
            }
        }

        //清理特殊情况下 断开的channel USER_UUIDS-clear  todo
        private void clearBuffer() {
            if (System.currentTimeMillis() > curTimes + (clearCount * 172800000L)) {
                clearCount++;

            }
        }
    }

}