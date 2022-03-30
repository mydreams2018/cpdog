package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelOutHandler;
import cn.kungreat.boot.impl.DefaultLogServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class WebSocketChannelOutHandler implements ChannelOutHandler<WebSocketChannelInHandler.WebSocketState,String> {

   public static Logger log;

    static {
        try {
            log = DefaultLogServer.createLog("C:\\Users\\kungr\\Videos\\Captures"
                        ,"cn.kungreat.boot.handler");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void before(SocketChannel socketChannel, WebSocketChannelInHandler.WebSocketState in) {

    }

    @Override
    public String handler(ByteBuffer byteBuffer, SocketChannel socketChannel, WebSocketChannelInHandler.WebSocketState in) throws Exception {
        if (in.isDone()) {
            log.info(in.getStringBuffer().toString());
        }
        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, WebSocketChannelInHandler.WebSocketState in) {

    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {

    }

    @Override
    public Class getInClass() {
        return WebSocketChannelInHandler.WebSocketState.class;
    }
}
