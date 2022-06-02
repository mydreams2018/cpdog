package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelOutHandler;
import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.GlobalEventListener;
import cn.kungreat.boot.tls.CpDogSSLContext;
import cn.kungreat.boot.utils.WebSocketResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketChannelOutHandler implements ChannelOutHandler<LinkedList<WebSocketChannelInHandler.WebSocketState>,String> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketChannelOutHandler.class);
    /*
    存放用户登录随机生成的UUID 关联信息.UUID在前端是session级别的. [k=uuid v=userid]
    */
    public static final Map<String,String> USER_UUIDS = new ConcurrentHashMap<>(1024);


    @Override
    public void before(SocketChannel socketChannel,LinkedList<WebSocketChannelInHandler.WebSocketState> in) {
        if(in != null){
            WebSocketChannelInHandler.WebSocketState first = in.peekFirst();
            //初始化页面完成记录用户的channel
            if(first.getType() == 1 && first.isDone() && first.getUrl().equals("initSaveConnection")){
                String nikeName = USER_UUIDS.get(first.getCharts().getTokenSession());
                if(nikeName != null){
                    GlobalEventListener.CONCURRENT_EVENT_MAP.put(nikeName,socketChannel);
                }
                reuserAddWebSocketState(in.removeFirst());
            }
        }
    }

    private void reuserAddWebSocketState(WebSocketChannelInHandler.WebSocketState webSocketState){
        if(webSocketState != null){
            webSocketState.clear();
            WebSocketChannelInHandler.REUSER_WEBSTATE.offer(webSocketState);
        }
    }

    @Override
    public String handler(ByteBuffer byteBuffer, SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) throws Exception {
        if(in != null){
            WebSocketChannelInHandler.WebSocketState first = in.peekFirst();
            while (first != null) {
                if(first.getType() == 1 && first.isDone()){
                    answer(first,byteBuffer,socketChannel);
                    reuserAddWebSocketState(in.removeFirst());
                    first = in.peekFirst();
                }else if(first.getType() == 2 && first.isDone()){
                    logger.info("文件写出完毕:{}",first.getFileName());
                    writeFiles(first);
                    answer(first,byteBuffer,socketChannel);
                    reuserAddWebSocketState(in.removeFirst());
                    first = in.peekFirst();
                }else if(first.getType() == 2 && first.isConvert()){
                    writeFiles(first);
                    break;
                }else if(first.getType() == 8){
                    //关闭信息状态标识
                    socketChannel.close();
                    break;
                }else if(first.getType() == 999){
                    //初始化的一个空对象标识
                    break;
                }else if(first.getType() == 2){
                    //第一次产生的头信息的二进制数据
                    break;
                }else if(first.getType() == 1){
                    //文本数据过大时没有读取完成不做操作
                    break;
                }else{
                    logger.error("out type 未知:"+first.getType());
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) {

    }
    //写出文件
    public void writeFiles(WebSocketChannelInHandler.WebSocketState first) throws IOException {
        ByteBuffer fileBuffer = first.getByteBuffer();
        fileBuffer.flip();
        byte[] bts = new byte[fileBuffer.remaining()];
        System.arraycopy(fileBuffer.array(),0,bts,0,fileBuffer.remaining());
        Files.write(first.getFilePath(),bts, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        fileBuffer.clear();
    }

    @Override
    public LinkedList exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        logger.error(e.getLocalizedMessage());
        socketChannel.close();
        return null;
    }

    @Override
    public Class getInClass() {
        return LinkedList.class;
    }

    @Override
    public void clearBuffers(SocketChannel socketChannel) {

    }
    //通过反射去调用方法根据 类名上有CpdogController 注解按index 优先级调用 只匹配一次
    public void answer(WebSocketChannelInHandler.WebSocketState first,ByteBuffer byteBuffer, SocketChannel socketChannel) throws Exception {
        String baseUrl = first.getUrl();
        for (int i=0; i < CpdogMain.CONTROLLERS.size(); i++){
            Class<?> aClass = CpdogMain.CONTROLLERS.get(i);
            Method[] declaredMethods = aClass.getMethods();
            if(declaredMethods != null && declaredMethods.length>0){
                for(int x=0;x<declaredMethods.length;x++){
                    Method methods = declaredMethods[x];
                    String name = methods.getName();
                    if(name.equals(baseUrl)){
                        if(Modifier.isStatic(methods.getModifiers())){
                            String rts =(String) methods.invoke(null,first);
                            if(rts.length()>0){
                                byte[] bytes = rts.getBytes(Charset.forName("UTF-8"));
                                int readLength = 0;
                                byteBuffer.put(WebSocketResponse.getBytes(bytes));
                                //下边是一次完整的数据写出.可能EVENT事什通知造成并发.所以加锁
                                synchronized (socketChannel){
                                    do{
                                        int min = Math.min(bytes.length - readLength, byteBuffer.remaining());
                                        byteBuffer.put(bytes,readLength,min);
                                        readLength = readLength + min;
                                        byteBuffer.flip();
//                                        socketChannel.write(byteBuffer);
                                        CpDogSSLContext.outEncode(socketChannel,byteBuffer);
                                        byteBuffer.clear();
                                    }while (readLength<bytes.length);
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
