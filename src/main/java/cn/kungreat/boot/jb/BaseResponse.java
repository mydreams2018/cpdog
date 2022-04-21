package cn.kungreat.boot.jb;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BaseResponse {
    private String uuid;
    private String sktoken;
    private String user;
    private String url;
    private String code="100";
    private String msg="未来错误";
    private String imgPath;
    private String srcTarUUID;
}
