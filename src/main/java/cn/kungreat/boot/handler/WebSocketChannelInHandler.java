package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.utils.CutoverBytes;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/*
* 把websocket 的数据 解码出来并且 传入下一个链路
*/
public class WebSocketChannelInHandler implements ChannelInHandler<ByteBuffer, LinkedList<WebSocketChannelInHandler.WebSocketState>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketChannelInHandler.class);
    //每次发生的完整的事件对象
    public static final Map<Integer,LinkedList<WebSocketState>> WEB_SOCKET_STATE_TREEMAP = new ConcurrentHashMap<>(1024);
    //二进制 数据时用来做的缓存
    public static final Map<Integer,String> WEB_SOCKET_STATE_BYTES = new ConcurrentHashMap<>(256);

    public static String FILE_PATH;
    public static final ObjectMapper MAP_JSON = new ObjectMapper(); //create once, reuse
    // 用来存放复用的 WebSocketState
    public static final LinkedBlockingQueue<WebSocketState> REUSE_WEB_STATE = new LinkedBlockingQueue<>(5120);
    @Override
    public void before(SocketChannel socketChannel,ByteBuffer buffer) throws Exception {
        buffer.flip();
        LinkedList<WebSocketState> listSos = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        if (listSos == null) {
            listSos = new LinkedList<>();
            listSos.add(reuserWebSocketState(buffer.capacity()));
            WEB_SOCKET_STATE_TREEMAP.put(socketChannel.hashCode(), listSos);
        }
        WebSocketState webSocketState = listSos.getLast();
        long remainingTotal = webSocketState.getDateLength() - webSocketState.getReadLength();
        if (remainingTotal == 0) {
            int remaining = buffer.remaining();
            //最少需要6个字节才能解释 协议
            if (remaining > 6) {
                //需要初始化这个
                webSocketState.setCurrentPos(0);
                webSocketState.setMaskingIndex(0);
                byte[] array = buffer.array();
                webSocketState.setFinish(array[0] < 0);
                if((array[0] & 15) != 0){
                    webSocketState.setType(array[0] & 15);
                }else if(webSocketState.getType() == 999){
                    LOGGER.error("警告,类型解释失败.关闭连接");
                    socketChannel.close();
                }else{
                    LOGGER.info("这是一个延续帧.保持上一次的数据类型不变");
                }
                if (array[1] < 0) {
                    //设置数据长度
                    webSocketState.setDateLength(array,remaining);
                    webSocketState.setMaskingKey(array);
                } else{
                    LOGGER.info("协议mask标记位不正确关闭连接:");
                    socketChannel.close();
                }
            }
        }
    }

    @Override
    public LinkedList<WebSocketState> handler(SocketChannel socketChannel, ByteBuffer buffer) throws Exception {
        if(socketChannel.isOpen()){
            WebSocketState webSocketState = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode()).getLast();
            int currentPos = webSocketState.getCurrentPos();
            int remaining = buffer.remaining();
            long remainingTotal = webSocketState.getDateLength()-webSocketState.getReadLength();
            //拿到当前指针的位置开始取真实的数据
            if (currentPos < remaining && remainingTotal > 0) {
                byte[] array = buffer.array();
                //拿到必需要的数据 还有额外的数据读.......
                int maxReadLength = remaining - currentPos;
                ByteBuffer tarBuffer = webSocketState.getByteBuffer();
                //必要的时候需要扩容.TLS
                if(tarBuffer.remaining() < maxReadLength){
                    ByteBuffer newTarBuffer = ByteBuffer.allocate(maxReadLength);
                    tarBuffer.flip();
                    newTarBuffer.put(tarBuffer);
                    tarBuffer = newTarBuffer;
                    webSocketState.setByteBuffer(tarBuffer);
                }
                long runLength = Math.min(maxReadLength, remainingTotal);
                //当前已经读取的长度索引
                long readLength = webSocketState.getReadLength();
                long maskingIndex = webSocketState.getMaskingIndex();
                byte[] maskingKey = webSocketState.getMaskingKey();
                for (int x = 0; x < runLength; x++) {
                    byte tar = (byte) (array[currentPos] ^ maskingKey[Math.floorMod(maskingIndex, 4)]);
                    tarBuffer.put(tar);
                    currentPos++;
                    readLength++;
                    maskingIndex++;
                }
                webSocketState.setReadLength(readLength);
                webSocketState.setMaskingIndex(maskingIndex);
                buffer.position(currentPos);
                webSocketState.setCurrentPos(0);
            }
            buffer.compact();
            return WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode());
        }
        return null;
    }

    @Override
    public void after(SocketChannel socketChannel, ByteBuffer buffer) throws Exception {
        if(socketChannel.isOpen()){
            WebSocketState webSocketState = WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode()).getLast();
            if(webSocketState.isFinish() && webSocketState.getReadLength()==webSocketState.getDateLength()){
                webSocketState.setDone(true);
            }
            if(webSocketState.getType()==1){
                webSocketState.setStringData(socketChannel);
            }else if(webSocketState.getType()==2){
                webSocketState.setByteData(socketChannel.hashCode(),
                        WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode()),socketChannel);
            }else if(webSocketState.getType() == 8){
                LOGGER.info("break:");
            }
            if(webSocketState.isDone()){
                if(webSocketState.getType() != 8){
                    WEB_SOCKET_STATE_TREEMAP.get(socketChannel.hashCode()).add(reuserWebSocketState(buffer.capacity()));
                    loopData(socketChannel,buffer);
                }
            }
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
    public ByteBuffer exception(Exception e, SocketChannel socketChannel, Object in) throws Exception {
        socketChannel.close();
        LOGGER.error(e.getLocalizedMessage());
        return null;
    }

    @Override
    public Class<ByteBuffer> getInClass() {
        return ByteBuffer.class;
    }

    @Override
    public void clearBuffers(SocketChannel socketChannel) {
        WEB_SOCKET_STATE_TREEMAP.remove(socketChannel.hashCode());
        WEB_SOCKET_STATE_BYTES.remove(socketChannel.hashCode());
    }

    public WebSocketState reuserWebSocketState(int capacity){
        WebSocketState webSocketState = REUSE_WEB_STATE.poll();
        if(webSocketState != null){
            return webSocketState;
        }
        return new WebSocketState(capacity);
    }

   public static final class WebSocketState {

        private WebSocketState(int length) {
            byteBuffer = ByteBuffer.allocate(length);
            this.type=999;
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
         * StringBuilder  缓存
         */
        private StringBuilder stringBuilder = new StringBuilder();
        /**
         * type  websocket 数据类型标识
         * 0 continuation frame
         * 1: 文本数据
         * 2: byte数据
         * 8: break 关闭的信号
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
         * maskingIndex  websocket 客户端掩码索引
         */
        private long maskingIndex;
        /**
         * currentPos websocket 当前byte指针位置
         */
        private int currentPos;

        private ByteBuffer byteBuffer;
        private String src;
        private String tar;
        private ChartsContent charts;
        private String url;
        /**
         * uuid 每条数据的唯一标识
         */
        private String uuid;
        /**
         * fileName 传送文件时使用
         */
        private String fileName;
        private Path filePath = null;
        /**
         * isConvert 传送文件时使用 表示数据是否已经转换
         */
        private boolean isConvert;
        private CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();
        private CharBuffer charBuffer = CharBuffer.allocate(1024);
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
                    byte[] temp = {(byte)(dateLength[2]&127), dateLength[3], dateLength[4], dateLength[5],
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


        /*
         编码转换成字符串 默认用UTF-8 charsetDecoder  charBuffer
        */
        public void setStringData(SocketChannel socketChannel) throws Exception {
            byteBuffer.flip();
            int remaining = byteBuffer.remaining();
            if(!done){
                CoderResult coderResult;
                do{
                    coderResult = charsetDecoder.decode(byteBuffer, charBuffer, false);
                    charBuffer.flip();
                    stringBuilder.append(charBuffer);
                    charBuffer.clear();
                }while (coderResult.isOverflow());
                if(coderResult.isError()){
                    throw new RuntimeException("!!!解码错误-111!!!");
                }
                byteBuffer.compact();
            }else{
                stringBuilder.append(new String(byteBuffer.array(),0,remaining, StandardCharsets.UTF_8));
                byteBuffer.clear();
                //接收字符串本次完成了.解释成对象
                ReceiveObj receiveObj = MAP_JSON.readValue(stringBuilder.toString(),ReceiveObj.class);
                stringBuilder = null;
                if(receiveObj == null){
                    LOGGER.error("字符内容解释出错:关闭连接");
                    socketChannel.close();
                    return;
                }else{
                    this.src=receiveObj.getSrc();
                    this.tar=receiveObj.getTar();
                    this.charts=receiveObj.getCharts();
                    this.url=receiveObj.getUrl();
                    this.uuid=receiveObj.getUuid();
                }
                if(this.src.isEmpty() || this.tar.isEmpty() || this.charts == null || this.url.isEmpty() || this.uuid.isEmpty()){
                    LOGGER.error("字符内容解释出错:关闭连接");
                    socketChannel.close();
                }
            }
        }

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }

        public String getTar() {
            return tar;
        }

        public void setTar(String tar) {
            this.tar = tar;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public ChartsContent getCharts() {
            return charts;
        }

        public void setCharts(ChartsContent charts) {
            this.charts = charts;
        }

        public Path getFilePath() {
            return filePath;
        }

        public void setFilePath(Path filePath) {
            this.filePath = filePath;
        }

        //二进制数据时做的数据处理. 由于是分二次发送所以第一次的二进制数据是 相关的源信息
        public void setByteData(int hashcode,LinkedList<WebSocketState> list,SocketChannel socketChannel) throws IOException {
            if(done){
                if(WEB_SOCKET_STATE_BYTES.get(hashcode) == null){
                    byteBuffer.flip();
                    int remaining = byteBuffer.remaining();
                    WEB_SOCKET_STATE_BYTES.put(hashcode,new String(byteBuffer.array(),0,remaining, StandardCharsets.UTF_8));
                    list.removeLast();
                    byteBuffer.clear();
                }else{
                    //第二次 二进制 进来一次就接收到了所有的数据的情况
                    if(!isConvert){
                        String sbu = WEB_SOCKET_STATE_BYTES.get(hashcode);
                        String[] split = sbu.split(";");
                        setDataBase(split,hashcode,socketChannel);
                    }
                    WEB_SOCKET_STATE_BYTES.remove(hashcode);
                }
            }else{
                String sbu = WEB_SOCKET_STATE_BYTES.get(hashcode);
                if(sbu==null && !byteBuffer.hasRemaining()){
                    LOGGER.error("二进制数据解释失败.关闭连接");
                    socketChannel.close();
                    return;
                }
                if(sbu!=null && !isConvert){
                    String[] split = sbu.split(";");
                    setDataBase(split,hashcode,socketChannel);
                }
            }
        }

        private void setDataBase(String[] split,int hashcode,SocketChannel socketChannel)throws IOException {
            for (String s : split) {
                String[] temp = s.split("=");
                if (temp[0].equals("src") && temp.length > 1) {
                    this.src = temp[1];
                }
                if (temp[0].equals("tar") && temp.length > 1) {
                    this.tar = temp[1];
                }
                if (temp[0].equals("fileName") && temp.length > 1) {
                    this.fileName = temp[1];
                }
                if (temp[0].equals("url") && temp.length > 1) {
                    this.url = temp[1];
                }
                if (temp[0].equals("uuid") && temp.length > 1) {
                    this.uuid = temp[1];
                }
            }
            if(this.src!=null && this.src.length()>0
                    && this.tar!=null && this.tar.length()>0
                    && this.fileName!=null && this.fileName.length()>0
                    && this.url!=null && this.url.length()>0
                    && this.uuid!=null && this.uuid.length()>0 ){
                try {
                    filePath = Path.of(WebSocketChannelInHandler.FILE_PATH,this.fileName);
                    Files.createFile(filePath);
                    isConvert=true;
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error("文件创建出错:关闭连接");
                    socketChannel.close();
                }
            }else{
                LOGGER.error("文件内容解释出错:关闭连接");
                socketChannel.close();
            }
        }

        public long getMaskingIndex() {
            return maskingIndex;
        }

        public void setMaskingIndex(long maskingIndex) {
            this.maskingIndex = maskingIndex;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isConvert() {
            return isConvert;
        }

        public void setConvert(boolean convert) {
            isConvert = convert;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }
//    初始化操作.复用此对象
        public void clear(){
            this.type=999;
            this.finish=false;
            this.done=false;
            this.stringBuilder=new StringBuilder();
            this.dateLength=0;
            this.readLength=0;
            this.maskingIndex=0;
            this.currentPos=0;
            if(this.byteBuffer!=null){
                this.byteBuffer.clear();
            }
            this.src=null;
            this.tar=null;
            this.charts=null;
            this.url=null;
            this.uuid=null;
            this.fileName=null;
            this.filePath=null;
            this.isConvert=false;
            this.charBuffer.clear();
        }
    }
    @Setter
    @Getter
    public static final class ReceiveObj{
        private String uuid;
        private String src;
        private String tar;
        private String url;
        private ChartsContent charts;
    }
    @Setter
    @Getter
    public static final class ChartsContent{
        private String nikeName;
        private String phone;
        private String password;
        private String firstLetter;
        private String tokenSession;
        private Integer currentPage;
        private Integer totalPage;
        private String currentActiveId;
        private String message;
        private List<String> nikeNamels;
        private String srcTarUUID;
        private String imgPath;
        private String describes;
    }
}