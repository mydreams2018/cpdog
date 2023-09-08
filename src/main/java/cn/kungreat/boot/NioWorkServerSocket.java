package cn.kungreat.boot;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

public interface NioWorkServerSocket {

    <T> NioWorkServerSocket setOption(SocketOption<T> name, T value);
    void setOption(SocketChannel channel) throws IOException;
    NioWorkServerSocket buildThread();
    NioWorkServerSocket buildSelector() throws IOException;
    NioWorkServerSocket start();
    Thread getWorkThreads() ;
    Selector getSelector();
    LinkedList<SelectionKey> getTlsInitKey();
}
