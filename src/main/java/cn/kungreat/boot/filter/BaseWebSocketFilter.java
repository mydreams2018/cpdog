package cn.kungreat.boot.filter;

import cn.kungreat.boot.FilterInHandler;
import cn.kungreat.boot.handler.WebSocketConvertData;

public class BaseWebSocketFilter implements FilterInHandler {
    @Override
    public void init(WebSocketConvertData.WebSocketData webSocketData) {

    }

    @Override
    public boolean filter(WebSocketConvertData.WebSocketData webSocketData) {
        return true;
    }

    @Override
    public void exception(Exception e, WebSocketConvertData.WebSocketData webSocketData) {

    }
}
