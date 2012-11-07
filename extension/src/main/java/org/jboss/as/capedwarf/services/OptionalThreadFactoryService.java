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

import java.util.concurrent.ThreadFactory;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Try to get ThreadFactory from MSC, else use very simple default.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OptionalThreadFactoryService implements Service<ThreadFactory> {
    private InjectedValue<ThreadFactory> tfInjectedValue = new InjectedValue<ThreadFactory>();
    private ThreadsHandler handler;
    private ThreadFactory factory;

    public OptionalThreadFactoryService(ThreadsHandler handler) {
        this.handler = handler;
    }

    public void start(StartContext context) throws StartException {
        final ThreadFactory tf = tfInjectedValue.getOptionalValue();
        if (tf == null) {
            factory = handler.getExecutor().getThreadFactory();
        }
    }

    public void stop(StopContext context) {
        if (factory != null) {
            factory = null;
            handler.ungetExecutor();
        }
    }

    public ThreadFactory getValue() throws IllegalStateException, IllegalArgumentException {
        return (factory != null) ? factory : tfInjectedValue.getValue();
    }

    public InjectedValue<ThreadFactory> getThreadFactoryInjectedValue() {
        return tfInjectedValue;
    }
}
