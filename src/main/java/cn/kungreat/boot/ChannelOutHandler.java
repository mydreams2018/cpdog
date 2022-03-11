package cn.kungreat.boot;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.TreeMap;

public interface ChannelOutHandler<I,O> {

    ChannelOutHandler<I,O> before(SocketChannel socketChannel, I in);

    O handler(TreeMap<Integer, ByteBuffer> treeMap,SocketChannel socketChannel, I in);

    ChannelOutHandler<I,O> after(SocketChannel socketChannel, I in);

}
