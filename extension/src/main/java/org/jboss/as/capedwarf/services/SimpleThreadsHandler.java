package org.jboss.as.capedwarf.services;

import java.util.concurrent.ArrayBlockingQueue;
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
            int maxPoolSize = Integer.parseInt(System.getProperty("jboss.capedwarf.maxPoolSize", "3"));
            executor = new ThreadPoolExecutor(1, maxPoolSize, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(maxPoolSize), this);
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
