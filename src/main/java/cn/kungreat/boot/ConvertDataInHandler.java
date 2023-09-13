package cn.kungreat.boot;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/*
* 把已经解密的数据解释出来形成对象
* */
public interface ConvertDataInHandler<E> {
    /*
     *@Param socketChannel 当前的套接字连接  byteBuffer原始读取的字节数据缓冲区需要转换
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    void before(SocketChannel clientChannel, ByteBuffer byteBuffer) throws Exception;

    /*
     *@Param socketChannel 当前的套接字连接  byteBuffer原始读取的字节数据缓冲区需要转换
     *@Return E 协议转换出来的数据
     *@Date 2022/3/18
     *@Time 11:09
     */
    E handler(SocketChannel socketChannel) throws Exception;

    /*
     *@Param socketChannel 当前的套接字连接  byteBuffer原始读取的字节数据缓冲区需要转换
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    void after(SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception;

    /*
     *@Description 出现异常需要处理
     *@Param e 异常信息 socketChannel 当前连接管道
     *@Date 2022/3/18
     *@Time 11:09
     */
    void exception(Exception e,SocketChannel socketChannel);

}
