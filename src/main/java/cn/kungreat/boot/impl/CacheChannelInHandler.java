package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChannelInHandler;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.TreeMap;

public class CacheChannelInHandler implements ChannelInHandler<byte[],ByteBuffer> {

    @Override
    public ChannelInHandler<byte[], ByteBuffer> before(SocketChannel socketChannel, byte[] in) {
        return null;
    }

    @Override
    public ByteBuffer handler(TreeMap<Integer, ByteBuffer> treeMap,SocketChannel socketChannel, byte[] in) {
        return null;
    }

    @Override
    public ChannelInHandler<byte[], ByteBuffer> after(SocketChannel socketChannel, byte[] in) {
        return null;
    }
}
