package cn.kungreat.boot;

import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.utils.WebSocketResponse;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/*
 * 全局的事件监听、负责事件传递
 */
public class GlobalEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalEventListener.class);
    /*
     * 出站的源始数据
     * */
    private static final ByteBuffer EVENT_BUFFER = ByteBuffer.allocate(8192);
    /*
     *所有的事件集合
     */
    public static final LinkedBlockingQueue<EventBean> EVENT_BLOCKING_QUEUE = new LinkedBlockingQueue<>();
    /*
     * <用户的呢称,对应的管道>
     */
    public static final ConcurrentHashMap<String, SocketChannel> CONCURRENT_EVENT_MAP = new ConcurrentHashMap<>(1024);

    /*
     * 迭代监听 EVENT_BLOCKING_QUEUE 事件 传递给指定的回调方法
     */
    public static void loopEvent() {
        //初始化 存出站的加密数据
        if (CpdogMain.THREAD_LOCAL.get() == null) {
            CpdogMain.THREAD_LOCAL.set(ByteBuffer.allocate(8192));
        }
        EventBean receiveObj;
        String receiveObjUrl;
        SocketChannel socketChannel;
        try {
            while (true) {
                receiveObj = EVENT_BLOCKING_QUEUE.take();
                receiveObjUrl = receiveObj.getUrl();
                socketChannel = CONCURRENT_EVENT_MAP.get(receiveObj.getTar());
                if (socketChannel != null && socketChannel.isOpen()) {
                    outer:
                    for (int i = 0; i < CpdogMain.EVENTS.size(); i++) {
                        Class<?> aClass = CpdogMain.EVENTS.get(i);
                        Method[] declaredMethods = aClass.getMethods();
                        for (Method methods : declaredMethods) {
                            String name = methods.getName();
                            if (name.equals(receiveObjUrl)) {
                                if (Modifier.isStatic(methods.getModifiers())) {
                                    EVENT_BUFFER.clear();
                                    CpdogMain.THREAD_LOCAL.get().clear();
                                    String rts = (String) methods.invoke(null, receiveObj);
                                    if (!rts.isEmpty()) {
                                        byte[] bytes = rts.getBytes(StandardCharsets.UTF_8);
                                        int readLength = 0;
                                        EVENT_BUFFER.put(WebSocketResponse.getBytes(bytes));
                                        synchronized (socketChannel) {
                                            do {
                                                int min = Math.min(bytes.length - readLength, EVENT_BUFFER.remaining());
                                                EVENT_BUFFER.put(bytes, readLength, min);
                                                readLength = readLength + min;
                                                EVENT_BUFFER.flip();
                                                CpDogSSLContext.outEncode(socketChannel, EVENT_BUFFER);
                                                EVENT_BUFFER.clear();
                                            } while (readLength < bytes.length);
                                        }
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                } else if (socketChannel != null && !socketChannel.isOpen()) {
                    CONCURRENT_EVENT_MAP.remove(receiveObj.getTar());
                }
            }
        } catch (Exception e) {
            LOGGER.error("事件监听错误:{}", e.getLocalizedMessage());
        }
    }

    /*
    * 事件的源信息,根据业务设计补充数据
    * */
    @Setter
    @Getter
    public static final class EventBean {
        private String uuid;
        private String src;
        private String tar;
        private String url;
        private Map<String, Object> extraData;
        private String nikeName;
        private String registerTime;
        private String describes;
        private String imgPath;
        private String sortFirst;
        private String id;
        private String srcTarUUID;
        private String type;
    }
}