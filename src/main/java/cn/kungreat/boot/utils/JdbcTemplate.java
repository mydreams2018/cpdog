package cn.kungreat.boot.utils;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.jb.BaseResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class JdbcTemplate {

    public static String register(WebSocketChannelInHandler.WebSocketState job){
        String rt = "";
        final BaseResponse baseResponse = new BaseResponse();
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select phone,nike_name from user_details where phone=? or nike_name=?")){
            WebSocketChannelInHandler.ChartsContent jobCharts = job.getCharts();
            if(jobCharts.getPhone().isEmpty() || jobCharts.getNikeName().isEmpty()
                    || jobCharts.getPassword().isEmpty() || jobCharts.getFirstLetter().isEmpty()){
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
            if(jobCharts.getPhone().isEmpty() || jobCharts.getPassword().isEmpty()){
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
                    WebSocketChannelOutHandler.USER_UUIDS.put(baseResponse.getSktoken(),jobCharts.getPhone());
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


        return rt;
    }
}
