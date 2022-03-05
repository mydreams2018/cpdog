package cn.kungreat.boot;

import cn.kungreat.boot.impl.NioBossServerSocketImpl;

public interface NioWorkServerSocket {

    public NioBossServerSocketImpl buildThread(int threadCounts);

    public NioBossServerSocketImpl start(int port);
}
