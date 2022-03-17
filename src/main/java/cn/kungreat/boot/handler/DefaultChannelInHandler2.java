package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;

import java.nio.channels.SocketChannel;

public class DefaultChannelInHandler2 implements ChannelInHandler<String,Integer> {

    @Override
    public void before(SocketChannel socketChannel, String in) {
        System.out.println(this.getClass()+"before");
    }

    @Override
    public Integer handler(SocketChannel socketChannel, String in) {
        System.out.println(this.getClass()+""+in);
        return 50;
    }

    @Override
    public void after(SocketChannel socketChannel, String in) {
        System.out.println(this.getClass()+"after");
    }

    @Override
    public Class<String> getInClass() {
        return String.class;
    }
}
