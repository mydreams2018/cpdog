package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelOutHandler;
import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.utils.WebSocketResponse;

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

    /*
    存放用户登录随机生成的UUID 关联信息.UUID在前端是session级别的. [k=uuid v=userid]
    */
    public static final Map<String,String> USER_UUIDS = new ConcurrentHashMap<>(1024);


    @Override
    public void before(SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) {

    }

    @Override
    public String handler(ByteBuffer byteBuffer, SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) throws Exception {
        if(in != null){
            WebSocketChannelInHandler.WebSocketState first = in.peekFirst();
            while (first != null) {
                if(first.getType() == 1 && first.isDone()){
                    answer(first,byteBuffer,socketChannel);
                    in.removeFirst();
                    first = in.peekFirst();
                }else if(first.getType() == 2 && first.isDone()){
                    System.out.println("文件写出完毕:"+first.getFileName());
                    ByteBuffer fileBuffer = first.getByteBuffer();
                    fileBuffer.flip();
                    byte[] bts = new byte[fileBuffer.remaining()];
                    System.arraycopy(fileBuffer.array(),0,bts,0,fileBuffer.remaining());
                    Files.write(first.getFilePath(),bts, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    fileBuffer.clear();
                    answer(first,byteBuffer,socketChannel);
                    in.removeFirst();
                    first = in.peekFirst();
                }else if(first.getType() == 2 && first.isConvert()){
                    //写出文件
                    ByteBuffer fileBuffer = first.getByteBuffer();
                    fileBuffer.flip();
                    byte[] bts = new byte[fileBuffer.remaining()];
                    System.arraycopy(fileBuffer.array(),0,bts,0,fileBuffer.remaining());
                    Files.write(first.getFilePath(),bts, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    fileBuffer.clear();
                    break;
                }else if(first.getType() == 8){
                    //关闭信息状态标识
                    System.out.println("out type ==8 ");
                    break;
                }else if(first.getType() == 999){
                    //初始化的一个空对象标识
                    System.out.println("out type 999");
                    break;
                }else if(first.getType() == 2){
                    //第一次产生的头信息的二进制数据
                    break;
                }else if(first.getType() == 1){
                    //文本数据过大时没有读取完成不做操作
                    break;
                }else{
                    System.out.println("out type 未知");
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) {

    }

    @Override
    public LinkedList exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        e.printStackTrace();
        socketChannel.close();
        WebSocketChannelInHandler.WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
        WebSocketChannelInHandler.WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
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
                            byte[] bytes = rts.getBytes(Charset.forName("UTF-8"));
                            int readLength = 0;
                            byteBuffer.put(WebSocketResponse.getBytes(bytes));
                            do{
                                int min = Math.min(bytes.length - readLength, byteBuffer.remaining());
                                byteBuffer.put(bytes,readLength,min);
                                readLength = readLength + min;
                                byteBuffer.flip();
                                socketChannel.write(byteBuffer);
                                byteBuffer.clear();
                            }while (readLength<bytes.length);
                            return;
                        }
                    }
                }
            }
        }
    }
}
