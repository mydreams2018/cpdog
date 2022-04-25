package cn.kungreat.boot.utils;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class JdbcUtils {

    public static HikariDataSource DATA_SOURCE;

/*    默认10个连接池
    One Connection Cycle is defined as single DataSource.getConnection()/Connection.close().
    One Statement Cycle is defined as single
    Connection.prepareStatement(),
    Statement.execute(), Statement.close()
    连接默认为 自动提交关闭 每次拿连接 回滚一下.
    */
    public static Connection getConnection(){
        Connection connection = null;
        try {
            connection =  DATA_SOURCE.getConnection();
            connection.rollback();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return connection;
    }
}
