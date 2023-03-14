package cn.kungreat.boot;

import cn.kungreat.boot.an.CpdogController;
import cn.kungreat.boot.an.CpdogEvent;
import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.handler.WebSocketProtocolHandler;
import cn.kungreat.boot.impl.ChooseWorkServerImpl;
import cn.kungreat.boot.impl.NioBossServerSocketImpl;
import cn.kungreat.boot.impl.NioWorkServerSocketImpl;
import cn.kungreat.boot.utils.JdbcUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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

    public static final List<Class<?>> CONTROLLERS = new ArrayList<>();
    public static final List<Class<?>> EVENTS = new ArrayList<>();
    //运行线程内共享数据 GlobalEventListener NioWorkServerSocketImpl 有使用 存出站的加密数据
    public static final ThreadLocal<ByteBuffer> THREAD_LOCAL = new ThreadLocal<>();

    static {
        InputStream cpdog = ClassLoader.getSystemResourceAsStream("cpdog.properties");
        Properties props = new Properties();
        try {
            props.load(cpdog);
            String scanPack = props.getProperty("scan.packages");
            setControllers(scanPack);
        } catch (Exception e) {
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

    private static void setControllers(String scanPack) throws Exception {
        String pks = scanPack==null?CpdogMain.class.getPackage().getName().replace(".","/"):scanPack.replace(".","/");
        URL location = ClassLoader.getSystemClassLoader().getResource(pks);
        String protocol = location.getProtocol();
        if(protocol.equals("file")){
            loopFile(new File(location.getFile()),pks.replace("/","."));
        }if(protocol.equals("jar")){
            loopJap(location,pks);
        }
    }

    public static void loopFile(File fl,String pks) throws Exception {
        if(fl.exists()){
            File[] list = fl.listFiles();
            if(list != null && list.length > 0){
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

    public static void loopJap(URL location,String scanPack) throws Exception {
        JarURLConnection jarConnection = (JarURLConnection)location.openConnection();
        JarFile jarFile = jarConnection.getJarFile();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()){
            JarEntry jarEntryFile = entries.nextElement();
            String realName = jarEntryFile.getRealName();
            if(realName.endsWith(".class") && realName.contains(scanPack)){
                String tempCls = realName.replace("/",".").replace(".class","");
                Class<?> cls = Class.forName(tempCls);
                if (cls.isAnnotationPresent(CpdogController.class)){
                    addControllers(cls);
                }
                if(cls.isAnnotationPresent(CpdogEvent.class)){
                    addEvents(cls);
                }
            }
        }
    }

    public static void addControllers(Class<?> cls){
        if(CONTROLLERS.isEmpty()){
            CONTROLLERS.add(cls);
        }else{
            int sortSrc = cls.getAnnotation(CpdogController.class).index();
            for(int i = 0; i < CONTROLLERS.size(); i++) {
                Class<?> aClass = CONTROLLERS.get(i);
                CpdogController annotation = aClass.getAnnotation(CpdogController.class);
                int index = annotation.index();
                if(sortSrc <= index){
                    CONTROLLERS.add(i,cls);
                    return;
                }else if(i==CONTROLLERS.size()-1){
                    CONTROLLERS.add(cls);
                    return;
                }
            }
        }
    }

    public static void addEvents(Class<?> cls){
        if(EVENTS.isEmpty()){
            EVENTS.add(cls);
        }else{
            int sortSrc = cls.getAnnotation(CpdogEvent.class).index();
            for(int i = 0; i < EVENTS.size(); i++) {
                Class<?> aClass = EVENTS.get(i);
                CpdogEvent annotation = aClass.getAnnotation(CpdogEvent.class);
                int index = annotation.index();
                if(sortSrc <= index){
                    EVENTS.add(i,cls);
                    return;
                }else if(i==EVENTS.size()-1){
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
        ChooseWorkServerImpl chooseWorkServer = new ChooseWorkServerImpl();
        NioWorkServerSocketImpl.addChannelInHandlers(new WebSocketChannelInHandler());
        NioWorkServerSocketImpl.addChannelOutHandlers(new WebSocketChannelOutHandler());
        NioWorkServerSocketImpl.addChannelProtocolHandler(new WebSocketProtocolHandler());
        NioWorkServerSocket[] workServerSockets = new NioWorkServerSocket[12];
        for(int x=0;x<workServerSockets.length;x++){
            NioWorkServerSocket workServerSocket = NioWorkServerSocketImpl.create();
            workServerSocket.buildThread();
            workServerSocket.buildSelector();
            workServerSocket.setOption(StandardSocketOptions.SO_KEEPALIVE,true);
            workServerSockets[x]=workServerSocket;
        }
        nioBossServerSocket.start(new InetSocketAddress(InetAddress.getLocalHost(),9999),workServerSockets,chooseWorkServer);
        //主线程监听事件处理
        GlobalEventListener.loopEvent();
    }
}