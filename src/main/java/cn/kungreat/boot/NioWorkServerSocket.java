package cn.kungreat.boot;

import cn.kungreat.boot.impl.NioBossServerSocketImpl;

public interface NioWorkServerSocket {

    NioBossServerSocketImpl buildThread(int threadCounts);
    NioBossServerSocketImpl start(int port);
}
