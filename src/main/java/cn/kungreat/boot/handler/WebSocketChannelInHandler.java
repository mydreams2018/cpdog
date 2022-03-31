package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.utils.CutoverBytes;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.TreeMap;

/*
* 把websocket 的数据 解码出来并且 传入下一个链路
*/
public class WebSocketChannelInHandler implements ChannelInHandler<ByteBuffer, LinkedList<WebSocketChannelInHandler.WebSocketState>> {
    //在前边就关闭了的连接  历史数据清理的问题  通过拿到所有选择器 channel 比较 hashcode todo
    public static final TreeMap<Integer,LinkedList<WebSocketState>> WEBSOCKETSTATETREEMAP = new TreeMap<>();

    @Override
    public void before(SocketChannel socketChannel,ByteBuffer buffer) throws Exception {
        buffer.flip();
        LinkedList<WebSocketState> listSos = WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode());
        if (listSos == null) {
            listSos = new LinkedList<>();
            listSos.add(new WebSocketState(buffer.capacity()));
            WEBSOCKETSTATETREEMAP.put(socketChannel.hashCode(), listSos);
        }
        WebSocketState webSocketState = listSos.getLast();
        long remainingTotal = webSocketState.getDateLength() - webSocketState.getReadLength();
        if (remainingTotal == 0) {
            int remaining = buffer.remaining();
            //最少需要6个字节才能解释 协议
            if (remaining > 6) {
                //需要初始化这个
                webSocketState.setCurrentPos(0);
                byte[] array = buffer.array();
                webSocketState.setFinish(array[0] < 0);
                webSocketState.setType(array[0] & 15);
                if (array[1] < 0) {
                    //设置数据长度
                    webSocketState.setDateLength(array,remaining);
                    webSocketState.setMaskingKey(array);
                } else{
                    System.out.println("协议mask标记位不正确关闭连接:");
                    socketChannel.close();
                    WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
                }
            }
        }
    }

    @Override
    public LinkedList<WebSocketState> handler(SocketChannel socketChannel, ByteBuffer buffer) throws Exception {
        if(socketChannel.isOpen()){
            WebSocketState webSocketState = WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode()).getLast();
            int currentPos = webSocketState.getCurrentPos();
            int remaining = buffer.remaining();
            long remainingTotal = webSocketState.getDateLength()-webSocketState.getReadLength();
            //拿到当前指针的位置开始取真实的数据
            if (currentPos < remaining && remainingTotal > 0) {
                byte[] array = buffer.array();
                //拿到必需要的数据 还有额外的数据读.......
                long maxReadLength = remaining - currentPos;
                ByteBuffer tarBuffer = webSocketState.getByteBuffer();
                long runLength = Math.min(tarBuffer.remaining(), Math.min(maxReadLength, remainingTotal));
                //当前已经读取的长度索引
                long readLength = webSocketState.getReadLength();
                byte[] maskingKey = webSocketState.getMaskingKey();
                for (int x = 0; x < runLength; x++) {
                    byte tar = (byte) (array[currentPos] ^ maskingKey[Math.floorMod(readLength, 4)]);
                    tarBuffer.put(tar);
                    currentPos++;
                    readLength++;
                }
                webSocketState.setReadLength(readLength);
                buffer.position(currentPos);
                webSocketState.setCurrentPos(0);
            }
            buffer.compact();
            return WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode());
        }else{
            WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
        }
        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer buffer) throws Exception {
        if(socketChannel.isOpen()){
            WebSocketState webSocketState = WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode()).getLast();
            if(webSocketState.isFinish() && webSocketState.getReadLength()==webSocketState.getDateLength()){
                webSocketState.setDone(true);
            }
            if(webSocketState.getType()==1){
                webSocketState.setStringData();
            }
            if(webSocketState.isDone()){
                WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode()).add(new WebSocketState(buffer.capacity()));
                loopData(socketChannel,buffer);
            }
        }else{
            WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
        }
    }
    /*
     *@Description TCP-IP 有可能一次到达多个信息.在一个消息完成后 判断需要有追加处理
     *@Param socketChannel 当前连接管道 byteBuffer原始读取的字节数据缓冲区需要转换
     *@Return 无
     *@Date 2022/3/18
     *@Time 11:09
     */
    private void loopData(SocketChannel socketChannel,ByteBuffer buffer) throws Exception {
        before(socketChannel,buffer);
        handler(socketChannel,buffer);
        after(socketChannel,buffer);
    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        socketChannel.close();
    }

    @Override
    public Class<ByteBuffer> getInClass() {
        return ByteBuffer.class;
    }

    static final class WebSocketState {

        private WebSocketState(int length) {
            byteBuffer = ByteBuffer.allocate(length);
        }

        /**
         * finish  websocket 是否完成标识
         */
        private boolean finish;
        /**
         * done 是否一次完整的消息完成. finish==true && dateLength==readLength
         */
        private boolean done=false;
        /**
         * stringBuffer  type==1 存放的文字内容
         */
        private StringBuffer stringBuffer = new StringBuffer();
        /**
         * type  websocket 数据类型标识
         * 1: 文本数据
         * 2: byte数据
         * 8: break
         * 9:  ping
         * 10: pong
         */
        private int type;
        /**
         * dateLength  websocket 数据长度标识
         */
        private long dateLength;

        /**
         * readLength  websocket 已经读取的长度
         */
        private long readLength;
        /**
         * maskingKey  websocket 客户端掩码
         */
        private byte[] maskingKey = new byte[4];
        /**
         * currentPos websocket 当前byte指针位置
         */
        private int currentPos;

        private ByteBuffer byteBuffer;

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

        public void setDateLength(byte[] dateLength,int remaining) {
            int lens = dateLength[1] & 127;
            if (lens < 126) {
                this.dateLength += lens;
                this.currentPos++;
            } else if (lens == 126){
                if(remaining>8){
                    this.dateLength += CutoverBytes.readInt((byte) 0, (byte) 0, dateLength[2], dateLength[3]);
                    this.currentPos = this.currentPos + 3;
                }else{
                    //当前数据 不够支持读取长度
                    this.dateLength=0;
                }
            } else {
                if(remaining>14){
                    byte[] temp = {dateLength[2], dateLength[3], dateLength[4], dateLength[5],
                            dateLength[6], dateLength[7], dateLength[8], dateLength[9]};
                    this.dateLength += CutoverBytes.readLong(temp);
                    this.currentPos = this.currentPos + 9;
                }else{
                    //当前数据 不够支持读取长度
                    this.dateLength=0;
                }
            }
        }

        public byte[] getMaskingKey() {
            return maskingKey;
        }

        public void setMaskingKey(byte[] maskingKey) {
            this.maskingKey[0] = maskingKey[this.currentPos++];
            this.maskingKey[1] = maskingKey[this.currentPos++];
            this.maskingKey[2] = maskingKey[this.currentPos++];
            this.maskingKey[3] = maskingKey[this.currentPos++];
        }

        public int getCurrentPos() {
            return currentPos;
        }

        public void setCurrentPos(int currentPos) {
            this.currentPos = currentPos;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        public void setByteBuffer(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
        }

        public long getReadLength() {
            return readLength;
        }

        public void setReadLength(long readLength) {
            this.readLength = readLength;
        }

        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        public StringBuffer getStringBuffer() {
            return stringBuffer;
        }

        /*
         编码转换成字符串 默认用UTF-8
        */
        public void setStringData() {
            byteBuffer.flip();
            int remaining = byteBuffer.remaining();
            if(!done){
                int ix = 0;
                do{
                    ix++;
                    byte b = byteBuffer.get(remaining - ix);
                    if(b>0 || (b<=-12 && b>=-62)){
                        break;
                    }
                }while(true);
                stringBuffer.append(new String(byteBuffer.array(),0,remaining-ix,Charset.forName("UTF-8")));
                byteBuffer.position(remaining-ix);
                byteBuffer.compact();
            }else{
                stringBuffer.append(new String(byteBuffer.array(),0,remaining,Charset.forName("UTF-8")));
                byteBuffer.clear();
            }
        }
    }
}
