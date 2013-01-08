/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.capedwarf.services;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Try to get Executor from MSC, else use default Executor.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OptionalExecutorService implements Service<Executor> {
    private InjectedValue<ExecutorService> executorInjectedValue = new InjectedValue<ExecutorService>();
    private ThreadsHandler handler;
    private ExecutorService executor;

    public OptionalExecutorService(ThreadsHandler handler) {
        this.handler = handler;
    }

    public void start(StartContext context) throws StartException {
        final ExecutorService e = executorInjectedValue.getOptionalValue();
        if (e == null) {
            executor = handler.getExecutor();
        }
    }

    public void stop(StopContext context) {
        if (executor != null) {
            executor = null;
            handler.ungetExecutor();
        }
    }

    public ExecutorService getValue() throws IllegalStateException, IllegalArgumentException {
        return (executor != null) ? executor : executorInjectedValue.getValue();
    }

    public InjectedValue<ExecutorService> getExecutorInjectedValue() {
        return executorInjectedValue;
    }
}
