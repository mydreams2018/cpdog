package cn.kungreat.boot;

import java.nio.channels.SocketChannel;

public interface ChannelInHandler<I,O> {
    /*
     *@Description 正常链路情况下会先调此方法、出现异常时不调用此方法
     *@Param socketChannel byteBuffer原始读取的字节数据缓冲区需要转换 当前连接管道 in 入参
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    void before(SocketChannel socketChannel, I in) throws Exception;

    /*
     *@Description 正常链路情况下会在中间调用此方法,异常时也会调用此方法
     *@Param socketChannel 当前连接管道 byteBuffer原始读取的字节数据缓冲区需要转换 in 入参
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    O handler(SocketChannel socketChannel, I in) throws Exception;

    /*
     *@Description 正常链路情况下会后调此方法、出现异常时不调用此方法
     *@Param socketChannel 当前连接管道 byteBuffer原始读取的字节数据缓冲区需要转换 in 入参
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    void after(SocketChannel socketChannel,I in) throws Exception;
    /*
    *@Description 出现异常需要处理.可选关闭通道 或者自已处理 但是要保证当前handler链路的入参正常接收
    *@Param e 异常信息 socketChannel 当前连接管道 byteBuffer原始读取的字节数据缓冲区需要转换 in 入参
    *@throws 可能出现在异常
    *@Return  handler 的入参
    *@Date 2022/3/18
    *@Time 11:09
    */
    I exception(Exception e,SocketChannel socketChannel,Object in) throws Exception;
    /*
     *@Description 返回当前入参的类型,为了方便反射调用
     *@Param 无
     *@Return 返回入参类型
     *@Date 2022/3/18
     *@Time 11:09
     */
    Class<I> getInClass();
    /*
     *@Description 清理数据的操作.针对那些特殊情况下关闭的资源的回调. 已经关闭的资源
     *@Param socketChannel 唯一的对象
     *@Date 2022/4/6
     *@Time 11:09
     */
    void clearBuffers(SocketChannel socketChannel);
}
