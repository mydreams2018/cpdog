package cn.kungreat.boot.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkThreadGroup extends ThreadGroup{

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkThreadGroup.class);

    public WorkThreadGroup(String name) {
        super(name);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LOGGER.error(t.getName(),e);
    }
}
