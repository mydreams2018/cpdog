package cn.kungreat.boot;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.handler.WebSocketChannelOutHandler;
import cn.kungreat.boot.handler.WebSocketProtocolHandler;
import cn.kungreat.boot.impl.ChooseWorkServerImpl;
import cn.kungreat.boot.impl.DefaultLogServer;
import cn.kungreat.boot.impl.NioBossServerSocketImpl;
import cn.kungreat.boot.impl.NioWorkServerSocketImpl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class CpdogMain {

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
