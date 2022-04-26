package cn.kungreat.boot.services;

import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.jb.MsgDescribe;
import cn.kungreat.boot.jb.QueryResult;
import cn.kungreat.boot.jb.UserDetails;
import cn.kungreat.boot.utils.JdbcUtils;
import cn.kungreat.boot.utils.Paging;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CpdogController(index = 3)
public class ChartsService {
    public static String uploadUserImg(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String nikeName = WebSocketChannelOutHandler.USER_UUIDS.get(job.getSrc());
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("update user_details set img_path=? where nike_name=?")){
            preparedStatement.setString(1,"/images/user/"+job.getFileName());
            preparedStatement.setString(2,nikeName);
            int i = preparedStatement.executeUpdate();
            if(i>0){
                baseResponse.setCode("200");
                baseResponse.setMsg("图片上传成功");
                baseResponse.setUrl("uploadUserImg");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }else{
                baseResponse.setMsg("图片上传失败");
                baseResponse.setUrl("uploadUserImg");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }
            connection.commit();
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }
    //查询聊天视图
    public static String queryChartsViews(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
        if(tokenSession!=null && !tokenSession.isBlank() && tokenNikeName != null){
            try(Connection connection = JdbcUtils.getConnection();
                PreparedStatement preparedCount = connection.prepareStatement("select count(id) from msg_view where user_src =? and show_state=1 and user_tar like ?");
                PreparedStatement preparedStatement = connection.prepareStatement("select msgview.user_tar,msgview.id,msgview.src_tar_uuid,msgview.last_msg_time,msgview.last_msg,usdet.img_path from " +
                        " (select user_tar,id,src_tar_uuid,FROM_UNIXTIME(last_msg_time,'%h:%i') as last_msg_time,last_msg from msg_view where user_src =? and show_state=1 and user_tar like ?) msgview " +
                        " join user_details usdet on msgview.user_tar = usdet.nike_name order by msgview.last_msg_time DESC limit ?,?")){
                WebSocketChannelInHandler.ChartsContent jobCharts = job.getCharts();
                String srcNikName = jobCharts.getNikeName();
                if(srcNikName!=null && !srcNikName.isBlank()){
                    preparedCount.setString(1,tokenNikeName);
                    preparedCount.setString(2,"%"+srcNikName+"%");
                    preparedStatement.setString(1,tokenNikeName);
                    preparedStatement.setString(2,"%"+srcNikName+"%");
                }else{
                    preparedCount.setString(1,tokenNikeName);
                    preparedCount.setString(2,"%");
                    preparedStatement.setString(1,tokenNikeName);
                    preparedStatement.setString(2,"%");
                }
                ResultSet rs1 = preparedCount.executeQuery();
                int nums = 0;
                if(rs1.next()){
                    nums = rs1.getInt(1);
                }
                List<UserDetails> list  = null;
                if(nums > 0){
                    Paging paging = new Paging();
                    list = new ArrayList<>();
                    paging.setCurrentPage(jobCharts.getCurrentPage());
                    preparedStatement.setInt(3, paging.getStart());
                    preparedStatement.setInt(4, paging.getPageSize());
                    ResultSet resultSet = preparedStatement.executeQuery();
                    while (resultSet.next()){
                        UserDetails userDetails = new UserDetails();
                        userDetails.setRegisterTime(resultSet.getString("last_msg_time"));
                        userDetails.setDescribes(resultSet.getString("last_msg"));
                        userDetails.setNikeName(resultSet.getString("user_tar"));
                        userDetails.setImgPath(resultSet.getString("img_path"));
                        userDetails.setId(resultSet.getString("id"));
                        userDetails.setSrcTarUUID(resultSet.getString("src_tar_uuid"));
                        list.add(userDetails);
                    }
                    paging.setData(nums,paging.getPageSize(),paging.getCurrentPage());
                    QueryResult result = new QueryResult();
                    result.setDatas(list);
                    result.setPage(paging);
                    result.setCurrentActiveId(jobCharts.getCurrentActiveId());
                    rt = WebSocketChannelInHandler.MAP_JSON.writeValueAsString(result);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return rt;
    }
    //聊天视图处理
    public static String handlerChartsViews(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String message = job.getCharts().getMessage();
        String nikeName = job.getCharts().getNikeName();
        if(tokenSession!=null && !tokenSession.isBlank() && message!=null && !message.isBlank()
                && nikeName!= null && !nikeName.isBlank()){
            String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
            if(tokenNikeName!=null){
                if(message.equals("hide")){
                    rt=hideChartsViews(nikeName,tokenNikeName);
                }else if(message.equals("show")){
                    rt=showChartsDetails(nikeName,tokenNikeName,job);
                }
            }
        }
        return rt;
    }

    private static String showChartsDetails(String primaryId, String tokenNikeName,WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select id from msg_view where src_tar_uuid=? and user_src=?");
            PreparedStatement count = connection.prepareStatement("select count(id) from msg_describe where src_tar_uuid=?");
            PreparedStatement prepareRt =connection.prepareStatement("select id, src_tar_uuid, data_type, receive_state, send_time, content, file_name, src_user, tar_user from msg_describe "+
                    "  where src_tar_uuid=? order by send_time desc limit ?,?")){
            preparedStatement.setString(1,primaryId);
            preparedStatement.setString(2,tokenNikeName);
            ResultSet resultSet = preparedStatement.executeQuery();
            //根据 必要的规则能查出数据说明 当前用户是数据的所有者
            if(resultSet.next()){
                count.setString(1,primaryId);
                prepareRt.setString(1,primaryId);
                ResultSet rs1 = count.executeQuery();
                int nums = 0;
                if(rs1.next()){
                    nums = rs1.getInt(1);
                }
                List<MsgDescribe> list  = Collections.emptyList();
                if(nums > 0){
                    Paging paging = new Paging();
                    list = new ArrayList<>();
                    paging.setCurrentPage(job.getCharts().getCurrentPage());
                    prepareRt.setInt(2,paging.getStart());
                    prepareRt.setInt(3,paging.getPageSize());
                    ResultSet resultSet2 = prepareRt.executeQuery();
                    while (resultSet2.next()){
                        MsgDescribe msgDescribe = new MsgDescribe();
                        msgDescribe.setId(resultSet2.getString("id"));
                        msgDescribe.setSrcTarUUID(resultSet2.getString("src_tar_uuid"));
                        msgDescribe.setDataType(resultSet2.getInt("data_type"));
                        msgDescribe.setReceiveState(resultSet2.getInt("receive_state"));
                        msgDescribe.setSendTime(resultSet2.getLong("send_time"));
                        msgDescribe.setContent(resultSet2.getString("content"));
                        msgDescribe.setFileName(resultSet2.getString("file_name"));
                        msgDescribe.setSrcUser(resultSet2.getString("src_user"));
                        msgDescribe.setTarUser(resultSet2.getString("tar_user"));
                        list.add(msgDescribe);
                    }
                    paging.setData(nums,paging.getPageSize(),paging.getCurrentPage());
                    QueryResult result = new QueryResult();
                    result.setDatas(list);
                    result.setPage(paging);
                    result.setCurrentActiveId(job.getCharts().getCurrentActiveId());
                    result.setDataId(primaryId);
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(result);
                }else{
                    Paging paging = new Paging();
                    paging.setData(0,paging.getPageSize(),paging.getCurrentPage());
                    QueryResult result = new QueryResult();
                    result.setDatas(list);
                    result.setPage(paging);
                    result.setCurrentActiveId(job.getCharts().getCurrentActiveId());
                    result.setDataId(primaryId);
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(result);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }

    //隐藏用户的聊天视图
    public static String hideChartsViews(String primaryId,String userNik){
        String rt="";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement= connection.prepareStatement("update msg_view set show_state=0 where id=? and user_src=?")){
            preparedStatement.setString(1,primaryId);
            preparedStatement.setString(2,userNik);
            preparedStatement.executeUpdate();
            connection.commit();
            baseResponse.setCode("200");
            baseResponse.setUrl("handlerChartsViews");
            baseResponse.setMsg("hide");
            baseResponse.setUuid(primaryId);
            rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }
    //给指定的用户发送聊天信息
    public static String handlerChartsSend(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String message = job.getCharts().getMessage();
        String nikeName = job.getCharts().getNikeName();
        String srcTarUUID = job.getCharts().getSrcTarUUID();
        if(tokenSession!=null && !tokenSession.isBlank() && message!=null && !message.isBlank()
                && nikeName!= null && !nikeName.isBlank() && srcTarUUID!=null && !srcTarUUID.isBlank()){
            String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
            if(tokenNikeName!=null){
                final BaseResponse baseResponse = new BaseResponse();
                if(!isHasFriend(tokenNikeName,nikeName)){
                    baseResponse.setUrl("handlerChartsSend");
                    baseResponse.setMsg("发送失败已经不是好友关系了:"+nikeName);
                    try {
                        rt = WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return rt;
                }
                try(Connection connection = JdbcUtils.getConnection();
                    PreparedStatement querypreparedUpdate = connection.prepareStatement("select id, user_src, user_tar, show_state, src_tar_uuid, last_msg_time, last_msg from msg_view where user_src=? and user_tar=? and src_tar_uuid=?"
                                ,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
                    PreparedStatement preparedUpdate = connection.prepareStatement("update msg_view set last_msg=?,last_msg_time=unix_timestamp(now()) where user_src=? and user_tar=? and src_tar_uuid=?");
                    PreparedStatement insert = connection.prepareStatement(" insert into msg_describe(id, src_tar_uuid, data_type, receive_state, send_time, content, file_name, src_user, tar_user ) " +
                            " values (UUID(),?,?,0,unix_timestamp(now()),?,?,?,?)")){
                    if(message.length()>82){
                        preparedUpdate.setString(1,message.substring(0,82));
                    }else{
                        preparedUpdate.setString(1,message);
                    }
                    querypreparedUpdate.setString(1,nikeName);
                    querypreparedUpdate.setString(2,tokenNikeName);
                    querypreparedUpdate.setString(3,srcTarUUID);
                    ResultSet resultSetTar = querypreparedUpdate.executeQuery();
                    if(resultSetTar.next()){
                        resultSetTar.updateLong("last_msg_time",System.currentTimeMillis()/1000);
                        resultSetTar.updateInt("show_state",1);
                        if(message.length()>82){
                            resultSetTar.updateString("last_msg",message.substring(0,82));
                        }else{
                            resultSetTar.updateString("last_msg",message);
                        }
                        resultSetTar.updateRow();
                    }else{
                        resultSetTar.moveToInsertRow();
                        resultSetTar.updateString("user_src",nikeName);
                        resultSetTar.updateString("user_tar",tokenNikeName);
                        resultSetTar.updateString("src_tar_uuid",srcTarUUID);
                        resultSetTar.updateLong("last_msg_time",System.currentTimeMillis()/1000);
                        resultSetTar.updateString("id",srcTarUUID);
                        if(message.length()>82){
                            resultSetTar.updateString("last_msg",message.substring(0,82));
                        }else{
                            resultSetTar.updateString("last_msg",message);
                        }
                        resultSetTar.insertRow();
                    }
                    preparedUpdate.setString(2,tokenNikeName);
                    preparedUpdate.setString(3,nikeName);
                    preparedUpdate.setString(4,srcTarUUID);
                    int num = preparedUpdate.executeUpdate();
                    if(num > 0){
                        insert.setString(1,srcTarUUID);
                        insert.setInt(2,1);
                        insert.setString(3,message);
                        insert.setString(4,"");
                        insert.setString(5,tokenNikeName);
                        insert.setString(6,nikeName);
                        int i = insert.executeUpdate();
                        if(i>0){
                            baseResponse.setCode("200");
                            baseResponse.setUrl("handlerChartsSend");
                            baseResponse.setMsg(message);
                            baseResponse.setUuid(job.getUuid());
                            baseResponse.setSrcTarUUID(srcTarUUID);
                            rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                            connection.commit();
                        }else{
                            baseResponse.setUrl("handlerChartsSend");
                            baseResponse.setMsg("发送聊天信息失败");
                            baseResponse.setUuid(job.getUuid());
                            baseResponse.setSrcTarUUID(srcTarUUID);
                            rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                            connection.rollback();
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        return rt;
    }
    //查询是否好友关系
    public static boolean isHasFriend(String src,String tar){
        boolean is = false;
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select src_user_id from friends_history where src_user_id=? and tar_user_id=? and cur_state=1")){
            preparedStatement.setString(1,src);
            preparedStatement.setString(2,tar);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next()){
                is = true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return is;
    }
    //修改用户的描述
    public static String handlerDesUpdate(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String nikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
        String message = job.getCharts().getMessage();
        if(nikeName !=null && !nikeName.isBlank() && message!=null && !message.isBlank()){
            final BaseResponse baseResponse = new BaseResponse();
            try(Connection connection = JdbcUtils.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement("update user_details set describes=? where nike_name=?")){
                preparedStatement.setString(1,message);
                preparedStatement.setString(2,nikeName);
                int i = preparedStatement.executeUpdate();
                if(i>0){
                    baseResponse.setCode("200");
                    baseResponse.setMsg("修改备注成功");
                    baseResponse.setUrl("handlerDesUpdate");
                    connection.commit();
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                }else{
                    baseResponse.setMsg("修改备注失败");
                    baseResponse.setUrl("handlerDesUpdate");
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return rt;
    }

}
