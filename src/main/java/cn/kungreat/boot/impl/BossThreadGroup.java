package cn.kungreat.boot.impl;

public class BossThreadGroup extends ThreadGroup{

    public BossThreadGroup(String name) {
        super(name);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        super.uncaughtException(t, e);
    }
}
