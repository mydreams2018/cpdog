package cn.kungreat.boot.jb;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class WebSocketBean {

    private String title;
    private String host;
    private String upgrade;
    private String connection;
    private String secWebSocketKey;
    private String secWebSocketVersion;
    private String secWebSocketProtocol;
    private String userAgent;
}
