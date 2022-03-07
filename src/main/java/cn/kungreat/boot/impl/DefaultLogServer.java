package cn.kungreat.boot.impl;

import java.io.IOException;
import java.util.logging.*;

public class DefaultLogServer{

    public static Logger createLog(String filePath,String packageName) throws IOException {
        final Logger log = Logger.getLogger(packageName);
        log.setUseParentHandlers(false);
        log.setLevel(Level.INFO);
        FileHandler fileHandler = new FileHandler(filePath+"/log%u.%g.txt",92101163,60,true);
        fileHandler.setLevel(Level.INFO);
        fileHandler.setFormatter(new SimpleFormatter());
        log.addHandler(fileHandler);
        return log;
    }

}
