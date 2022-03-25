package cn.kungreat.boot;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface ChannelProtocolHandler {

    /*
     *@Description 协议处理器 如果有问题请关闭此流.socketChannel.close()
     *@Param socketChannel 当前连接管道 in 原始流入参
     *@Return boolean true 的话表示协议处理成功.下次数据进来将不再走此链路. false 下次数据进来还是走此链路
     *@Date 2022/3/25
     *@Time 11:09
     */
    boolean handlers(SocketChannel socketChannel, ByteBuffer in) throws Exception;;

}
