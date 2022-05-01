package cn.kungreat.boot;

import cn.kungreat.boot.jb.EventBean;
import cn.kungreat.boot.utils.WebSocketResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/*
* 全局的事件监听、负责事件传递
*/
public class GlobalEventListener {

    private static final ByteBuffer EVENT_BUFFER = ByteBuffer.allocateDirect(2048);
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
    public static void loopEvent(){
        EventBean receiveObj = null;
        String receiveObjUrl ;
        SocketChannel socketChannel ;
        try {
            while (true){
                receiveObj = EVENT_BLOCKING_QUEUE.take();
                receiveObjUrl = receiveObj.getUrl();
                socketChannel = CONCURRENT_EVENT_MAP.get(receiveObj.getTar());
                if(socketChannel != null && socketChannel.isOpen()){
                    outer:for(int i=0;i<CpdogMain.EVENTS.size();i++){
                        Class<?> aClass = CpdogMain.EVENTS.get(i);
                        Method[] declaredMethods = aClass.getMethods();
                        if(declaredMethods != null && declaredMethods.length>0){
                            for(int x=0;x<declaredMethods.length;x++){
                                Method methods = declaredMethods[x];
                                String name = methods.getName();
                                if(name.equals(receiveObjUrl)){
                                    if(Modifier.isStatic(methods.getModifiers())){
                                        EVENT_BUFFER.clear();
                                        String rts = (String) methods.invoke(null,receiveObj);
                                        if(rts.length() > 0){
                                            byte[] bytes = rts.getBytes(Charset.forName("UTF-8"));
                                            int readLength = 0;
                                            EVENT_BUFFER.put(WebSocketResponse.getBytes(bytes));
                                            //必须处理并发.数据一次完整的写出.
                                            synchronized (socketChannel){
                                                do{
                                                    int min = Math.min(bytes.length - readLength, EVENT_BUFFER.remaining());
                                                    EVENT_BUFFER.put(bytes,readLength,min);
                                                    readLength = readLength + min;
                                                    EVENT_BUFFER.flip();
                                                    socketChannel.write(EVENT_BUFFER);
                                                    EVENT_BUFFER.clear();
                                                }while (readLength<bytes.length);
                                            }
                                           break outer;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }catch(IOException e){
            e.printStackTrace();
            //清空 receiveObj.getTar() 用户的channel缓存
            if(receiveObj != null){
                CONCURRENT_EVENT_MAP.remove(receiveObj.getTar());
            }
        }catch (ReflectiveOperationException e){
            e.printStackTrace();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        loopEvent();
    }
}
