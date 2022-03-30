package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelOutHandler;
import cn.kungreat.boot.impl.DefaultLogServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.logging.Logger;

public class WebSocketChannelOutHandler implements ChannelOutHandler<LinkedList<WebSocketChannelInHandler.WebSocketState>,String> {

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
    public void before(SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) {

    }

    @Override
    public String handler(ByteBuffer byteBuffer, SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) throws Exception {
        WebSocketChannelInHandler.WebSocketState first = in.getFirst();
        while (first.getType() == 1 && first.isDone()) {
            log.info(socketChannel.getRemoteAddress() +first.getStringBuffer().toString());
            in.removeFirst();
            first = in.getFirst();
        }
        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) {

    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        e.printStackTrace();
        socketChannel.close();
    }

    @Override
    public Class getInClass() {
        return LinkedList.class;
    }
}
