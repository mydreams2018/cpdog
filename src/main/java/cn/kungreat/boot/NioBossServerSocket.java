package cn.kungreat.boot;


import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Set;

public interface NioBossServerSocket {

    NioBossServerSocket buildChannel() throws IOException;
    <T> NioBossServerSocket setOption(SocketOption<T> name, T value) throws IOException;
    <T> T getOption(SocketOption<T> name) throws IOException;
    Set<SocketOption<?>> supportedOptions();
    NioBossServerSocket buildThread();
    NioBossServerSocket start(SocketAddress local, int backlog, NioWorkServerSocket[] workServerSockets,
                              ChooseWorkServer chooseWorkServer) throws IOException;
    NioBossServerSocket start(SocketAddress local, NioWorkServerSocket[] workServerSockets,
                              ChooseWorkServer chooseWorkServer) throws IOException;
}
