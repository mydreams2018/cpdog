package cn.kungreat.boot.enents;

import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.jb.EventBean;
import com.fasterxml.jackson.core.JsonProcessingException;

@CpdogEvent(index = 5)
public class BaseEvents {

    public static String enentAddFriends(EventBean receiveObj){
        String rt = "";
        try {
            rt= WebSocketChannelInHandler.MAP_JSON.writeValueAsString(receiveObj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return rt;
    }
}
