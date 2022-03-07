package cn.kungreat.test;

import cn.kungreat.boot.NioWorkServerSocket;
import cn.kungreat.boot.impl.ChooseWorkServerImpl;
import cn.kungreat.boot.impl.DefaultLogServer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class test {
    public static void main(String[] args) throws IOException {
//        NioBossServerSocket nioBossServerSocket = NioBossServerSocketImpl.create();
//        nioBossServerSocket.buildThread();
//        Properties properties = System.getProperties();
//        properties.forEach((k,v)->{
//            System.out.println(k+":"+v);
//        });
//        Logger log = DefaultLogServer.createLog("C:/Users/kungr/Pictures/Saved Pictures"
//                ,"kungreat.cn");
//        log.log(Level.WARNING,"WARNING 1");
        ChooseWorkServerImpl chooseWorkServer = new ChooseWorkServerImpl();
        NioWorkServerSocket[] serverSockets = new NioWorkServerSocket[19];
        for(int x=0;x<100;x++){
            chooseWorkServer.choose(serverSockets);
        }
    }
}
