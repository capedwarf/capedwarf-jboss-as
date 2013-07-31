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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Cache indexing.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CacheIndexing {
    private int prefix;
    private int offset;
    private Set<String> classes = new CopyOnWriteArraySet<>();
    private Set<Class<?>> exactClasses = new CopyOnWriteArraySet<>();

    public CacheIndexing(int prefix) {
        this.prefix = prefix;
    }

    public CacheIndexing addClass(String className) {
        classes.add(className);
        return this;
    }

    public CacheIndexing addExactClass(Class<?> clazz) {
        exactClasses.add(clazz);
        return this;
    }

    public Iterable<Class<?>> getClasses(ClassLoader cl) {
        final Set<Class<?>> clazzez = new LinkedHashSet<>(exactClasses);
        try {
            for (String clazz : classes) {
                clazzez.add(cl.loadClass(clazz));
            }
            return clazzez;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPrefix() {
        return prefix;
    }

    public CacheIndexing setOffset(int offset) {
        this.offset = offset;
        return this;
    }

    public int getOffset() {
        return offset;
    }
}
