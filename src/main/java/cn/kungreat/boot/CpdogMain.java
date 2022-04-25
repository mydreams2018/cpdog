package cn.kungreat.boot;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.handler.WebSocketProtocolHandler;
import cn.kungreat.boot.impl.ChooseWorkServerImpl;
import cn.kungreat.boot.impl.DefaultLogServer;
import cn.kungreat.boot.impl.NioBossServerSocketImpl;
import cn.kungreat.boot.impl.NioWorkServerSocketImpl;
import cn.kungreat.boot.utils.JdbcUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.logging.Logger;

public class CpdogMain {

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
        JdbcUtils.DATA_SOURCE = new HikariDataSource(config);
    }

    public static void main(String[] args) throws Exception {
        NioBossServerSocket nioBossServerSocket = NioBossServerSocketImpl.create();
        nioBossServerSocket.buildChannel();
        nioBossServerSocket.buildThread();
        Logger log = DefaultLogServer.createLog("D:/kungreat/IdeaProjects/log2"
                ,"kungreat.cn");
        nioBossServerSocket.setLogger(log);
        ChooseWorkServerImpl chooseWorkServer = new ChooseWorkServerImpl();
        NioWorkServerSocketImpl.addChannelInHandlers(new WebSocketChannelInHandler());
        NioWorkServerSocketImpl.addChannelOutHandlers(new WebSocketChannelOutHandler());
        NioWorkServerSocketImpl.addChannelProtocolHandler(new WebSocketProtocolHandler());
        NioWorkServerSocket[] workServerSockets = new NioWorkServerSocket[12];
        for(int x=0;x<workServerSockets.length;x++){
            NioWorkServerSocket workServerSocket = NioWorkServerSocketImpl.create();
            workServerSocket.buildThread();
            workServerSocket.buildSelector(8192);
//            workServerSocket.setOption​(StandardSocketOptions.SO_KEEPALIVE,true);
            workServerSockets[x]=workServerSocket;
        }
        nioBossServerSocket.start(new InetSocketAddress(InetAddress.getLocalHost(),9999),workServerSockets,chooseWorkServer);
    }
}
