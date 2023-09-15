package cn.kungreat.boot.enents;

import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.handler.WebSocketConvertData;
import cn.kungreat.boot.jb.EventBean;

@CpdogEvent(index = 5)
public class BaseEvents {

    public static String eventAddFriends(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String eventDeleteCurFriend(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String eventChartSendMsg(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String eventApplyFriend(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketConvertData.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }
}
