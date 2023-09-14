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
    public void before(WebSocketConvertData.WebSocketData webSocketData, ByteBuffer byteBuffer) throws Exception {

    }

    @Override
    public void handler(WebSocketConvertData.WebSocketData webSocketData, ByteBuffer byteBuffer, SocketChannel socketChannel) throws Exception {
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
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception {

    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel) {

    }
}
