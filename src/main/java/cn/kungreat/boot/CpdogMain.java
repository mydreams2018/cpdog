package cn.kungreat.boot;

import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.filter.BaseWebSocketFilter;
import cn.kungreat.boot.handler.WebSocketProtocolHandler;
import cn.kungreat.boot.impl.ChooseWorkServerImpl;
import cn.kungreat.boot.impl.NioBossServerSocketImpl;
import cn.kungreat.boot.impl.NioWorkServerSocketImpl;
import cn.kungreat.boot.utils.JdbcUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CpdogMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpdogMain.class);
    public static final List<Class<?>> CONTROLLERS = new ArrayList<>();
    public static final List<Class<?>> EVENTS = new ArrayList<>();
    //运行线程内共享数据 GlobalEventListener NioWorkServerSocketImpl 有使用 存出站的加密数据
    public static final ThreadLocal<ByteBuffer> THREAD_LOCAL = new ThreadLocal<>();
    //图片存放地址
    public static final String FILE_PATH;
    //刷新token的url前后端配合使用
    public static final String REFRESH_TOKEN_URL;

    static {
        InputStream cpDogFile = ClassLoader.getSystemResourceAsStream("cpdog.properties");
        Properties props = new Properties();
        try {
            props.load(cpDogFile);
            String scanPack = props.getProperty("scan.packages");
            setControllers(scanPack);
        } catch (Exception e) {
            LOGGER.error("CpdogMain-init失败:{}", e.getLocalizedMessage());
        }
        FILE_PATH = props.getProperty("user.imgPath");
        REFRESH_TOKEN_URL = props.getProperty("refresh.token.url");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("jdbc.url"));
        config.setUsername(props.getProperty("user.name"));
        config.setPassword(props.getProperty("user.password"));
        config.setAutoCommit(false);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "50");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "512");
        JdbcUtils.dataSource = new HikariDataSource(config);
    }

    private static void setControllers(String scanPack) throws Exception {
        String pks = scanPack == null ? CpdogMain.class.getPackage().getName().replace(".", "/") : scanPack.replace(".", "/");
        URL location = ClassLoader.getSystemClassLoader().getResource(pks);
        String protocol = location.getProtocol();
        if (protocol.equals("file")) {
            loopFile(new File(location.getFile()), pks.replace("/", "."));
        }
        if (protocol.equals("jar")) {
            loopJap(location, pks);
        }
    }

    public static void loopFile(File fl, String pks) throws Exception {
        if (fl.exists()) {
            File[] list = fl.listFiles();
            if (list != null) {
                for (File temp : list) {
                    String s = temp.toString().replace(File.separator, ".");
                    if (s.endsWith(".class")) {
                        s = s.replace(".class", "");
                        Class<?> cls = Class.forName(pks + s.split(pks)[1]);
                        if (cls.isAnnotationPresent(CpdogController.class)) {
                            addControllers(cls);
                        }
                        if (cls.isAnnotationPresent(CpdogEvent.class)) {
                            addEvents(cls);
                        }
                    }
                    if (temp.isDirectory()) {
                        loopFile(temp, pks);
                    }
                }
            }
        }
    }

    public static void loopJap(URL location, String scanPack) throws Exception {
        JarURLConnection jarConnection = (JarURLConnection) location.openConnection();
        JarFile jarFile = jarConnection.getJarFile();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntryFile = entries.nextElement();
            String realName = jarEntryFile.getRealName();
            if (realName.endsWith(".class") && realName.contains(scanPack)) {
                String tempCls = realName.replace("/", ".").replace(".class", "");
                Class<?> cls = Class.forName(tempCls);
                if (cls.isAnnotationPresent(CpdogController.class)) {
                    addControllers(cls);
                }
                if (cls.isAnnotationPresent(CpdogEvent.class)) {
                    addEvents(cls);
                }
            }
        }
    }

    public static void addControllers(Class<?> cls) {
        if (CONTROLLERS.isEmpty()) {
            CONTROLLERS.add(cls);
        } else {
            int sortSrc = cls.getAnnotation(CpdogController.class).index();
            for (int i = 0; i < CONTROLLERS.size(); i++) {
                Class<?> aClass = CONTROLLERS.get(i);
                CpdogController annotation = aClass.getAnnotation(CpdogController.class);
                int index = annotation.index();
                if (sortSrc <= index) {
                    CONTROLLERS.add(i, cls);
                    return;
                } else if (i == CONTROLLERS.size() - 1) {
                    CONTROLLERS.add(cls);
                    return;
                }
            }
        }
    }

    public static void addEvents(Class<?> cls) {
        if (EVENTS.isEmpty()) {
            EVENTS.add(cls);
        } else {
            int sortSrc = cls.getAnnotation(CpdogEvent.class).index();
            for (int i = 0; i < EVENTS.size(); i++) {
                Class<?> aClass = EVENTS.get(i);
                CpdogEvent annotation = aClass.getAnnotation(CpdogEvent.class);
                int index = annotation.index();
                if (sortSrc <= index) {
                    EVENTS.add(i, cls);
                    return;
                } else if (i == EVENTS.size() - 1) {
                    EVENTS.add(cls);
                    return;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        NioBossServerSocket nioBossServerSocket = NioBossServerSocketImpl.create();
        nioBossServerSocket.buildChannel();
        nioBossServerSocket.buildThread();
        nioBossServerSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ChooseWorkServerImpl chooseWorkServer = new ChooseWorkServerImpl();
        NioWorkServerSocketImpl.addChannelProtocolHandler(new WebSocketProtocolHandler());
        NioWorkServerSocketImpl.addFilterChain(new BaseWebSocketFilter());
        NioWorkServerSocket[] workServerSockets = new NioWorkServerSocket[12];
        for (int x = 0; x < workServerSockets.length; x++) {
            NioWorkServerSocket workServerSocket = NioWorkServerSocketImpl.create();
            workServerSocket.buildThread();
            workServerSocket.buildSelector();
            workServerSocket.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            workServerSockets[x] = workServerSocket;
        }
        nioBossServerSocket.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9999), workServerSockets, chooseWorkServer);
        //主线程监听事件处理
        GlobalEventListener.loopEvent();
    }
}