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

import org.jboss.capedwarf.shared.config.CacheName;

/**
 * Available caches in CapeDwarf.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CacheConfigs {
    private static class CacheConfigImpl implements CacheConfig {
        private CacheName cacheName;
        private CacheIndexing indexing;
        private boolean storeAsBinary;
        private boolean defensive;

        private CacheConfigImpl(CacheName cacheName, int prefix) {
            this.cacheName = cacheName;
            this.indexing = new CacheIndexing(prefix);
        }

        public String getName() {
            return cacheName.getName();
        }

        public CacheIndexing getIndexing() {
            return indexing;
        }

        public boolean storeAsBinary() {
            return storeAsBinary;
        }

        public boolean defensive() {
            return defensive;
        }
    }

    public static void storeAsBinary(CacheConfig config) {
        if (config instanceof CacheConfigImpl) {
            CacheConfigImpl.class.cast(config).storeAsBinary = true;
        }
    }

    public static void defensive(CacheConfig config) {
        if (config instanceof CacheConfigImpl) {
            CacheConfigImpl.class.cast(config).defensive = true;
        }
    }

    /**
     * DEFAULT = 3 * 1 - 1 = 2
     * SEARCH = 3 * (-1) + 1 = -2
     * PS = 3 * (-1) - 0 = -3
     * TASKS = 3 * 1 - 0 = 3
     * LOGS = 3 * 1 - 2 = 1
     * CHANNEL = 3 * (-1) + 2 = -1
     */

    public static CacheConfig createDefaultConfig() {
        CacheConfigImpl config = new CacheConfigImpl(CacheName.DEFAULT, 1);
        config.getIndexing().setOffset(-1);
        return config;
    }

    public static CacheConfig createSearchConfig() {
        CacheConfigImpl config = new CacheConfigImpl(CacheName.SEARCH, -1);
        config.getIndexing().setOffset(1);
        return config;
    }

    public static CacheConfig createProspectiveSearchConfig() {
        return new CacheConfigImpl(CacheName.PROSPECTIVE_SEARCH, -1);
    }

    public static CacheConfig createTasksConfig() {
        return new CacheConfigImpl(CacheName.TASKS, 1);
    }

    public static CacheConfig createLogsConfig() {
        CacheConfigImpl config = new CacheConfigImpl(CacheName.LOGS, 1);
        config.getIndexing().setOffset(-2);
        return config;
    }

    public static CacheConfig createChannelConfig() {
        CacheConfigImpl config = new CacheConfigImpl(CacheName.CHANNEL, -1);
        config.getIndexing().setOffset(2);
        return config;
    }

    public static CacheConfig createCacheConfig(CacheName cacheName) {
        return new CacheConfigImpl(cacheName, 0);
    }
}
