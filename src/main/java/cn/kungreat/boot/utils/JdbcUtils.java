package cn.kungreat.boot.utils;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class JdbcUtils {

    private final static HikariDataSource DATA_SOURCE;

    static {
        InputStream cpdog = ClassLoader.getSystemResourceAsStream("cpdog.properties");
        Properties props = new Properties();
        try {
            props.load(cpdog);
        } catch (IOException e) {
            e.printStackTrace();
        }
        WebSocketChannelInHandler.FILE_PATH=props.getProperty("user.imgPath");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("jdbc.url"));
        config.setUsername(props.getProperty("user.name"));
        config.setPassword(props.getProperty("user.password"));
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
