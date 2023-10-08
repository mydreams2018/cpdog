package cn.kungreat.boot.filter;

import cn.kungreat.boot.CpdogMain;
import cn.kungreat.boot.FilterInHandler;
import cn.kungreat.boot.GlobalEventListener;
import cn.kungreat.boot.handler.WebSocketConvertData;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseWebSocketFilter implements FilterInHandler {

    /*
     *   存放用户登录随机生成的UUID 关联信息.UUID在前端是session级别的. [k=uuid v=userid]
     */
    public static final Map<String, String> USER_UUIDS = new ConcurrentHashMap<>(1024);

    @Override
    public void init(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) throws Exception {
        //页面刷新就会重建socket连接,用此来记录用户的channel信息,需要前端配合处理,在登录完成时返回token让前端保存
        if (webSocketData.getReceiveObj().getUrl().equals(CpdogMain.REFRESH_TOKEN_URL)) {
            String nikeName = USER_UUIDS.get(webSocketData.getReceiveObj().getCharts().getTokenSession());
            if (nikeName != null) {
                GlobalEventListener.CONCURRENT_EVENT_MAP.put(nikeName, socketChannel);
            }
        }
    }

    @Override
    public boolean filter(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) throws Exception {
        return true;
    }

    @Override
    public void exception(Exception e, WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) {

    }
}
