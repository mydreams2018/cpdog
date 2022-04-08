package cn.kungreat.boot.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class JdbcUtils {

    private final static HikariDataSource DATA_SOURCE;

    static {
        HikariConfig config = new HikariConfig();
        config.setDataSourceClassName("");
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/socketcharts?useSSL=true&serverTimezone=GMT%2B8");
        config.setUsername("root");
        config.setPassword("yjssaje");
        config.setAutoCommit(false);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "50");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "512");
        DATA_SOURCE = new HikariDataSource(config);
    }

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
