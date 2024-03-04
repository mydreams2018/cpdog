package cn.kungreat.boot.impl;

import cn.kungreat.boot.ChooseWorkServer;
import cn.kungreat.boot.NioWorkServerSocket;

import java.util.concurrent.atomic.AtomicInteger;

/*
 * 简单的轮询选择一个NioWorkServerSocket服务
 * */
public class ChooseWorkServerImpl implements ChooseWorkServer {

    private final AtomicInteger atomicInteger = new AtomicInteger(0);

    @Override
    public NioWorkServerSocket choose(NioWorkServerSocket[] workServerSockets) {
        int length = workServerSockets.length;
        int andIncrement = atomicInteger.getAndIncrement();
        if (andIncrement >= length) {
            atomicInteger.getAndSet(1);
            return workServerSockets[0];
        }
        return workServerSockets[andIncrement];
    }
}