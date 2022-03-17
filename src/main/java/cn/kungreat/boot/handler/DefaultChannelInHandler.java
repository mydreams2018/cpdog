package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class DefaultChannelInHandler implements ChannelInHandler<ByteBuffer,String> {

    @Override
    public void before(SocketChannel socketChannel, ByteBuffer in) {
        System.out.println(this.getClass()+"before");
    }

    @Override
    public String handler(SocketChannel socketChannel, ByteBuffer in) {
        System.out.println(this.getClass()+"handler");
        return "第一个";
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer in) {
        System.out.println(this.getClass()+"after");
    }

    @Override
    public Class<ByteBuffer> getInClass() {
        return ByteBuffer.class;
    }
}
