package cn.kungreat.boot.utils;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.jb.QueryResult;
import cn.kungreat.boot.jb.UserDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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
            if(jobCharts.getNikeName() != null && !jobCharts.getNikeName().isBlank()){
                preparedCount.setString(1, "%" + jobCharts.getNikeName());
                preparedStatement.setString(1, "%" + jobCharts.getNikeName());
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
            Paging paging = new Paging();
            if(nums > 0){
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
        if(nikeNamels != null && nikeNamels.size()>0 && tokenSession!= null){
            String nikeNm = WebSocketChannelOutHandler.USER_UUIDS.get("tokenSession");

        }
        return rt;
    }
}
