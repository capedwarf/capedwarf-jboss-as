package org.jboss.as.capedwarf.services;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Simple threads handler.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class SimpleThreadsHandler implements ThreadsHandler {
    private int counter;
    private ThreadPoolExecutor executor;

    public synchronized ThreadPoolExecutor getExecutor() {
        counter++;
        if (executor == null) {
            int poolSize = Integer.parseInt(System.getProperty("jboss.capedwarf.poolSize", "10"));
            long keepAliveTime = Long.parseLong(System.getProperty("jboss.capedwarf.keepAliveTime", "10"));
            int blockingSize = Integer.parseInt(System.getProperty("jboss.capedwarf.blockingSize", String.valueOf(Integer.MAX_VALUE))); // unbounded
            executor = new ThreadPoolExecutor(poolSize, poolSize, keepAliveTime, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(blockingSize), this);
        }
        return executor;
    }

    public synchronized void ungetExecutor() {
        counter--;
        if (counter <= 0 && executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        throw new RejectedExecutionException("Current thread pool executor queue: " + executor.getQueue());
    }
}
