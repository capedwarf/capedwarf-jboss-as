/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.capedwarf.socket;

import java.lang.reflect.Constructor;
import java.net.SocketImpl;
import java.net.SocketImplFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.capedwarf.shared.util.Utils;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfSocketFactory implements SocketImplFactory {
    public static final CapedwarfSocketFactory INSTANCE = new CapedwarfSocketFactory();

    private final Map<ClassLoader, Object> classloaders;

    private CapedwarfSocketFactory() {
        classloaders = new ConcurrentHashMap<>();
    }

    public void addClassLoader(ClassLoader cl) {
        classloaders.put(cl, 0);
    }

    public void removeClassLoader(ClassLoader cl) {
        classloaders.remove(cl);
    }

    SocketImpl createDelegate() {
        try {
            Class<?> clazz = getClass().getClassLoader().loadClass("java.net.SocksSocketImpl");
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (SocketImpl) ctor.newInstance();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public SocketImpl createSocketImpl() {
        final SocketImpl delegate = createDelegate();

        final ClassLoader tccl = Utils.getAppClassLoader();
        if (tccl != null && classloaders.containsKey(tccl)) {
            return new CapedwarfSocket(delegate);
        } else {
            return delegate;
        }
    }
}
