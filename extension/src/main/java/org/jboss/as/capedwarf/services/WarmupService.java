/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.lang.reflect.Method;

import org.jboss.modules.Module;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WarmupService implements Service<Void> {
    private final Module module;

    public WarmupService(Module module) {
        this.module = module;
    }

    public void start(StartContext context) throws StartException {
        final ClassLoader cl = module.getClassLoader();
        final ClassLoader previous = SecurityActions.setTCCL(cl);
        try {
            Class<?> siClass = cl.loadClass("org.jboss.capedwarf.tasks.ServletInvoker");
            Method invoke = siClass.getMethod("invoke", String.class, String.class);
            invoke.invoke(null, module.getIdentifier().toString(), "/_ah/warmup");
        } catch (Exception e) {
            throw new StartException(e);
        } finally {
            SecurityActions.setTCCL(previous);
        }

    }

    public void stop(StopContext context) {
    }

    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}
