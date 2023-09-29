package cn.kungreat.boot.enents;

import cn.kungreat.boot.GlobalEventListener;
import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.handler.WebSocketConvertData;

@CpdogEvent(index = 5)
public class BaseEvents {

    public static String eventAddFriends(GlobalEventListener.EventBean receiveObj) throws Exception {
        String rt = "";
        rt = WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String eventDeleteCurFriend(GlobalEventListener.EventBean receiveObj) throws Exception {
        String rt = "";
        rt = WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String eventChartSendMsg(GlobalEventListener.EventBean receiveObj) throws Exception {
        String rt = "";
        rt = WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String eventApplyFriend(GlobalEventListener.EventBean receiveObj) throws Exception {
        String rt = "";
        rt = WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }
}
