package cn.kungreat.boot.jb;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EventBean extends UserDetails{
    private String uuid;
    private String src;
    private String tar;
    private String url;
}
