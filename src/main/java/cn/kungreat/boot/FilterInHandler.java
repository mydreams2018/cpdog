package cn.kungreat.boot;

import cn.kungreat.boot.handler.WebSocketConvertData;

import java.nio.channels.SocketChannel;

public interface FilterInHandler {
    /*
     *@Description 初始时调用
     *@Param webSocketData 当前websocket一次性的数据
     *@Date 2023/09/15
     */
    void init(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) throws Exception;

    /*
     *@Description 过滤链路
     *@Param webSocketData 当前websocket一次性的数据
     *@Return true 表示放行此次请求 false 表示放弃此次请求
     *@Date 2023/09/15
     */
    boolean filter(WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel) throws Exception;

    /*
     *@Description 出现异常时会调用此方法
     *@Param e 异常信息 webSocketData 当前websocket一次性的数据
     *@Date 2023/09/15
     */
    void exception(Exception e, WebSocketConvertData.WebSocketData webSocketData, SocketChannel socketChannel);
}
