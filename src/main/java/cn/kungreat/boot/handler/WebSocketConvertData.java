package cn.kungreat.boot.handler;

import cn.kungreat.boot.ConvertDataInHandler;
import cn.kungreat.boot.exp.WebSocketExceptional;
import cn.kungreat.boot.utils.CutoverBytes;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketConvertData implements ConvertDataInHandler<List<WebSocketConvertData.WebSocketData>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketConvertData.class);

    /*
     * 存放此SocketChannel 关联的任务列表
     * */
    public static final Map<Integer, LinkedList<WebSocketData>> WEB_SOCKET_STATE_TREEMAP = new ConcurrentHashMap<>(1024);


    @Override
    public void before(SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception {
        byteBuffer.flip();
        LinkedList<WebSocketData> listSos = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        if (listSos == null) {
            listSos = new LinkedList<>();
            listSos.add(new WebSocketData(999));
            WEB_SOCKET_STATE_TREEMAP.put(socketChannel.hashCode(), listSos);
        }
        loopConvertData(socketChannel,byteBuffer);
    }

    private void loopConvertData(final SocketChannel socketChannel,final ByteBuffer byteBuffer) throws Exception {
        LinkedList<WebSocketData> listSocket = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        WebSocketData socketData = listSocket.getLast();
        int remaining = byteBuffer.remaining();
        if (socketData.getType() == 999) {
            //初始最少需要6个字节才能解释协议
            if (remaining > 6) {
                byte[] array = byteBuffer.array();
                if ((array[0] & 15) != 0) {
                    socketData.setFinish(array[0] < 0);
                    if (array[1] < 0) {
                        socketData.setDateLength(array, remaining);
                        if (socketData.getDateLength() > 0 && socketData.getCurrentPos() + 4 < array.length) {
                            socketData.setMaskingKey(array);//设置掩码覆盖,转换后续数据
                            socketData.setType(array[0] & 15);
                            socketData.setByteBuffer(ByteBuffer.allocate(8192));
                        } else {
                            //当前的数据长度不够初始化协议的基本数据
                            socketData.setCurrentPos(0);
                            socketData.setDateLength(0);
                            return ;
                        }
                    } else {
                        LOGGER.error("协议mask标记位不正确 -> 关闭连接");
                        throw new WebSocketExceptional("协议mask标记位不正确 -> 关闭连接");
                    }
                } else {
                    LOGGER.error("警告类型解释失败 -> 关闭连接");
                    throw new WebSocketExceptional("警告类型解释失败 -> 关闭连接");
                }
            } else {
                return ;
            }
        }

        //TODO 按需要转换Socket数据 -> WebSocketData.

        if(socketData.isFinish() && socketData.getReadLength() != socketData.getDateLength()){
            throw new WebSocketExceptional("数据异常 -> 关闭连接");
        } else if (!socketData.isFinish() && socketData.getReadLength() == socketData.getDateLength()) {
            throw new WebSocketExceptional("数据异常 -> 关闭连接");
        } else if (socketData.isFinish()) {
            socketData.setDone(true);
            WebSocketData webSocketData = new WebSocketData(999);
            listSocket.add(webSocketData);
            loopConvertData(socketChannel,byteBuffer);
        }

    }

    @Override
    public List<WebSocketData> handler(SocketChannel socketChannel) throws Exception {
        return null;
    }

    @Override
    public void after(SocketChannel socketChannel) throws Exception {

    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel) throws Exception {

    }

    @Setter
    @Getter
    public static final class WebSocketData {

        public WebSocketData(int type) {
            this.type = type;
        }

        /**
         * finish  websocket 是否完成标识
         */
        private boolean finish;
        /**
         * done 是否一次完整的消息完成. finish==true && dateLength==readLength
         */
        private boolean done = false;
        /**
         * type  websocket 数据类型标识
         * 0  continuation frame
         * 1: 文本数据
         * 2: byte数据
         * 8: break 关闭的信号
         * 9:  ping
         * 10: pong
         * 999: 自定义的初始状态
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
        private final byte[] maskingKey = new byte[4];
        /**
         * maskingIndex  websocket 客户端掩码索引
         */
        private long maskingIndex;
        /*
         * 存放websocket字节数据
         * */
        private ByteBuffer byteBuffer;

        /**
         * isConvert 传送文件时使用 表示数据是否已经转换
         */
        private boolean isConvert;

        /**
         * currentPos websocket-byteBuffer 当前byte指针位置
         */
        private int currentPos;

        /*
         * 接收的数据转换成Map
         * */
        private Map<String, Object> convertData;

        public void setFinish(boolean finish) {
            this.finish = finish;
            this.currentPos++;
        }

        /* 如果有延续帧 把数据长度相加保留总长度  */
        public void setDateLength(byte[] byteArray, int remaining) {
            int lens = byteArray[1] & 127;
            if (lens < 126) {
                this.dateLength += lens;
                this.currentPos++;
            } else if (lens == 126) {
                if (remaining > 8) {
                    this.dateLength += CutoverBytes.readInt((byte) 0, (byte) 0, byteArray[2], byteArray[3]);
                    this.currentPos = this.currentPos + 3;
                }
            } else {
                if (remaining > 14) {
                    byte[] temp = {(byte) (byteArray[2] & 127), byteArray[3], byteArray[4], byteArray[5],
                            byteArray[6], byteArray[7], byteArray[8], byteArray[9]};
                    this.dateLength += CutoverBytes.readLong(temp);
                    this.currentPos = this.currentPos + 9;
                }
            }
        }

        public void setMaskingKey(byte[] maskingKey) {
            this.maskingKey[0] = maskingKey[this.currentPos++];
            this.maskingKey[1] = maskingKey[this.currentPos++];
            this.maskingKey[2] = maskingKey[this.currentPos++];
            this.maskingKey[3] = maskingKey[this.currentPos++];
        }

    }
}
