package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelOutHandler;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class DefautChannelOutHandler implements ChannelOutHandler<String,String> {

    @Override
    public void before(SocketChannel socketChannel, String in) {
        System.out.println(this.getClass()+":before");
    }

    @Override
    public String handler(ByteBuffer byteBuffer,SocketChannel socketChannel, String in) throws Exception {
        byteBuffer.put(in.getBytes(Charset.forName("UTF-8")));
        byteBuffer.flip();
        socketChannel.write(byteBuffer);
        return in;
    }

    @Override
    public void after(SocketChannel socketChannel, String in) {
        System.out.println(this.getClass()+":after");
    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        socketChannel.close();
    }

    @Override
    public Class<String> getInClass() {
        return String.class;
    }
}
