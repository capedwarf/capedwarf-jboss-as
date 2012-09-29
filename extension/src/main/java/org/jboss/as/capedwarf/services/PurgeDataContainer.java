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

import java.util.Iterator;
import java.util.logging.Logger;

import org.infinispan.container.DefaultDataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.eviction.EvictionThreadPolicy;

/**
 * Custom purge.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class PurgeDataContainer extends DefaultDataContainer {
    private static final Logger log = Logger.getLogger(PurgeDataContainer.class.getName());

    private static final String TLE_CLASSNAME = "org.jboss.capedwarf.tasks.TaskLeaseEntity";
    private static final String QF_CLASSNAME = "com.google.appengine.api.taskqueue.QueueFactory";
    private static final String TO_CLASSNAME = "com.google.appengine.api.taskqueue.TaskOptions";

    private ClassLoader classLoader;

    PurgeDataContainer(int concurrencyLevel, int maxEntries, EvictionStrategy strategy, EvictionThreadPolicy policy, ClassLoader classLoader) {
        super(concurrencyLevel, maxEntries, strategy, policy);
        this.classLoader = classLoader;
    }

    @SuppressWarnings("unchecked")
    public void purgeExpired() {
        long currentTimeMillis = System.currentTimeMillis();
        for (Iterator<InternalCacheEntry> purgeCandidates = entries.values().iterator(); purgeCandidates.hasNext();) {
            InternalCacheEntry e = purgeCandidates.next();
            if (e.isExpired(currentTimeMillis)) {
                purgeCandidates.remove();
                Object value = e.getValue();
                if (value != null && value.getClass().getName().equals(TLE_CLASSNAME)) {
                    try {
                        // super hack-ish to re-add TO
                        Class<?> qfClass = classLoader.loadClass(QF_CLASSNAME);
                        Class<?> tleClass = value.getClass();
                        String queueName = tleClass.getMethod("getQueueName").invoke(value).toString();
                        Object queue = qfClass.getMethod("getQueue", String.class).invoke(null, queueName);
                        Object options = tleClass.getMethod("getOptions").invoke(value);
                        Class<?> toClass = classLoader.loadClass(TO_CLASSNAME);
                        queue.getClass().getMethod("add", toClass).invoke(queue, options);
                    } catch (Exception ex) {
                        log.warning("Failed to re-add TaskLeaseEntity - " + value + ": " + ex);
                    }
                }
            }
        }
    }

    @Override
    public void clear() {
        classLoader = null;
        super.clear();
    }
}
