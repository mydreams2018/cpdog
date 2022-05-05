package cn.kungreat.boot.enents;

import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.jb.EventBean;

@CpdogEvent(index = 5)
public class BaseEvents {

    public static String enentAddFriends(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketChannelInHandler.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String enentDeleteCurFriend(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketChannelInHandler.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String enentChartSendMsg(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketChannelInHandler.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }

    public static String enentApplyFriend(EventBean receiveObj) throws Exception{
        String rt = "";
        rt= WebSocketChannelInHandler.MAP_JSON.writeValueAsString(receiveObj);
        return rt;
    }
}
