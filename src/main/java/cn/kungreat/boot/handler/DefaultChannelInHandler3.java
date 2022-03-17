package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;

import java.nio.channels.SocketChannel;

public class DefaultChannelInHandler3 implements ChannelInHandler<Integer,StringBuffer> {

    @Override
    public void before(SocketChannel socketChannel, Integer in) {
        System.out.println(this.getClass()+"before");
    }

    @Override
    public StringBuffer handler(SocketChannel socketChannel, Integer in) {
        System.out.println(this.getClass()+""+in);
        return new StringBuffer("ENENED");
    }

    @Override
    public void after(SocketChannel socketChannel, Integer in) {
        System.out.println(this.getClass()+"after");
    }

    @Override
    public Class<Integer> getInClass() {
        return Integer.class;
    }
}
