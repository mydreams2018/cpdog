package cn.kungreat.boot.services;

import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.jb.BaseResponse;
import cn.kungreat.boot.utils.JdbcUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

@CpdogController(index = 1)
public class LoginService {

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
                    baseResponse.setSkToken(UUID.randomUUID().toString());
                    baseResponse.setImgPath(resultSet.getString("img_path"));
                    WebSocketChannelOutHandler.USER_UUIDS.put(baseResponse.getSkToken(),resultSet.getString("nike_name"));
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
}
