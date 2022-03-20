package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class DefaultChannelInHandler implements ChannelInHandler<ByteBuffer,String> {

    @Override
    public void before(SocketChannel socketChannel, ByteBuffer in) {
        in.flip();//切换读模式
        System.out.println(this.getClass()+":before");
    }

    @Override
    public String handler(SocketChannel socketChannel, ByteBuffer in) throws Exception{
        System.out.println(this.getClass()+":handler");
        byte[] array = in.array();
        return new String(array,0,in.remaining(), Charset.forName("UTF-8"));
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer in) {
        in.clear();//当前这种情况是清空数据、不是所有都这样 注意协议消费
        System.out.println(this.getClass()+":after");
    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        socketChannel.close();
    }

    @Override
    public Class<ByteBuffer> getInClass() {
        return ByteBuffer.class;
    }
}
