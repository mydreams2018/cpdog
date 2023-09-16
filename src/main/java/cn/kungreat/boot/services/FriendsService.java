package cn.kungreat.boot.services;

import cn.kungreat.boot.GlobalEventListener;
import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.handler.WebSocketConvertData;
import cn.kungreat.boot.handler.WebSocketConvertDataOut;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.jb.EventBean;
import cn.kungreat.boot.jb.QueryResult;
import cn.kungreat.boot.jb.UserDetails;
import cn.kungreat.boot.utils.JdbcUtils;
import cn.kungreat.boot.utils.Paging;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@CpdogController(index = 5)
public class FriendsService {
    public static String queryUsers(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedCount = connection.prepareStatement("select count(id) from user_details where nike_name LIKE ?");
             PreparedStatement preparedStatement = connection.prepareStatement("select register_time,describes,nike_name,img_path,sort_first from user_details " +
                     "where nike_name LIKE ? order by sort_first limit ?,? ")) {
            WebSocketConvertData.ChartsContent jobCharts = job.getCharts();
            String nikName = jobCharts.getNikeName();
            if (nikName != null && !nikName.isBlank()) {
                preparedCount.setString(1, "%" + nikName + "%");
                preparedStatement.setString(1, "%" + nikName + "%");
            } else {
                preparedCount.setString(1, "%");
                preparedStatement.setString(1, "%");
            }
            ResultSet rs1 = preparedCount.executeQuery();
            int nums = 0;
            if (rs1.next()) {
                nums = rs1.getInt(1);
            }
            List<UserDetails> list = null;
            if (nums > 0) {
                Paging paging = new Paging();
                list = new ArrayList<>();
                paging.setCurrentPage(jobCharts.getCurrentPage());
                preparedStatement.setInt(2, paging.getStart());
                preparedStatement.setInt(3, paging.getPageSize());
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    UserDetails userDetails = new UserDetails();
                    userDetails.setRegisterTime(resultSet.getString("register_time"));
                    userDetails.setDescribes(resultSet.getString("describes"));
                    userDetails.setNikeName(resultSet.getString("nike_name"));
                    userDetails.setImgPath(resultSet.getString("img_path"));
                    userDetails.setSortFirst(resultSet.getString("sort_first"));
                    list.add(userDetails);
                }
                paging.setData(nums, paging.getPageSize(), paging.getCurrentPage());
                QueryResult result = new QueryResult();
                result.setDataList(list);
                result.setPage(paging);
                result.setCurrentActiveId(jobCharts.getCurrentActiveId());
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }

