package cn.kungreat.boot;

import java.nio.channels.SocketChannel;

public interface ChannelInHandler<I,O> {

    void before(SocketChannel socketChannel,I in);

    O handler(SocketChannel socketChannel, I in);

    void after(SocketChannel socketChannel,I in);

    Class<I> getInClass();
}
