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

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStopped;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStoppedEvent;

/**
 * Default configuration callback.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class DefaultConfigurationCallback extends BasicConfigurationCallback {
    private static final StatsListener LISTENER = new StatsListener();

    public DefaultConfigurationCallback(String appId, ClassLoader classLoader) {
        super(CacheName.DEFAULT, appId, classLoader);
    }

    @Override
    public void start(EmbeddedCacheManager manager) {
        if (manager.getListeners().contains(LISTENER) == false) {
            manager.addListener(LISTENER);
        }
        LISTENER.counter++;
    }

    @Override
    public void stop(EmbeddedCacheManager manager) {
        if (--LISTENER.counter == 0) {
            manager.removeListener(LISTENER);
        }
    }

    @Listener
    public static class StatsListener {
        volatile int counter;

        @CacheStopped
        public void onCacheStopped(CacheStoppedEvent event) {
            String cacheName = event.getCacheName();
            if (cacheName.startsWith("default_")) {
                Cache cache = event.getCacheManager().getCache(cacheName, false);
                AdvancedCache ac = cache.getAdvancedCache();
                Object listener = null;
                for (Object l : ac.getListeners()) {
                    // impl detail!
                    if (l.getClass().getName().equals("org.jboss.capedwarf.datastore.query.EagerStatsQueryHandle$EagerListener")) {
                        listener = l;
                        break;
                    }
                }
                if (listener != null) {
                    ac.removeListener(listener);
                }
            }
        }
    }
}
