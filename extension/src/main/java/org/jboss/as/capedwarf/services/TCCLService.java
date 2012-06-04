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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Wrap service, using CL variable as TCCL.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TCCLService<T> implements Service<T> {
    private final Service<T> delegate;
    private final ClassLoader classLoader;

    public TCCLService(Service<T> delegate, ClassLoader classLoader) {
        this.delegate = delegate;
        this.classLoader = classLoader;
    }

    public void start(StartContext context) throws StartException {
        final ClassLoader previous = SecurityActions.setTCCL(classLoader);
        try {
            delegate.start(context);
        } finally {
            SecurityActions.setTCCL(previous);
        }
    }

    public void stop(StopContext context) {
        final ClassLoader previous = SecurityActions.setTCCL(classLoader);
        try {
            delegate.stop(context);
        } finally {
            SecurityActions.setTCCL(previous);
        }
    }

    public T getValue() throws IllegalStateException, IllegalArgumentException {
        final ClassLoader previous = SecurityActions.setTCCL(classLoader);
        try {
            return delegate.getValue();
        } finally {
            SecurityActions.setTCCL(previous);
        }
    }
}
