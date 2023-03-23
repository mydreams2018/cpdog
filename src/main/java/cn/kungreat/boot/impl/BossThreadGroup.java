package cn.kungreat.boot.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BossThreadGroup extends ThreadGroup{
    private static final Logger LOGGER = LoggerFactory.getLogger(BossThreadGroup.class);

    public BossThreadGroup(String name) {
        super(name);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LOGGER.error(t.getName(),e);
    }
}
