package cn.kungreat.boot.handler;

import cn.kungreat.boot.ConvertDataOutHandler;
import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.utils.WebSocketResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class WebSocketConvertDataOut implements ConvertDataOutHandler<WebSocketConvertData.WebSocketData> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketConvertDataOut.class);

    @Override
    public void before(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) throws Exception {

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
    public void after(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception {
        if (webSocketData.getType() == 8) {
            socketChannel.close();
        }
    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel) {

    }
}
