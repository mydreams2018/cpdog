package cn.kungreat.boot.utils;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.jb.MsgDescribe;
import cn.kungreat.boot.jb.QueryResult;
import cn.kungreat.boot.jb.UserDetails;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.sql.*;
import java.util.*;

public class JdbcTemplate {

    public static String register(WebSocketChannelInHandler.WebSocketState job){
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select phone,nike_name from user_details where phone=? or nike_name=?")){
            WebSocketChannelInHandler.ChartsContent jobCharts = job.getCharts();
            if(jobCharts.getPhone().isBlank() || jobCharts.getNikeName().isBlank()
                    || jobCharts.getPassword().isBlank() || jobCharts.getFirstLetter().isBlank()){
                baseResponse.setUuid(job.getUuid());
                baseResponse.setMsg("必要数据为空");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }else{
                preparedStatement.setString(1,jobCharts.getPhone());
                preparedStatement.setString(2,jobCharts.getNikeName());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()){
                    //数据已经存在了
                    baseResponse.setUuid(job.getUuid());
                    baseResponse.setMsg("用户已经存在了");
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                }else{
                    PreparedStatement insert = connection.prepareStatement("insert into user_details (phone,nike_name,password,id,register_time,sort_first) values (?,?,?,?,CURDATE(),?)");
                    insert.setString(1,jobCharts.getPhone());
                    insert.setString(2,jobCharts.getNikeName());
                    insert.setString(3,jobCharts.getPassword());
                    insert.setString(4,job.getUuid());
                    insert.setString(5,jobCharts.getFirstLetter());
                    int is = insert.executeUpdate();
                    if(is>0){
                        baseResponse.setUuid(job.getUuid());
                        baseResponse.setMsg("用户注册成功");
                        baseResponse.setCode("200");
                        rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                    }else{
                        baseResponse.setUuid(job.getUuid());
                        baseResponse.setMsg("未知错误");
                        baseResponse.setCode("000");
                        rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                    }
                    connection.commit();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }

    public static String login(WebSocketChannelInHandler.WebSocketState job){
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select describes,phone,nike_name,img_path from user_details where phone=? and password=?")){
            WebSocketChannelInHandler.ChartsContent jobCharts = job.getCharts();
            if(jobCharts.getPhone().isBlank() || jobCharts.getPassword().isBlank()){
                baseResponse.setUuid(job.getUuid());
                baseResponse.setMsg("必要数据为空");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }else{
                preparedStatement.setString(1,jobCharts.getPhone());
                preparedStatement.setString(2,jobCharts.getPassword());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()){
                    baseResponse.setUuid(job.getUuid());
                    baseResponse.setMsg("用户认证成功");
                    baseResponse.setCode("200");
                    baseResponse.setDescribes(resultSet.getString("describes"));
                    baseResponse.setUser(resultSet.getString("nike_name"));
                    baseResponse.setSktoken(UUID.randomUUID().toString());
                    baseResponse.setImgPath(resultSet.getString("img_path"));
                    WebSocketChannelOutHandler.USER_UUIDS.put(baseResponse.getSktoken(),resultSet.getString("nike_name"));
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                }else{
                    //验证失败
                    baseResponse.setUuid(job.getUuid());
                    baseResponse.setMsg("用户或密码错误");
                    rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }

    public static String queryUsers(WebSocketChannelInHandler.WebSocketState job){
        String rt = "";
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedCount = connection.prepareStatement("select count(id) from user_details where nike_name LIKE ?");
            PreparedStatement preparedStatement = connection.prepareStatement("select register_time,describes,nike_name,img_path,sort_first from user_details " +
                                                                    "where nike_name LIKE ? order by sort_first limit ?,? ")){
            WebSocketChannelInHandler.ChartsContent jobCharts = job.getCharts();
            String nikName = jobCharts.getNikeName();
            if(nikName != null && !nikName.isBlank()){
                preparedCount.setString(1, "%"+nikName+"%");
                preparedStatement.setString(1, "%"+nikName+"%");
            }else{
                preparedCount.setString(1, "%");
                preparedStatement.setString(1, "%");
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
                preparedStatement.setInt(2, paging.getStart());
                preparedStatement.setInt(3, paging.getPageSize());
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()){
                    UserDetails userDetails = new UserDetails();
                    userDetails.setRegisterTime(resultSet.getString("register_time"));
                    userDetails.setDescribes(resultSet.getString("describes"));
                    userDetails.setNikeName(resultSet.getString("nike_name"));
                    userDetails.setImgPath(resultSet.getString("img_path"));
                    userDetails.setSortFirst(resultSet.getString("sort_first"));
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
        return rt;
    }

    public static String applyFriends(WebSocketChannelInHandler.WebSocketState first) {
        String rt = "";
        List<String> nikeNamels = first.getCharts().getNikeNamels();
        String tokenSession = first.getCharts().getTokenSession();
        if(nikeNamels != null && nikeNamels.size()>0 && tokenSession != null){
            final BaseResponse baseResponse = new BaseResponse();
            String nikeNm = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
            if(nikeNm != null){
                String message = first.getCharts().getMessage();
                StringBuilder nks = new StringBuilder();
                for (int x=0;x< nikeNamels.size();x++) {
                    if(x==nikeNamels.size()-1){
                        nks.append("?");
                        nks.append(")");
                    }else{
                        nks.append("?");
                        nks.append(",");
                    }
                }
                try(Connection connection = JdbcUtils.getConnection();
                    PreparedStatement statement = connection.prepareStatement("insert into apply_history (src_user_id, tar_user_id,apply_msg,apply_time) values (?,?,?,CURDATE())");
                    PreparedStatement preparedStatement = connection.prepareStatement("select tar_user_id from friends_history where src_user_id = ? and cur_state = 1 and tar_user_id in ("+nks)){
                    clearApplyFriends(nikeNm,nikeNamels,preparedStatement);
                    for(int x=0;x<nikeNamels.size();x++){
                        statement.setString(1,nikeNm);
                        statement.setString(2,nikeNamels.get(x));
                        statement.setString(3,message);
                        statement.addBatch();
                    }
                    if(nikeNamels.size()>0){
                        int[] ints = statement.executeBatch();
                        statement.clearBatch();
                        baseResponse.setCode("200");
                        baseResponse.setMsg("申请添加好友成功.共"+ints.length+"个人");
                        baseResponse.setUrl("applyFriends");
                        rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                        connection.commit();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        return rt;
    }
//查询当前的好友. 过滤掉重复的申请
    private static void clearApplyFriends(String nikeName,List<String> srcs,PreparedStatement preparedStatement) throws SQLException {
        List<String> rts = new ArrayList<>();
        preparedStatement.setString(1,nikeName);
        for (int i = 0; i < srcs.size(); i++) {
            preparedStatement.setString(i+2,srcs.get(i));
        }
        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()){
            rts.add(resultSet.getString("tar_user_id"));
        }
        if(rts.size() > 0){
            Iterator<String> stringIterator = srcs.iterator();
            while(stringIterator.hasNext()) {
                String temp = stringIterator.next();
                if(rts.contains(temp)){
                    stringIterator.remove();
                }
            }
        }
    }

    public static String queryUsersFriends(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
        if(tokenSession!=null && !tokenSession.isBlank() && tokenNikeName !=null){
            try(Connection connection = JdbcUtils.getConnection();
                PreparedStatement preparedCount = connection.prepareStatement("select count(src_user_id) from friends_history where src_user_id= ? and tar_user_id LIKE ? and cur_state=1");
                PreparedStatement preparedStatement = connection.prepareStatement("select nike_name,register_time,describes,img_path,sort_first from user_details where nike_name in "+
                        "(select tar_user_id from friends_history where src_user_id= ? and tar_user_id LIKE ? and cur_state=1) order by sort_first limit ?,?")){
                WebSocketChannelInHandler.ChartsContent jobCharts = job.getCharts();
                String nikName = jobCharts.getNikeName();
                if(nikName != null && !nikName.isBlank()){
                    preparedCount.setString(1, tokenNikeName);
                    preparedStatement.setString(1,tokenNikeName);
                    preparedCount.setString(2,"%"+nikName+"%");
                    preparedStatement.setString(2,"%"+nikName+"%");
                }else{
                    preparedCount.setString(1, tokenNikeName);
                    preparedStatement.setString(1,tokenNikeName);
                    preparedCount.setString(2, "%");
                    preparedStatement.setString(2, "%");
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
                        userDetails.setRegisterTime(resultSet.getString("register_time"));
                        userDetails.setDescribes(resultSet.getString("describes"));
                        userDetails.setNikeName(resultSet.getString("nike_name"));
                        userDetails.setImgPath(resultSet.getString("img_path"));
                        userDetails.setSortFirst(resultSet.getString("sort_first"));
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

    public static String queryAnswerFriends(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
        if(tokenSession!=null && !tokenSession.isBlank() && tokenNikeName != null){
            try(Connection connection = JdbcUtils.getConnection();
                PreparedStatement preparedCount = connection.prepareStatement("select count(src_user_id) from apply_history where tar_user_id =? and src_user_id like ? and apply_state=0");
                PreparedStatement preparedStatement = connection.prepareStatement("select histy.src_user_id, histy.apply_time,histy.apply_msg, usdet.img_path from " +
                        " (select src_user_id,apply_time,apply_msg from apply_history where tar_user_id =? and src_user_id like ? and apply_state=0) histy " +
                        " join user_details usdet on histy.src_user_id = usdet.nike_name order by histy.apply_time limit ?,?")){
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
                        userDetails.setRegisterTime(resultSet.getString("apply_time"));
                        userDetails.setDescribes(resultSet.getString("apply_msg"));
                        userDetails.setNikeName(resultSet.getString("src_user_id"));
                        userDetails.setImgPath(resultSet.getString("img_path"));
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

    public static String handlerApplyFriend(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String message = job.getCharts().getMessage();
        String nikeName = job.getCharts().getNikeName();
        String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
        if(tokenSession!=null && !tokenSession.isBlank() && message!=null && !message.isBlank()
               && nikeName!= null && !nikeName.isBlank() && tokenNikeName != null){
            if(message.equals("delete")){
              rt=deleteApplyFriend(nikeName,tokenNikeName);
            }else if(message.equals("accept")){
                rt = acceptApplyFriend(nikeName,tokenNikeName);
            }else if(message.equals("reject")){
                rt=rejectApplyFriend(nikeName,tokenNikeName);
            }
        }
        return rt;
    }
/* 不考虑极端情况 添加好友  */
    private static String acceptApplyFriend(String src, String tar) {
        String rt="";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement=connection.prepareStatement("update apply_history set apply_state=1 where src_user_id=? and tar_user_id=? and apply_state=0");
            PreparedStatement friendPreparedStatement = connection.prepareStatement("insert into friends_history(src_user_id,tar_user_id,add_sources,active_time,cur_state) values (?,?,?,CURDATE(),1)")){
            preparedStatement.setString(1,src);
            preparedStatement.setString(2,tar);
            int i = preparedStatement.executeUpdate();
            friendPreparedStatement.setString(1,src);
            friendPreparedStatement.setString(2,tar);
            friendPreparedStatement.setString(3,"1");
            int i1 = friendPreparedStatement.executeUpdate();
            friendPreparedStatement.setString(1,tar);
            friendPreparedStatement.setString(2,src);
            friendPreparedStatement.setString(3,"2");
            int i2 = friendPreparedStatement.executeUpdate();
            if(i>0 && i1>0 && i2>0){
                baseResponse.setCode("200");
                baseResponse.setMsg("接受申请成功:"+src);
                baseResponse.setUser(src);
                baseResponse.setUrl("handlerApplyFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                connection.commit();
            }else{
                baseResponse.setMsg("接受申请失败:"+src);
                baseResponse.setUrl("handlerApplyFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                connection.rollback();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }

    private static String deleteApplyFriend(String src,String tar){
        String rt="";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement=connection.prepareStatement("delete from apply_history where src_user_id=? and tar_user_id=? and apply_state=0")){
            preparedStatement.setString(1,src);
            preparedStatement.setString(2,tar);
            int i = preparedStatement.executeUpdate();
            if(i>0){
                baseResponse.setCode("200");
                baseResponse.setMsg("删除申请成功:"+src);
                baseResponse.setUser(src);
                baseResponse.setUrl("handlerApplyFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }else{
                baseResponse.setMsg("删除失败:"+src);
                baseResponse.setUrl("handlerApplyFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }
            connection.commit();
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }

    private static String rejectApplyFriend(String src,String tar){
        String rt="";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement=connection.prepareStatement("update apply_history set apply_state=2 where src_user_id=? and tar_user_id=? and apply_state=0")){
            preparedStatement.setString(1,src);
            preparedStatement.setString(2,tar);
            int i = preparedStatement.executeUpdate();
            if(i>0){
                baseResponse.setCode("200");
                baseResponse.setMsg("拒绝申请成功:"+src);
                baseResponse.setUser(src);
                baseResponse.setUrl("handlerApplyFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }else{
                baseResponse.setMsg("拒绝申请失败:"+src);
                baseResponse.setUrl("handlerApplyFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
            }
            connection.commit();
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }
//现有好友处理
    public static String handlerCurrentFriend(WebSocketChannelInHandler.WebSocketState job) {
        String rt="";
        String tokenSession = job.getCharts().getTokenSession();
        String message = job.getCharts().getMessage();
        String nikeName = job.getCharts().getNikeName();
        if(tokenSession!=null && !tokenSession.isBlank() && message!=null && !message.isBlank()
                && nikeName!= null && !nikeName.isBlank()){
            String tokenNikeName = WebSocketChannelOutHandler.USER_UUIDS.get(tokenSession);
            if(tokenNikeName!=null){
                if(message.equals("add group")){

                }else if(message.equals("new message")){
                    rt=addMsgView(tokenNikeName,nikeName);
                }else if(message.equals("delete user")){
                    rt=deleteCurrentFriend(tokenNikeName,nikeName);
                }
            }
        }
        return rt;
    }

    private static String addMsgView(String src,String tar){
        String rt="";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select id,user_src, user_tar, show_state, src_tar_uuid, last_msg_time from msg_view where user_src=? and user_tar=?"
                    ,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
            PreparedStatement insert = connection.prepareStatement("insert into msg_view(user_src, user_tar, src_tar_uuid, last_msg_time,id) values (?,?,UUID(),unix_timestamp(now()),UUID())")){
            preparedStatement.setString(1,src);
            preparedStatement.setString(2,tar);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()){
                //修改自已当前的视图信息
                resultSet.updateInt("show_state", 1);
                resultSet.updateInt("last_msg_time",(int)(System.currentTimeMillis()/1000));
                resultSet.updateRow();
            }else{
                preparedStatement.setString(1,tar);
                preparedStatement.setString(2,src);
                resultSet = preparedStatement.executeQuery();
                if(resultSet.next()){
                    String tempUUID = resultSet.getString("src_tar_uuid");
                    //从对方已有信息获得数据 添加自已的视图信息 src_tar_uuid 这个是双方唯一的信息认证标识
                    resultSet.moveToInsertRow();
                    resultSet.updateString("id",UUID.randomUUID().toString());
                    resultSet.updateString("user_src",src);
                    resultSet.updateString("user_tar",tar);
                    resultSet.updateInt("show_state",1);
                    resultSet.updateString("src_tar_uuid",tempUUID);
                    resultSet.updateInt("last_msg_time",(int)(System.currentTimeMillis()/1000));
                    resultSet.insertRow();
                }else{
                    //这里是完全的新增一个视图
                    insert.setString(1,src);
                    insert.setString(2,tar);
                    insert.executeUpdate();
                }
            }
            connection.commit();
            baseResponse.setCode("200");
            baseResponse.setUrl("handlerCurrentFriend");
            baseResponse.setMsg("添加聊天视图成功");
            rt = WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }
    /* src发起的源 tar 目标 */
    private static String deleteCurrentFriend(String src,String tar){
        String rt="";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("update friends_history set cur_state=2,delete_source=?,delete_time=CURDATE() " +
                    "where src_user_id=? and tar_user_id=? and cur_state=1")){
            preparedStatement.setString(1,"1");
            preparedStatement.setString(2,src);
            preparedStatement.setString(3,tar);
            int num1 = preparedStatement.executeUpdate();
            preparedStatement.setString(1,"2");
            preparedStatement.setString(2,tar);
            preparedStatement.setString(3,src);
            int num2 = preparedStatement.executeUpdate();
            if(num1>0 && num2>0){
                baseResponse.setCode("200");
                baseResponse.setMsg("删除好友成功:"+src);
                baseResponse.setUser(tar);
                baseResponse.setUrl("handlerCurrentFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                connection.commit();
            }else{
                baseResponse.setMsg("删除好友失败:"+src);
                baseResponse.setUrl("handlerCurrentFriend");
                rt=WebSocketChannelInHandler.MAP_JSON.writeValueAsString(baseResponse);
                connection.rollback();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }

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
                    PreparedStatement preparedUpdate = connection.prepareStatement("update msg_view set last_msg=?,last_msg_time=unix_timestamp(now()) where user_src=? and user_tar=? and src_tar_uuid=?");
                    PreparedStatement insert = connection.prepareStatement(" insert into msg_describe(id, src_tar_uuid, data_type, receive_state, send_time, content, file_name, src_user, tar_user ) " +
                            " values (UUID(),?,?,0,unix_timestamp(now()),?,?,?,?)")){
                    if(message.length()>82){
                        preparedUpdate.setString(1,message.substring(0,82));
                    }else{
                        preparedUpdate.setString(1,message);
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
