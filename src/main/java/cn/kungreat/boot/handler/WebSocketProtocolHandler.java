package cn.kungreat.boot.handler;

import cn.kungreat.boot.ChannelProtocolHandler;
import cn.kungreat.boot.jb.WebSocketBean;
import cn.kungreat.boot.tls.CpDogSSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class WebSocketProtocolHandler implements ChannelProtocolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketProtocolHandler.class);
    /*  websocket 固定拼接的字符串  */
    private static final String MAGICSTRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    @Override
    public boolean handlers(SocketChannel socketChannel, ByteBuffer in) throws Exception {
        boolean rt = false;
        if (in.position()>0){
            List<String> lis = new ArrayList<>(32);
            byte[] bytes = in.array();
            int beforeIndex = 0;
            int afterIndex ;
            for(int x=0;x<in.position();x++){
                if(bytes[x]==10 || bytes[x]== 13){
                    afterIndex = x;
                    if(afterIndex-beforeIndex > 0){
                        lis.add(new String(bytes,beforeIndex,(afterIndex-beforeIndex)));
                    }
                    beforeIndex= x+1;
                }
            }
            WebSocketBean webSocketBean = getWebSocketBean(lis);
            if(webSocketBean.getSecWebSocketKey()!=null && !webSocketBean.getSecWebSocketKey().isEmpty()){
                String secWebSocketKey = getSecWebSocketKey(webSocketBean.getSecWebSocketKey());
                writeProtocol(secWebSocketKey,socketChannel,in);
                in.clear();
                rt = true;
            }else if(!in.hasRemaining()){
                socketChannel.close();
                LOGGER.error("WebSocketProtocolHandler:数据读满了.但是没有解释出协议");
            }
        }else{
            socketChannel.close();
            LOGGER.error("WebSocketProtocolHandler:没有可读取字节");
        }
        return rt;
    }

    private WebSocketBean getWebSocketBean(List<String> lis){
        WebSocketBean webSocketBean = new WebSocketBean();
        for(int x=0;x<lis.size();x++){
            String tempKV = lis.get(x);
            if(x==0){
                webSocketBean.setTitle(tempKV);
            }else{
                String[] splits = tempKV.split(":");
                if(splits[0].trim().equals("Sec-WebSocket-Key")){
                    webSocketBean.setSecWebSocketKey(splits[1].trim());
                }
                if(splits[0].trim().equals("Connection")){
                    webSocketBean.setConnection(splits[1].trim());
                }
                if(splits[0].trim().equals("Sec-WebSocket-Version")){
                    webSocketBean.setSecWebSocketVersion(splits[1].trim());
                }
                if(splits[0].trim().equals("Upgrade")){
                    webSocketBean.setUpgrade(splits[1].trim());
                }
            }
        }
        return webSocketBean;
    }

    private String getSecWebSocketKey(String src) throws Exception {
        String rt = src+MAGICSTRING;
        MessageDigest instance = MessageDigest.getInstance("SHA");
        instance.update(rt.getBytes(StandardCharsets.UTF_8));
        byte[] digest = instance.digest();
        return Base64.getEncoder().encodeToString(digest);
    }

    private void writeProtocol(String secWebSocketKey,SocketChannel socketChannel,ByteBuffer in) throws Exception {
        StringBuilder stringBuilder = new StringBuilder(256);
        stringBuilder.append("HTTP/1.1 101 Switching Protocols");
        stringBuilder.append(System.lineSeparator());
        stringBuilder.append("Upgrade: websocket");
        stringBuilder.append(System.lineSeparator());
        stringBuilder.append("Connection: Upgrade");
        stringBuilder.append(System.lineSeparator());
        stringBuilder.append("Sec-WebSocket-Accept: "+secWebSocketKey);
        stringBuilder.append(System.lineSeparator());
        stringBuilder.append(System.lineSeparator());
        in.clear();
        in.put(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
        in.flip();
        CpDogSSLContext.outEncode(socketChannel,in);
    }
}
