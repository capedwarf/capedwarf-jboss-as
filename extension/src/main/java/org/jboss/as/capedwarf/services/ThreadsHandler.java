package org.jboss.as.capedwarf.services;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Threads handler.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public interface ThreadsHandler extends RejectedExecutionHandler {
    /**
     * Get default thread pool executor.
     *
     * @return thread pool executor
     */
    ThreadPoolExecutor getExecutor();

    /**
     * Unget executor.
     */
    void ungetExecutor();
}
