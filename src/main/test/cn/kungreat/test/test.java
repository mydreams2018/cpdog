package cn.kungreat.test;

import cn.kungreat.boot.NioBossServerSocket;
import cn.kungreat.boot.NioWorkServerSocket;
import cn.kungreat.boot.impl.ChooseWorkServerImpl;
import cn.kungreat.boot.impl.DefaultLogServer;
import cn.kungreat.boot.impl.NioBossServerSocketImpl;
import cn.kungreat.boot.impl.NioWorkServerSocketImpl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.logging.Logger;

public class test {
    public static void main(String[] args) throws IOException {
        NioBossServerSocket nioBossServerSocket = NioBossServerSocketImpl.create();
        nioBossServerSocket.buildChannel();
        nioBossServerSocket.buildThread();
        Logger log = DefaultLogServer.createLog("C:/Users/kungreat/Documents"
                ,"kungreat.cn");
        nioBossServerSocket.setLogger(log);
        ChooseWorkServerImpl chooseWorkServer = new ChooseWorkServerImpl();
        NioWorkServerSocket[] workServerSockets = new NioWorkServerSocket[12];
        for(int x=0;x<workServerSockets.length;x++){
            NioWorkServerSocket workServerSocket = NioWorkServerSocketImpl.create();
            workServerSocket.buildThread();
            workServerSocket.buildSelector();
            workServerSocket.setOption​(StandardSocketOptions.SO_KEEPALIVE,true);
            workServerSocket.setOption​(StandardSocketOptions.SO_RCVBUF,1024);
            workServerSockets[x]=workServerSocket;
        }
        nioBossServerSocket.start(new InetSocketAddress(InetAddress.getLocalHost(),9999),workServerSockets,chooseWorkServer);
    }
}
