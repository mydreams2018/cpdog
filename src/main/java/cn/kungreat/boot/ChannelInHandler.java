package cn.kungreat.boot;

import java.nio.channels.SocketChannel;

public interface ChannelInHandler<I,O> {
    /*
     *@Description 正常链路情况下会先调此方法、出现异常时不调用此方法
     *@Param socketChannel 当前连接管道 in 入参
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    void before(SocketChannel socketChannel,I in);

    /*
     *@Description 正常链路情况下会在中间调用此方法,异常时也会调用此方法
     *@Param socketChannel 当前连接管道 in 入参
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    O handler(SocketChannel socketChannel, I in);

    /*
     *@Description 正常链路情况下会后调此方法、出现异常时不调用此方法
     *@Param socketChannel 当前连接管道 in 入参
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    void after(SocketChannel socketChannel,I in);
    /*
    *@Description 出现异常需要处理.可选关闭通道 或者自已处理 但是要保证下一个链路的入参正常接收
    *@Param e 异常信息 socketChannel 当前连接管道 in 入参
    *@Return 无
    *@Date 2022/3/18
    *@Time 11:09
    */
    void exception(Exception e,SocketChannel socketChannel,Object in);
    /*
     *@Description 返回当前入参的类型,为了方便反射调用
     *@Param 无
     *@Return 返回入参类型
     *@Date 2022/3/18
     *@Time 11:09
     */
    Class<I> getInClass();
}
