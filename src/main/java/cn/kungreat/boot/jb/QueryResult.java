package cn.kungreat.boot.jb;

import cn.kungreat.boot.utils.Paging;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class QueryResult {
    private Paging page;
    private List datas;
}
