package cn.kungreat.boot.enents;

import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.handler.WebSocketChannelInHandler;

@CpdogEvent(index = 5)
public class BaseEvents {

    public static String enentAddFriends(WebSocketChannelInHandler.ReceiveObj receiveObj){
        String rt = "";
        return rt;
    }
}
