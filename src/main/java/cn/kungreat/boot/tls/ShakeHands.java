package cn.kungreat.boot.tls;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * 线程池负责TLS握手
 */
public class ShakeHands {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShakeHands.class);
    public static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(2, 5, 3600,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(99, false), new CpdogThreadFactory(), new DiscardPolicy());

    final static class CpdogThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public CpdogThreadFactory() {
            group = Thread.currentThread().getThreadGroup();
            namePrefix = "ShakeHands-" + POOL_NUMBER.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new CpdogThread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    @Setter
    @Getter
    final static class CpdogThread extends Thread {

        public CpdogThread(ThreadGroup group, Runnable target, String name,
                           long stackSize) {
            super(group, target, name, stackSize);
        }

        private ByteBuffer inSrc = ByteBuffer.allocate(8192);
        private ByteBuffer inSrcDecode = ByteBuffer.allocate(8192);
        private ByteBuffer outSrcEncode = ByteBuffer.allocate(32768);

    }

    final static class DiscardPolicy implements RejectedExecutionHandler {

        public DiscardPolicy() {
        }

        /**
         * 记录日志
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            LOGGER.error("线程池队列已经满了...");
        }
    }

}
