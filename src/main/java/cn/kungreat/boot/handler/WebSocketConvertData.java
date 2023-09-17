package cn.kungreat.boot.handler;

import cn.kungreat.boot.ConvertDataInHandler;
import cn.kungreat.boot.exp.WebSocketExceptional;
import cn.kungreat.boot.utils.CutoverBytes;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
 * 此类把数据转换为WebSocketData对象
 * 一次进来最可能多的转换成WebSocketData对象
 * */

public class WebSocketConvertData implements ConvertDataInHandler<List<WebSocketConvertData.WebSocketData>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketConvertData.class);

    /*
     * 一个WORK对象绑定一个减少并发, 存放此SocketChannel 关联的任务列表
     * */
    private final Map<Integer, LinkedList<WebSocketData>> WEB_SOCKET_STATE_TREEMAP = new HashMap<>(1024);
    /*
     * 一个WORK对象绑定一个减少并发, 二进制数据用来做的缓存
     * */
    private final Map<Integer, String> WEB_SOCKET_STATE_BYTES = new HashMap<>(1024);

    public static final ObjectMapper MAP_JSON = new ObjectMapper();

    @Override
    public void before(SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception {
        byteBuffer.flip();
        LinkedList<WebSocketData> listSos = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        if (listSos == null) {
            listSos = new LinkedList<>();
            listSos.add(new WebSocketData(999));
            WEB_SOCKET_STATE_TREEMAP.put(socketChannel.hashCode(), listSos);
        }
        loopConvertData(socketChannel, byteBuffer);
    }

    private void loopConvertData(final SocketChannel socketChannel, final ByteBuffer byteBuffer) throws Exception {
        LinkedList<WebSocketData> listSocket = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        WebSocketData socketData = listSocket.getLast();
        int remaining = byteBuffer.remaining();
        if (socketData.getType() == 999) {
            //初始最少需要6个字节才能解释协议
            if (remaining > 6) {
                byte[] array = byteBuffer.array();
                if ((array[byteBuffer.position()] & 15) != 0) {
                    socketData.setFinish(array[byteBuffer.position()] < 0);
                    if (array[byteBuffer.position() + 1] < 0) {
                        socketData.setDateLength(array, remaining, byteBuffer);
                        if (socketData.getDateLength() > 0 && socketData.getCurrentPos() + 4 < remaining) {
                            socketData.setMaskingKey(array, byteBuffer);//设置掩码覆盖,转换后续数据
                            socketData.setByteBuffer(ByteBuffer.allocate(16384));
                            socketData.setType(array[byteBuffer.position()] & 15);
                            byteBuffer.position(byteBuffer.position() + socketData.getCurrentPos());
                            remaining = byteBuffer.remaining();
                        } else {
                            //当前的数据长度不够初始化协议的基本数据
                            socketData.setCurrentPos(0);
                            socketData.setDateLength(0);
                            return;
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
                return;
            }
        }
        ByteBuffer targetBuffer = socketData.getByteBuffer();
        //拿到最小可以转换的数据长度
        long remainingTotal = Math.min(remaining, socketData.getDateLength() - socketData.getReadLength());
        if (targetBuffer.remaining() < remainingTotal) {
            //在此种情况下必需要扩容
            int needGrow = (int) (remainingTotal - targetBuffer.remaining());
            LOGGER.info("targetBuffer扩容{}", needGrow);
            ByteBuffer allocateGrow = ByteBuffer.allocate(targetBuffer.capacity() + needGrow);
            targetBuffer.flip();
            allocateGrow.put(targetBuffer);
            socketData.setByteBuffer(allocateGrow);
            targetBuffer = allocateGrow;
        }
        if (remainingTotal > 0) {
            byte[] array = byteBuffer.array();
            long readLength = socketData.getReadLength();
            long maskingIndex = socketData.getMaskingIndex();
            byte[] maskingKey = socketData.getMaskingKey();
            for (int i = 0; i < remainingTotal; i++) {
                byte tarByte = (byte) (array[byteBuffer.position() + i] ^ maskingKey[Math.floorMod(maskingIndex, 4)]);
                targetBuffer.put(tarByte);
                readLength++;
                maskingIndex++;
            }
            socketData.setReadLength(readLength);
            socketData.setMaskingIndex(maskingIndex);
            byteBuffer.position((int) (byteBuffer.position() + remainingTotal));
        }
        if (socketData.getType() != 999 && !socketData.isFinish() && socketData.getReadLength() == socketData.getDateLength()
                && byteBuffer.hasRemaining()) {
            if ((byteBuffer.get(byteBuffer.position()) & 15) == 0) {
                //这是一个延续帧
                socketData.setCurrentPos(0);
                socketData.setMaskingIndex(0);
                remaining = byteBuffer.remaining();
                if (remaining > 6) {
                    byte[] array = byteBuffer.array();
                    socketData.setFinish(array[byteBuffer.position()] < 0);
                    if (array[byteBuffer.position() + 1] < 0) {
                        long tempLength = socketData.getDateLength();
                        socketData.setDateLength(array, remaining, byteBuffer);
                        if (socketData.getDateLength() > tempLength && socketData.getCurrentPos() + 4 < remaining) {
                            socketData.setMaskingKey(array, byteBuffer);//设置掩码覆盖,转换后续数据
                            byteBuffer.position(byteBuffer.position() + socketData.getCurrentPos());
                            loopConvertData(socketChannel, byteBuffer);
                        } else {
                            //当前的数据长度不够初始化协议的基本数据
                            socketData.setCurrentPos(0);
                            socketData.setDateLength(tempLength);
                        }
                    } else {
                        LOGGER.error("协议mask标记位不正确 -> 关闭连接");
                        throw new WebSocketExceptional("协议mask标记位不正确 -> 关闭连接");
                    }
                }
            } else {
                throw new WebSocketExceptional("数据异常 -> 关闭连接");
            }
        } else if (socketData.isFinish() && socketData.getReadLength() == socketData.getDateLength()) {
            socketData.setDone(true);
            WebSocketData webSocketData = new WebSocketData(999);
            listSocket.add(webSocketData);
            loopConvertData(socketChannel, byteBuffer);
        }
    }

    @Override
    public List<WebSocketData> handler(SocketChannel socketChannel) throws Exception {
        LinkedList<WebSocketData> listSocket = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        Iterator<WebSocketData> dataIterator = listSocket.iterator();
        while (dataIterator.hasNext()) {
            WebSocketData socketData = dataIterator.next();
            if (socketData.getType() == 1 && socketData.getReceiveObj() == null) {
                socketData.setStringData();
            } else if (socketData.getType() == 2) {
                socketData.setByteData(dataIterator, WEB_SOCKET_STATE_BYTES, socketChannel);
            } else if (socketData.getType() == 8) {
                LOGGER.info("退出信号{}", socketChannel.getRemoteAddress());
            }
        }
        return listSocket;
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer byteBuffer) throws Exception {
        byteBuffer.compact();
    }

    @Override
    public void exception(Exception e, SocketChannel socketChannel) {
        LOGGER.error(e.getLocalizedMessage());
        try {
            socketChannel.close();
            WEB_SOCKET_STATE_TREEMAP.remove(socketChannel.hashCode());
            WEB_SOCKET_STATE_BYTES.remove(socketChannel.hashCode());
        } catch (Exception ex) {
            LOGGER.error(ex.getLocalizedMessage());
        }
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
         * StringBuilder  缓存
         */
        private StringBuilder stringBuilder = new StringBuilder();
        private final CharBuffer charBuffer = CharBuffer.allocate(1024);
        private static final CharsetDecoder CHARSET_DECODER = StandardCharsets.UTF_8.newDecoder();

        private WebSocketConvertData.ReceiveObj receiveObj;
        /**
         * isConvert 传送文件时使用 表示数据是否已经转换
         */
        private boolean isConvert;

        /**
         * currentPos websocket-byteBuffer 当前byte指针位置
         */
        private int currentPos;

        /*
         * 用来存放过滤链路上的数据转换成Map
         * 不存在多线程并发性,除非自已创建新的线程来操作此对象
         * */
        private Map<String, Object> convertData;

        /*
         * 用来存放返回给前端的数据,比如在执行链路上出错时可以初始化此数据.
         * 如果此数据不为空的话,将把此数据作为此次请求将响应的数据.
         * 正常链路时不要设置此值
         * 不存在多线程并发性,除非自已创建新的线程来操作此对象
         * */
        private Map<String, Object> sendBackMessage;

        public void setFinish(boolean finish) {
            this.finish = finish;
            this.currentPos++;
        }

        public void setByteBuffer(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            if (this.type == 2 && this.isConvert) {
                //在扩容时 同步文件时需要此操作
                this.receiveObj.setFileReceiveConvert(byteBuffer);
            }
        }

        /* 如果有延续帧 把数据长度相加保留总长度  */
        public void setDateLength(byte[] byteArray, int remaining, ByteBuffer byteBuffer) {
            int lens = byteArray[byteBuffer.position() + 1] & 127;
            if (lens < 126) {
                this.dateLength += lens;
                this.currentPos++;
            } else if (lens == 126) {
                if (remaining > 8) {
                    this.dateLength += CutoverBytes.readInt((byte) 0, (byte) 0, byteArray[byteBuffer.position() + 2],
                            byteArray[byteBuffer.position() + 3]);
                    this.currentPos = this.currentPos + 3;
                }
            } else {
                if (remaining > 14) {
                    byte[] temp = {(byte) (byteArray[byteBuffer.position() + 2] & 127), byteArray[byteBuffer.position() + 3],
                            byteArray[byteBuffer.position() + 4], byteArray[byteBuffer.position() + 5],
                            byteArray[byteBuffer.position() + 6], byteArray[byteBuffer.position() + 7],
                            byteArray[byteBuffer.position() + 8], byteArray[byteBuffer.position() + 9]};
                    this.dateLength += CutoverBytes.readLong(temp);
                    this.currentPos = this.currentPos + 9;
                }
            }
        }

        public void setMaskingKey(byte[] maskingKey, ByteBuffer byteBuffer) {
            this.maskingKey[0] = maskingKey[byteBuffer.position() + this.currentPos++];
            this.maskingKey[1] = maskingKey[byteBuffer.position() + this.currentPos++];
            this.maskingKey[2] = maskingKey[byteBuffer.position() + this.currentPos++];
            this.maskingKey[3] = maskingKey[byteBuffer.position() + this.currentPos++];
        }

        /*
         *   编码转换成字符串 默认用UTF-8 CHARSET_DECODER  charBuffer
         */
        public void setStringData() throws Exception {
            byteBuffer.flip();
            if (!done) {
                CoderResult coderResult;
                do {
                    coderResult = CHARSET_DECODER.decode(byteBuffer, charBuffer, false);
                    charBuffer.flip();
                    stringBuilder.append(charBuffer);
                    charBuffer.clear();
                } while (coderResult.isOverflow());
                if (coderResult.isError()) {
                    throw new WebSocketExceptional("!!!解码错误-111!!!");
                }
                byteBuffer.compact();
            } else {
                int remaining = byteBuffer.remaining();
                stringBuilder.append(new String(byteBuffer.array(), 0, remaining, StandardCharsets.UTF_8));
                //接收字符串本次完成了解释成对象
                WebSocketConvertData.ReceiveObj receiveObj = MAP_JSON.readValue(stringBuilder.toString(), WebSocketConvertData.ReceiveObj.class);
                if (receiveObj == null) {
                    LOGGER.error("字符内容解释出错:关闭连接");
                    throw new WebSocketExceptional("字符内容解释出错 -> 关闭连接");
                }
                this.receiveObj = receiveObj;
                this.byteBuffer.clear();
                this.stringBuilder = null;
            }
        }

        //二进制数据时做的数据处理 由于是分二次发送所以第一次的二进制数据是 相关的源信息
        public void setByteData(Iterator<WebSocketData> dataIterator, Map<Integer, String> webSocketStateBytes, SocketChannel socketChannel) throws Exception {
            final int hashcode = socketChannel.hashCode();
            if (this.done) {
                if (webSocketStateBytes.get(hashcode) == null) {
                    this.byteBuffer.flip();
                    int remaining = this.byteBuffer.remaining();
                    webSocketStateBytes.put(hashcode, new String(this.byteBuffer.array(), 0, remaining, StandardCharsets.UTF_8));
                    dataIterator.remove();
                    this.byteBuffer.clear();
                } else {
                    //第二次进来一次就接收到了所有的数据的情况
                    if (!this.isConvert) {
                        String srcMessage = webSocketStateBytes.get(hashcode);
                        String[] split = srcMessage.split(";");
                        setDataBase(split);
                    }
                    webSocketStateBytes.remove(hashcode);
                    this.receiveObj.setFileDone(this.done);
                }
            } else {
                /*
                 * 1.如果srcMessage为空说明这是第一次的二进制信息,不做任何处理等待接收完整数据再处理
                 * 2.如果srcMessage不为空说明这是第二次的二进制信息,要转换第一次的二进制信息到此数据中
                 * */
                String srcMessage = webSocketStateBytes.get(hashcode);
                if (srcMessage == null && this.byteBuffer.position() > 4096) {
                    LOGGER.error("第一次的二进制数据超过长度 -> 关闭连接");
                    throw new WebSocketExceptional("第一次的二进制数据超过长度 -> 关闭连接");
                }
                if (srcMessage != null && !this.isConvert) {
                    String[] split = srcMessage.split(";");
                    setDataBase(split);
                }
            }
        }

        private void setDataBase(String[] split) throws Exception {
            this.receiveObj = new ReceiveObj();
            for (String s : split) {
                String[] temp = s.split("=");
                if (temp[0].equals("src") && temp.length > 1) {
                    this.receiveObj.setSrc(temp[1]);
                }
                if (temp[0].equals("tar") && temp.length > 1) {
                    this.receiveObj.setTar(temp[1]);
                }
                if (temp[0].equals("fileName") && temp.length > 1) {
                    this.receiveObj.setFileName(temp[1]);
                }
                if (temp[0].equals("url") && temp.length > 1) {
                    this.receiveObj.setUrl(temp[1]);
                }
                if (temp[0].equals("uuid") && temp.length > 1) {
                    this.receiveObj.setUuid(temp[1]);
                }
            }
            if (this.receiveObj.getSrc().isEmpty() || this.receiveObj.getTar().isEmpty() ||
                    this.receiveObj.getFileName().isEmpty() || this.receiveObj.getUrl().isEmpty()) {
                LOGGER.error("文件内容解释出错:关闭连接");
                throw new WebSocketExceptional("文件内容解释出错 -> 关闭连接");
            }
            this.receiveObj.setFileReceiveConvert(this.byteBuffer);
            this.isConvert = true;
        }

    }

    @Setter
    @Getter
    public static final class ReceiveObj {
        private String uuid;
        private String src;
        private String tar;
        private String url;
        private String fileName;
        private ByteBuffer fileReceiveConvert;
        private boolean fileDone;
        private ChartsContent charts;
    }

    @Setter
    @Getter
    public static final class ChartsContent {
        private String nikeName;
        private String phone;
        private String password;
        private String firstLetter;
        private String tokenSession;
        private Integer currentPage;
        private Integer totalPage;
        private String currentActiveId;
        private String message;
        private List<String> nikeNames;
        private String srcTarUUID;
        private String imgPath;
        private String describes;
    }
}
