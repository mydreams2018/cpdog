package cn.kungreat.boot.utils;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class JdbcTemplate {

    public static String register(WebSocketChannelInHandler.WebSocketState job){
        String rt = "uuid=%s;code=%s;msg=%s";
        try(Connection connection = JdbcUtils.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select phone,nike_name from user_details where phone=? and nike_name=?")){
            String phone = "";
            String nikeName = "";
            String password = "";
            String jobCharts = job.getCharts();
            String[] split = jobCharts.split("&");
            for(int x=0;x<split.length;x++){
                String[] temp = split[x].split(":");
                if(temp[0].equals("phone")){
                    phone = temp[1];
                }
                if(temp[0].equals("nikeName")){
                    nikeName = temp[1];
                }
                if(temp[0].equals("password")){
                    password = temp[1];
                }
            }
            if(phone.isEmpty() || nikeName.isEmpty() || password.isEmpty()){
                rt=String.format(rt, job.getUuid(),"100","必要数据为空");
            }else{
                preparedStatement.setString(1,phone);
                preparedStatement.setString(2,nikeName);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()){
                    //数据已经存在了
                    rt=String.format(rt, job.getUuid(),"100","用户已经存在了丢雷");
                }else{
                    PreparedStatement insert = connection.prepareStatement("insert into user_details (phone,nike_name,password) values (?,?,?)");
                    insert.setString(1,phone);
                    insert.setString(2,nikeName);
                    insert.setString(3,password);
                    int is = insert.executeUpdate();
                    if(is>0){
                        rt=String.format(rt,job.getUuid(),"200","用户注册成功");
                    }else{
                        rt=String.format(rt,job.getUuid(),"000","未知错误");
                    }
                    connection.commit();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rt;
    }
}
