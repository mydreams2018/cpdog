package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelOutHandler;
import cn.kungreat.boot.impl.DefaultLogServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.LinkedList;
import java.util.logging.Logger;

public class WebSocketChannelOutHandler implements ChannelOutHandler<LinkedList<WebSocketChannelInHandler.WebSocketState>,String> {

   public static Logger log;

    static {
        try {
            log = DefaultLogServer.createLog("D:\\kungreat\\IdeaProjects\\log1"
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
        if(in != null){
            WebSocketChannelInHandler.WebSocketState first = in.peekFirst();
            while (first != null) {
                if(first.getType() == 1 && first.isDone()){
                    log.info(socketChannel.getRemoteAddress() + "src:"+first.getSrc()+" chatrs:"+first.getCharts()+" tar:"+first.getTar());
                    in.removeFirst();
                    first = in.peekFirst();
                }else if(first.getType() == 2 && first.isDone()){
                    //写出文件 FileSystems.getDefault()
                    System.out.println("文件写出完毕:"+first.getFileName());
                    ByteBuffer fileBuffer = first.getByteBuffer();
                    fileBuffer.flip();
                    byte[] bts = new byte[fileBuffer.remaining()];
                    System.arraycopy(fileBuffer.array(),0,bts,0,fileBuffer.remaining());
                    Files.write(first.getFilePath(),bts, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    fileBuffer.clear();
                    in.removeFirst();
                    first = in.peekFirst();
                }else if(first.getType() == 2 && first.getFilePath() != null){
                    //写出文件
                    ByteBuffer fileBuffer = first.getByteBuffer();
                    fileBuffer.flip();
                    byte[] bts = new byte[fileBuffer.remaining()];
                    System.arraycopy(fileBuffer.array(),0,bts,0,fileBuffer.remaining());
                    Files.write(first.getFilePath(),bts, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    fileBuffer.clear();
                    break;
                }else if(first.getType() == 8){
                    System.out.println("out type ==8 ");
                    break;
                }else if(first.getType() == 999){
                    break;
                }else if(first.getType() == 2){//第一次产生二进制数据 但是还没有读完全的情况
                    break;
                }else if(first.getType() == 1){//文本数据过大时没有读取完成不做操作
                    break;
                }else{
                    System.out.println("out type 未知");
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, LinkedList<WebSocketChannelInHandler.WebSocketState> in) {

    }

    @Override
    public LinkedList exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        e.printStackTrace();
        socketChannel.close();
        WebSocketChannelInHandler.WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
        WebSocketChannelInHandler.WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
        return null;
    }

    @Override
    public Class getInClass() {
        return LinkedList.class;
    }
}
