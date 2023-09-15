package cn.kungreat.boot.handler;

import cn.kungreat.boot.ConvertDataOutHandler;
import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.GlobalEventListener;
import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.utils.WebSocketResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketConvertDataOut implements ConvertDataOutHandler<WebSocketConvertData.WebSocketData> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketConvertDataOut.class);

    /*
     *   存放用户登录随机生成的UUID 关联信息.UUID在前端是session级别的. [k=uuid v=userid]
     */
    public static final Map<String, String> USER_UUIDS = new ConcurrentHashMap<>(1024);

    @Override
    public void before(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) throws Exception {
        //页面刷新就会重建连接,用此来记录用户的channel信息,需要前端配合处理,在登录完成时返回token让前端保存
        if (webSocketData.getReceiveObj().getUrl().equals("initSaveConnection")) {
            String nikeName = USER_UUIDS.get(webSocketData.getReceiveObj().getCharts().getTokenSession());
            if (nikeName != null) {
                GlobalEventListener.CONCURRENT_EVENT_MAP.put(nikeName, socketChannel);
            }
        }
    }

    @Override
    public void handler(WebSocketConvertData.WebSocketData webSocketData, ByteBuffer byteBuffer, SocketChannel socketChannel) throws Exception {
        if (webSocketData.getSendBackMessage() != null) {
            String valueAsString = WebSocketConvertData.MAP_JSON.writeValueAsString(webSocketData.getSendBackMessage());
            writeByteBuffer(valueAsString, byteBuffer, socketChannel);
            LOGGER.error("输出的非正常流程数据{}", valueAsString);
            return;
        }
        String baseUrl = webSocketData.getReceiveObj().getUrl();
        for (int i = 0; i < CpdogMain.CONTROLLERS.size(); i++) {
            Class<?> aClass = CpdogMain.CONTROLLERS.get(i);
            Method[] declaredMethods = aClass.getMethods();
            for (Method methods : declaredMethods) {
                String name = methods.getName();
                if (name.equals(baseUrl)) {
                    if (Modifier.isStatic(methods.getModifiers())) {
                        String rts = (String) methods.invoke(null, webSocketData.getReceiveObj());
                        if (!rts.isEmpty()) {
                            writeByteBuffer(rts, byteBuffer, socketChannel);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void writeByteBuffer(String rts, ByteBuffer byteBuffer, SocketChannel socketChannel) throws Exception {
        byte[] bytes = rts.getBytes(StandardCharsets.UTF_8);
        int readLength = 0;
        byteBuffer.put(WebSocketResponse.getBytes(bytes));
        //下边是一次完整的数据写出.可能EVENT事什通知造成并发.所以加锁
        synchronized (socketChannel) {
            do {
                int min = Math.min(bytes.length - readLength, byteBuffer.remaining());
                byteBuffer.put(bytes, readLength, min);
                readLength = readLength + min;
                byteBuffer.flip();
                CpDogSSLContext.outEncode(socketChannel, byteBuffer);
                byteBuffer.clear();
            } while (readLength < bytes.length);
        }
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception {

    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel) {

    }
}