    public static String applyFriends(WebSocketConvertData.ReceiveObj first) {
        String rt = "";
        List<String> nikeNames = first.getCharts().getNikeNames();
        String tokenSession = first.getCharts().getTokenSession();
        if (nikeNames != null && nikeNames.size() > 0 && tokenSession != null) {
            final BaseResponse baseResponse = new BaseResponse();
            String nikeNm = WebSocketConvertDataOut.USER_UUIDS.get(tokenSession);
            if (nikeNm != null) {
                String message = first.getCharts().getMessage();
                StringBuilder nks = new StringBuilder();
                for (int x = 0; x < nikeNames.size(); x++) {
                    if (x == nikeNames.size() - 1) {
                        nks.append("?");
                        nks.append(")");
                    } else {
                        nks.append("?");
                        nks.append(",");
                    }
                }
                try (Connection connection = JdbcUtils.getConnection();
                     PreparedStatement statement = connection.prepareStatement("insert into apply_history (src_user_id, tar_user_id,apply_msg,apply_time) values (?,?,?,CURDATE())");
                     PreparedStatement preparedStatement = connection.prepareStatement("select tar_user_id from friends_history where src_user_id = ? and cur_state = 1 and tar_user_id in (" + nks)) {
                    clearApplyFriends(nikeNm, nikeNames, preparedStatement);
                    for (int x = 0; x < nikeNames.size(); x++) {
                        statement.setString(1, nikeNm);
                        statement.setString(2, nikeNames.get(x));
                        statement.setString(3, message);
                        statement.addBatch();
                    }
                    if (nikeNames.size() > 0) {
                        int[] ints = statement.executeBatch();
                        statement.clearBatch();
                        baseResponse.setCode("200");
                        baseResponse.setMsg("申请添加好友成功.共" + ints.length + "个人");
                        baseResponse.setUrl("applyFriends");
                        rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
                        connection.commit();
                        //申请好友添加到通知队列中
                        String srcImgPath = first.getCharts().getImgPath();
                        for (int i = 0; i < nikeNames.size(); i++) {
                            EventBean eventAdd = new EventBean();
                            eventAdd.setSrc(nikeNm);
                            eventAdd.setTar(nikeNames.get(i));
                            eventAdd.setUrl("eventAddFriends");
                            eventAdd.setDescribes(message);
                            eventAdd.setImgPath(srcImgPath);
                            GlobalEventListener.EVENT_BLOCKING_QUEUE.offer(eventAdd);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return rt;
    }

    //查询当前的好友. 过滤掉重复的申请
    private static void clearApplyFriends(String nikeName, List<String> srcs, PreparedStatement preparedStatement) throws SQLException {
        List<String> rts = new ArrayList<>();
        preparedStatement.setString(1, nikeName);
        for (int i = 0; i < srcs.size(); i++) {
            preparedStatement.setString(i + 2, srcs.get(i));
        }
        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()) {
            rts.add(resultSet.getString("tar_user_id"));
        }
        if (rts.size() > 0) {
            Iterator<String> stringIterator = srcs.iterator();
            while (stringIterator.hasNext()) {
                String temp = stringIterator.next();
                if (rts.contains(temp)) {
                    stringIterator.remove();
                }
            }
        }
    }


    public static String queryUsersFriends(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        String tokenSession = job.getCharts().getTokenSession();
        String tokenNikeName = WebSocketConvertDataOut.USER_UUIDS.get(tokenSession);
        if (tokenSession != null && !tokenSession.isBlank() && tokenNikeName != null) {
            try (Connection connection = JdbcUtils.getConnection();
                 PreparedStatement preparedCount = connection.prepareStatement("select count(src_user_id) from friends_history where src_user_id= ? and tar_user_id LIKE ? and cur_state=1");
                 PreparedStatement preparedStatement = connection.prepareStatement("select nike_name,register_time,describes,img_path,sort_first from user_details where nike_name in " +
                         "(select tar_user_id from friends_history where src_user_id= ? and tar_user_id LIKE ? and cur_state=1) order by sort_first limit ?,?")) {
                WebSocketConvertData.ChartsContent jobCharts = job.getCharts();
                String nikName = jobCharts.getNikeName();
                if (nikName != null && !nikName.isBlank()) {
                    preparedCount.setString(1, tokenNikeName);
                    preparedStatement.setString(1, tokenNikeName);
                    preparedCount.setString(2, "%" + nikName + "%");
                    preparedStatement.setString(2, "%" + nikName + "%");
                } else {
                    preparedCount.setString(1, tokenNikeName);
                    preparedStatement.setString(1, tokenNikeName);
                    preparedCount.setString(2, "%");
                    preparedStatement.setString(2, "%");
                }
                ResultSet rs1 = preparedCount.executeQuery();
                int nums = 0;
                if (rs1.next()) {
                    nums = rs1.getInt(1);
                }
                List<UserDetails> list = null;
                if (nums > 0) {
                    Paging paging = new Paging();
                    list = new ArrayList<>();
                    paging.setCurrentPage(jobCharts.getCurrentPage());
                    preparedStatement.setInt(3, paging.getStart());
                    preparedStatement.setInt(4, paging.getPageSize());
                    ResultSet resultSet = preparedStatement.executeQuery();
                    while (resultSet.next()) {
                        UserDetails userDetails = new UserDetails();
                        userDetails.setRegisterTime(resultSet.getString("register_time"));
                        userDetails.setDescribes(resultSet.getString("describes"));
                        userDetails.setNikeName(resultSet.getString("nike_name"));
                        userDetails.setImgPath(resultSet.getString("img_path"));
                        userDetails.setSortFirst(resultSet.getString("sort_first"));
                        list.add(userDetails);
                    }
                    paging.setData(nums, paging.getPageSize(), paging.getCurrentPage());
                    QueryResult result = new QueryResult();
                    result.setDataList(list);
                    result.setPage(paging);
                    result.setCurrentActiveId(jobCharts.getCurrentActiveId());
                    rt = WebSocketConvertData.MAP_JSON.writeValueAsString(result);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return rt;
    }

    public static String queryAnswerFriends(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        String tokenSession = job.getCharts().getTokenSession();
        String tokenNikeName = WebSocketConvertDataOut.USER_UUIDS.get(tokenSession);
        if (tokenSession != null && !tokenSession.isBlank() && tokenNikeName != null) {
            try (Connection connection = JdbcUtils.getConnection();
                 PreparedStatement preparedCount = connection.prepareStatement("select count(src_user_id) from apply_history where tar_user_id =? and src_user_id like ? and apply_state=0");
                 PreparedStatement preparedStatement = connection.prepareStatement("select histy.src_user_id, histy.apply_time,histy.apply_msg, usdet.img_path from " +
                         " (select src_user_id,apply_time,apply_msg from apply_history where tar_user_id =? and src_user_id like ? and apply_state=0) histy " +
                         " join user_details usdet on histy.src_user_id = usdet.nike_name order by histy.apply_time desc limit ?,?")) {
                WebSocketConvertData.ChartsContent jobCharts = job.getCharts();
                String srcNikName = jobCharts.getNikeName();
                if (srcNikName != null && !srcNikName.isBlank()) {
                    preparedCount.setString(1, tokenNikeName);
                    preparedCount.setString(2, "%" + srcNikName + "%");
                    preparedStatement.setString(1, tokenNikeName);
                    preparedStatement.setString(2, "%" + srcNikName + "%");
                } else {
                    preparedCount.setString(1, tokenNikeName);
                    preparedCount.setString(2, "%");
                    preparedStatement.setString(1, tokenNikeName);
                    preparedStatement.setString(2, "%");
                }
                ResultSet rs1 = preparedCount.executeQuery();
                int nums = 0;
                if (rs1.next()) {
                    nums = rs1.getInt(1);
                }
                List<UserDetails> list = null;
                if (nums > 0) {
                    Paging paging = new Paging();
                    list = new ArrayList<>();
                    paging.setCurrentPage(jobCharts.getCurrentPage());
                    preparedStatement.setInt(3, paging.getStart());
                    preparedStatement.setInt(4, paging.getPageSize());
                    ResultSet resultSet = preparedStatement.executeQuery();
                    while (resultSet.next()) {
                        UserDetails userDetails = new UserDetails();
                        userDetails.setRegisterTime(resultSet.getString("apply_time"));
                        userDetails.setDescribes(resultSet.getString("apply_msg"));
                        userDetails.setNikeName(resultSet.getString("src_user_id"));
                        userDetails.setImgPath(resultSet.getString("img_path"));
                        list.add(userDetails);
                    }
                    paging.setData(nums, paging.getPageSize(), paging.getCurrentPage());
                    QueryResult result = new QueryResult();
                    result.setDataList(list);
                    result.setPage(paging);
                    result.setCurrentActiveId(jobCharts.getCurrentActiveId());
                    rt = WebSocketConvertData.MAP_JSON.writeValueAsString(result);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return rt;
    }

    public static String handlerApplyFriend(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        String tokenSession = job.getCharts().getTokenSession();
        String message = job.getCharts().getMessage();
        String nikeName = job.getCharts().getNikeName();
        String tokenNikeName = WebSocketConvertDataOut.USER_UUIDS.get(tokenSession);
        if (tokenSession != null && !tokenSession.isBlank() && message != null && !message.isBlank()
                && nikeName != null && !nikeName.isBlank() && tokenNikeName != null) {
            if (message.equals("delete")) {
                rt = deleteApplyFriend(nikeName, tokenNikeName);
            } else if (message.equals("accept")) {
                rt = acceptApplyFriend(nikeName, tokenNikeName);
            } else if (message.equals("reject")) {
                rt = rejectApplyFriend(nikeName, tokenNikeName);
            }
            //通知对方 申请好友事件 start
            EventBean eventAdd = new EventBean();
            eventAdd.setSrc(tokenNikeName);
            eventAdd.setTar(nikeName);
            eventAdd.setUrl("eventApplyFriend");
            eventAdd.setImgPath(job.getCharts().getImgPath());
            eventAdd.setDescribes(job.getCharts().getDescribes());
            eventAdd.setType(message);
            GlobalEventListener.EVENT_BLOCKING_QUEUE.offer(eventAdd);
            //通知对方 申请好友事件 end
        }
        return rt;
    }

    /* 不考虑极端情况 添加好友  */
    private static String acceptApplyFriend(String src, String tar) {
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("update apply_history set apply_state=1 where src_user_id=? and tar_user_id=? and apply_state=0");
             PreparedStatement friendPreparedStatement = connection.prepareStatement("insert into friends_history(src_user_id,tar_user_id,add_sources,active_time,cur_state) values (?,?,?,CURDATE(),1)")) {
            preparedStatement.setString(1, src);
            preparedStatement.setString(2, tar);
            int i = preparedStatement.executeUpdate();
            friendPreparedStatement.setString(1, src);
            friendPreparedStatement.setString(2, tar);
            friendPreparedStatement.setString(3, "1");
            int i1 = friendPreparedStatement.executeUpdate();
            friendPreparedStatement.setString(1, tar);
            friendPreparedStatement.setString(2, src);
            friendPreparedStatement.setString(3, "2");
            int i2 = friendPreparedStatement.executeUpdate();
            if (i > 0 && i1 > 0 && i2 > 0) {
                baseResponse.setCode("200");
                baseResponse.setMsg("接受申请成功:" + src);
                baseResponse.setUser(src);
                baseResponse.setUrl("handlerApplyFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
                connection.commit();
            } else {
                baseResponse.setMsg("接受申请失败:" + src);
                baseResponse.setUrl("handlerApplyFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
                connection.rollback();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }

    private static String deleteApplyFriend(String src, String tar) {
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("delete from apply_history where src_user_id=? and tar_user_id=? and apply_state=0")) {
            preparedStatement.setString(1, src);
            preparedStatement.setString(2, tar);
            int i = preparedStatement.executeUpdate();
            if (i > 0) {
                baseResponse.setCode("200");
                baseResponse.setMsg("删除申请成功:" + src);
                baseResponse.setUser(src);
                baseResponse.setUrl("handlerApplyFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
            } else {
                baseResponse.setMsg("删除失败:" + src);
                baseResponse.setUrl("handlerApplyFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
            }
            connection.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }

    private static String rejectApplyFriend(String src, String tar) {
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("update apply_history set apply_state=2 where src_user_id=? and tar_user_id=? and apply_state=0")) {
            preparedStatement.setString(1, src);
            preparedStatement.setString(2, tar);
            int i = preparedStatement.executeUpdate();
            if (i > 0) {
                baseResponse.setCode("200");
                baseResponse.setMsg("拒绝申请成功:" + src);
                baseResponse.setUser(src);
                baseResponse.setUrl("handlerApplyFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
            } else {
                baseResponse.setMsg("拒绝申请失败:" + src);
                baseResponse.setUrl("handlerApplyFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
            }
            connection.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }

    //现有好友处理
    public static String handlerCurrentFriend(WebSocketConvertData.ReceiveObj job) {
        String rt = "";
        String tokenSession = job.getCharts().getTokenSession();
        String message = job.getCharts().getMessage();
        String nikeName = job.getCharts().getNikeName();
        if (tokenSession != null && !tokenSession.isBlank() && message != null && !message.isBlank()
                && nikeName != null && !nikeName.isBlank()) {
            String tokenNikeName = WebSocketConvertDataOut.USER_UUIDS.get(tokenSession);
            if (tokenNikeName != null) {
                if (message.equals("add group")) {

                } else if (message.equals("new message")) {
                    rt = addMsgView(tokenNikeName, nikeName);
                } else if (message.equals("delete user")) {
                    rt = deleteCurrentFriend(tokenNikeName, nikeName);
                }
            }
        }
        return rt;
    }

    private static String addMsgView(String src, String tar) {
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("select id,user_src, user_tar, show_state, src_tar_uuid, last_msg_time from msg_view where user_src=? and user_tar=?"
                     , ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
             PreparedStatement insert = connection.prepareStatement("insert into msg_view(user_src, user_tar, src_tar_uuid, last_msg_time,id) values (?,?,UUID(),unix_timestamp(now()),UUID())")) {
            preparedStatement.setString(1, src);
            preparedStatement.setString(2, tar);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                //修改自已当前的视图信息
                resultSet.updateInt("show_state", 1);
                resultSet.updateInt("last_msg_time", (int) (System.currentTimeMillis() / 1000));
                resultSet.updateRow();
            } else {
                preparedStatement.setString(1, tar);
                preparedStatement.setString(2, src);
                resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String tempUUID = resultSet.getString("src_tar_uuid");
                    //从对方已有信息获得数据 添加自已的视图信息 src_tar_uuid 这个是双方唯一的信息认证标识
                    resultSet.moveToInsertRow();
                    resultSet.updateString("id", UUID.randomUUID().toString());
                    resultSet.updateString("user_src", src);
                    resultSet.updateString("user_tar", tar);
                    resultSet.updateInt("show_state", 1);
                    resultSet.updateString("src_tar_uuid", tempUUID);
                    resultSet.updateInt("last_msg_time", (int) (System.currentTimeMillis() / 1000));
                    resultSet.insertRow();
                } else {
                    //这里是完全的新增一个视图
                    insert.setString(1, src);
                    insert.setString(2, tar);
                    insert.executeUpdate();
                }
            }
            connection.commit();
            baseResponse.setCode("200");
            baseResponse.setUrl("handlerCurrentFriend");
            baseResponse.setMsg("添加聊天视图成功:" + tar);
            rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }

    /* src发起的源 tar 目标 */
    private static String deleteCurrentFriend(String src, String tar) {
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try (Connection connection = JdbcUtils.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("update friends_history set cur_state=2,delete_source=?,delete_time=CURDATE() " +
                     "where src_user_id=? and tar_user_id=? and cur_state=1")) {
            preparedStatement.setString(1, "1");
            preparedStatement.setString(2, src);
            preparedStatement.setString(3, tar);
            int num1 = preparedStatement.executeUpdate();
            preparedStatement.setString(1, "2");
            preparedStatement.setString(2, tar);
            preparedStatement.setString(3, src);
            int num2 = preparedStatement.executeUpdate();
            if (num1 > 0 && num2 > 0) {
                baseResponse.setCode("200");
                baseResponse.setMsg("删除好友成功:" + tar);
                baseResponse.setUser(tar);
                baseResponse.setUrl("handlerCurrentFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
                connection.commit();
                //通知对方删除事件 start
                EventBean eventAdd = new EventBean();
                eventAdd.setSrc(src);
                eventAdd.setTar(tar);
                eventAdd.setUrl("eventDeleteCurFriend");
                GlobalEventListener.EVENT_BLOCKING_QUEUE.offer(eventAdd);
                //通知对方删除事件 end
            } else {
                baseResponse.setMsg("删除好友失败:" + tar);
                baseResponse.setUrl("handlerCurrentFriend");
                rt = WebSocketConvertData.MAP_JSON.writeValueAsString(baseResponse);
                connection.rollback();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rt;
    }
}
