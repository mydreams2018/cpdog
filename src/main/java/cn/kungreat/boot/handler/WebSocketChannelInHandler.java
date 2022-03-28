package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.utils.CutoverBytes;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.TreeMap;

public class WebSocketChannelInHandler implements ChannelInHandler<ByteBuffer,String> {

    private static final TreeMap<Integer,WebSocketState> WEBSOCKETSTATETREEMAP = new TreeMap<>();

    @Override
    public void before(SocketChannel socketChannel,ByteBuffer buffer, ByteBuffer in) throws Exception {
        WebSocketState webSocketState = WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode());
        if(webSocketState == null){
            webSocketState = new WebSocketState();
            WEBSOCKETSTATETREEMAP.put(socketChannel.hashCode(),webSocketState);
        }
        if(webSocketState.getDateLength()==0){
            byte[] array = buffer.array();
            webSocketState.setFinish(array[0]<0);
            webSocketState.setType(array[0]&15);
            if(array[1]<0){
                webSocketState.setDateLength(array);
                webSocketState.setMaskingKey(array);
                int currentPos = webSocketState.getCurrentPos();
                //拿到当前指针的位置开始取真实的数据
            }else{
                System.out.println("协议mask标记位不正确关闭连接:");
                socketChannel.close();
                WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
            }
        }else{
            //读以前的旧数据
        }


    }

    @Override
    public String handler(SocketChannel socketChannel,ByteBuffer buffer, ByteBuffer in) throws Exception{
        System.out.println(this.getClass()+":handler");
        byte[] array = in.array();
        return new String(array,0,in.remaining(), Charset.forName("UTF-8"));
    }

    @Override
    public void after(SocketChannel socketChannel,ByteBuffer buffer, ByteBuffer in) {
        in.clear();//当前这种情况是清空数据、不是所有都这样 注意协议消费
        System.out.println(this.getClass()+":after");
    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel,ByteBuffer buffer, Object in) throws Exception {
        socketChannel.close();
    }

    @Override
    public Class<ByteBuffer> getInClass() {
        return ByteBuffer.class;
    }

    static final class WebSocketState{
        /**
         * finish  websocket 是否完成标识
         */
        private boolean finish;
        /**
         * type  websocket 数据类型标识
         *  1: 文本数据
         *  2: byte数据
         *  8: break
         *  9:  ping
         *  10: pong
         */
        private int type;
        /**
         * dateLength  websocket 数据长度标识
         */
        private long dateLength;
        /**
         * maskingKey  websocket 客户端掩码
         */
        private byte[] maskingKey = new byte[4];
        /**
         *currentPos websocket 当前byte指针位置
         */
        private int currentPos;

        public boolean isFinish() {
            return finish;
        }

        public void setFinish(boolean finish) {
            this.finish = finish;
            this.currentPos++;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public long getDateLength() {
            return dateLength;
        }

        public void setDateLength(byte[] dateLength){
            int lens = dateLength[1] & 127;
            if(lens<126){
                this.dateLength = lens;
                this.currentPos++;
            }else if(lens==126){
                this.dateLength=CutoverBytes.readInt((byte) 0,(byte) 0,dateLength[2],dateLength[3]);
                this.currentPos= this.currentPos+3;
            }else{
                byte[] temp = {dateLength[2],dateLength[3],dateLength[4], dateLength[5],
                        dateLength[6],dateLength[7],dateLength[8],dateLength[9]};
                this.dateLength=CutoverBytes.readLong(temp);
                this.currentPos= this.currentPos+9;
            }
        }

        public byte[] getMaskingKey() {
            return maskingKey;
        }

        public void setMaskingKey(byte[] maskingKey) {
            this.maskingKey[0]=maskingKey[this.currentPos++];
            this.maskingKey[1]=maskingKey[this.currentPos++];
            this.maskingKey[2]=maskingKey[this.currentPos++];
            this.maskingKey[3]=maskingKey[this.currentPos++];
        }

        public int getCurrentPos() {
            return currentPos;
        }

        public void setCurrentPos(int currentPos) {
            this.currentPos = currentPos;
        }
    }
}
