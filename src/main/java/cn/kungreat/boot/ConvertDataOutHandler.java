package cn.kungreat.boot;

import cn.kungreat.boot.handler.WebSocketConvertData;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/*
 * 根据入参对象 通过反射去调用指定的方法
 * 再把方法返回值写出到客户端去
 * */
public interface ConvertDataOutHandler<T> {
    /*
     *@Description 之前的处理
     *@Param T 入参对象
     *@Return 无
     *@Date 2023/09/15
     */
    void before(T t, SocketChannel socketChannel) throws Exception;

    /*
     *@Description 根据入参对象 通过反射去调用指定的方法 再把方法返回值写出到客户端去
     *@Param T 入参对象
     *@Return 调用指定的方法返回的数据
     *@Date 2023/09/15
     */
    void handler(T t, ByteBuffer byteBuffer, SocketChannel socketChannel) throws Exception;

    /*
     *@Description 之后的处理
     *@Param SocketChannel 客户端流 ByteBuffer 输出流
     *@Date 2023/09/15
     */
    void after(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception;

    /*
     *@Description 出现异常需要处理
     *@Param e 异常信息 socketChannel 当前连接管道
     *@Date 2022/3/18
     *@Time 11:09
     */
    void exception(Exception e, SocketChannel socketChannel);

}
