package cn.kungreat.boot.jb;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MsgDescribe {
    private String id;
    private String srcTarUUID;
    private Integer dataType;
    private Integer receiveState;
    private long sendTime;
    private String content;
    private String fileName;
    private String srcUser;
    private String tarUser;
}
