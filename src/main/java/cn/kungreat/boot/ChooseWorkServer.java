package cn.kungreat.boot;

public interface ChooseWorkServer {
    NioWorkServerSocket choose(NioWorkServerSocket[] workServerSockets);
}
