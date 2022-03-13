package cn.kungreat.boot;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public interface NioWorkServerSocket {

    <T> NioWorkServerSocket setOption​(SocketOption<T> name, T value) throws IOException;
    void setOption​(SocketChannel channel) throws IOException;
    NioWorkServerSocket buildThread();
    NioWorkServerSocket buildSelector(int bufferSize) throws IOException;
    NioWorkServerSocket start();
    Thread getWorkThreads() ;
    Selector getSelector();
}
