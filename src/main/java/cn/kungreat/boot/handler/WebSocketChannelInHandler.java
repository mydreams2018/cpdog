package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelInHandler;
import cn.kungreat.boot.utils.CutoverBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
* 把websocket 的数据 解码出来并且 传入下一个链路
*/
public class WebSocketChannelInHandler implements ChannelInHandler<ByteBuffer, LinkedList<WebSocketChannelInHandler.WebSocketState>> {
    //在前边就关闭了的连接  历史数据清理的问题  通过拿到所有选择器 channel 比较 hashcode todo
    public static final Map<Integer,LinkedList<WebSocketState>> WEBSOCKETSTATETREEMAP = new ConcurrentHashMap<>();
    //二进制 数据时用来做的缓存
    public static final Map<Integer,String> WEBSOCKETSTATEBYTES = new ConcurrentHashMap<>();

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
                webSocketState.setMaskingIndex(0);
                byte[] array = buffer.array();
                webSocketState.setFinish(array[0] < 0);
                if((array[0] & 15) != 0){
                    webSocketState.setType(array[0] & 15);
                }else if(webSocketState.getType() == 999){
                    System.out.println("警告,类型解释失败.关闭连接");
                    socketChannel.close();
                    WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
                    WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
                }else{
                    System.out.println("这是一个延续帧.保持上一次的数据类型不变");
                }
                if (array[1] < 0) {
                    //设置数据长度
                    webSocketState.setDateLength(array,remaining);
                    webSocketState.setMaskingKey(array);
                } else{
                    System.out.println("协议mask标记位不正确关闭连接:");
                    socketChannel.close();
                    WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
                    WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
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
            return WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode());
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
                webSocketState.setStringData(socketChannel);
            }else if(webSocketState.getType()==2){
                webSocketState.setByteData(socketChannel.hashCode(),
                        WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode()),socketChannel);
            }else if(webSocketState.getType() == 8){
                System.out.println("break:");
                WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
                WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
            }
            if(webSocketState.isDone()){
                if(webSocketState.getType() != 8){
                    WEBSOCKETSTATETREEMAP.get(socketChannel.hashCode()).add(new WebSocketState(buffer.capacity()));
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
        e.printStackTrace();
        WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
        WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
        return null;
    }

    @Override
    public Class<ByteBuffer> getInClass() {
        return ByteBuffer.class;
    }

    @Override
    public void clearBuffers(SocketChannel socketChannel) {
        WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
        WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
    }

    static final class WebSocketState {

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
         * stringBuffer  缓存
         */
        private StringBuilder stringBuffer = new StringBuilder();
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
        private String charts;
        /**
         * fileName 传送文件时使用
         */
        private String fileName;
        private Path filePath = null;
        /**
         * isConvert 传送文件时使用 表示数据是否已经转换
         */
        private boolean isConvert;

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

        public StringBuilder getStringBuffer() {
            return stringBuffer;
        }

        /*
         编码转换成字符串 默认用UTF-8
        */
        public void setStringData(SocketChannel socketChannel) throws Exception {
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
                String[] split = stringBuffer.toString().split(";");
                stringBuffer = null;
                for(int x=0;x<split.length;x++){
                    String[] temp = split[x].split("=");
                    if(temp[0].equals("src")){
                        this.src=temp[1];
                    }
                    if(temp[0].equals("tar")){
                        this.tar=temp[1];
                    }
                    if(temp[0].equals("charts")){
                        this.charts=temp[1];
                    }
                }
                if(this.src.isEmpty() || this.tar.isEmpty() || this.charts.isEmpty()){
                    System.out.println("字符内容解释出错:关闭连接");
                    socketChannel.close();
                    WEBSOCKETSTATETREEMAP.remove(socketChannel.hashCode());
                    WEBSOCKETSTATEBYTES.remove(socketChannel.hashCode());
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

        public String getCharts() {
            return charts;
        }

        public void setCharts(String charts) {
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
                if(WEBSOCKETSTATEBYTES.get(hashcode) == null){
                    byteBuffer.flip();
                    int remaining = byteBuffer.remaining();
                    WEBSOCKETSTATEBYTES.put(hashcode,new String(byteBuffer.array(),0,remaining,Charset.forName("UTF-8")));
                    list.removeLast();
                    byteBuffer.clear();
                }else{
                    //第二次 二进制 进来一次就接收到了所有的数据的情况
                    if(!isConvert){
                        String sbu = WEBSOCKETSTATEBYTES.get(hashcode);
                        String[] split = sbu.split(";");
                        for(int x=0;x<split.length;x++){
                            String[] temp = split[x].split("=");
                            if(temp[0].equals("src") && temp.length>1){
                                this.src=temp[1];
                            }
                            if(temp[0].equals("tar") && temp.length>1){
                                this.tar=temp[1];
                            }
                            if(temp[0].equals("fileName") && temp.length>1){
                                this.fileName=temp[1];
                            }
                        }
                        if(this.src!=null && this.src.length()>0
                                && this.tar!=null && this.tar.length()>0
                                && this.fileName!=null && this.fileName.length()>0){
                            try {
                                filePath = Path.of("D:\\kungreat\\IdeaProjects",this.fileName);
                                Files.createFile(filePath);
                                isConvert=true;
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("文件创建出错:关闭连接");
                                socketChannel.close();
                                WEBSOCKETSTATETREEMAP.remove(hashcode);
                                WEBSOCKETSTATEBYTES.remove(hashcode);
                            }
                        }else{
                            System.out.println("文件内容解释出错:关闭连接");
                            socketChannel.close();
                            WEBSOCKETSTATETREEMAP.remove(hashcode);
                            WEBSOCKETSTATEBYTES.remove(hashcode);
                        }
                    }
                    WEBSOCKETSTATEBYTES.remove(hashcode);
                }
            }else{
                String sbu = WEBSOCKETSTATEBYTES.get(hashcode);
                if(sbu==null && !byteBuffer.hasRemaining()){
                    System.out.println("二进制数据解释失败.关闭连接");
                    socketChannel.close();
                    WEBSOCKETSTATETREEMAP.remove(hashcode);
                    WEBSOCKETSTATEBYTES.remove(hashcode);
                    return;
                }
                if(sbu!=null && !isConvert){
                    String[] split = sbu.split(";");
                    for(int x=0;x<split.length;x++){
                        String[] temp = split[x].split("=");
                        if(temp[0].equals("src") && temp.length>1){
                            this.src=temp[1];
                        }
                        if(temp[0].equals("tar") && temp.length>1){
                            this.tar=temp[1];
                        }
                        if(temp[0].equals("fileName") && temp.length>1){
                            this.fileName=temp[1];
                        }
                    }
                    if(this.src!=null && this.src.length()>0
                       && this.tar!=null && this.tar.length()>0
                            && this.fileName!=null && this.fileName.length()>0){
                        try {
                            filePath = Path.of("D:\\kungreat\\IdeaProjects",this.fileName);
                            Files.createFile(filePath);
                            isConvert=true;
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("文件创建出错:关闭连接");
                            socketChannel.close();
                            WEBSOCKETSTATETREEMAP.remove(hashcode);
                            WEBSOCKETSTATEBYTES.remove(hashcode);
                        }
                    }else{
                        System.out.println("文件内容解释出错:关闭连接");
                        socketChannel.close();
                        WEBSOCKETSTATETREEMAP.remove(hashcode);
                        WEBSOCKETSTATEBYTES.remove(hashcode);
                    }
                }
            }
        }

        public long getMaskingIndex() {
            return maskingIndex;
        }

        public void setMaskingIndex(long maskingIndex) {
            this.maskingIndex = maskingIndex;
        }
    }
}
