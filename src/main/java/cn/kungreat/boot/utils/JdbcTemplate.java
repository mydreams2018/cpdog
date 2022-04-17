package cn.kungreat.boot.utils;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.jb.QueryResult;
import cn.kungreat.boot.jb.UserDetails;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

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
            PreparedStatement preparedStatement = connection.prepareStatement("select phone,nike_name from user_details where phone=? and password=?")){
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
                    baseResponse.setUser(resultSet.getString("nike_name"));
                    baseResponse.setSktoken(UUID.randomUUID().toString());
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
                PreparedStatement preparedCount = connection.prepareStatement("select count(src_user_id) from friends_history where src_user_id= ? and tar_user_id LIKE ?");
                PreparedStatement preparedStatement = connection.prepareStatement("select nike_name,register_time,describes,img_path,sort_first from user_details where nike_name in "+
                        "(select tar_user_id from friends_history where src_user_id= ? and tar_user_id LIKE ?) order by sort_first limit ?,?")){
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
}
