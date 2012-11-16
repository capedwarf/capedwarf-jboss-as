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

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Start / Stop cache.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CacheLifecycleService extends AbstractConfigurationCallback implements Service<Cache> {
    private final String cacheName;
    private final String appId;
    private final ConfigurationCallback callback;

    private final InjectedValue<EmbeddedCacheManager> ecmiv = new InjectedValue<EmbeddedCacheManager>();
    private final InjectedValue<Configuration> civ = new InjectedValue<Configuration>();

    private Cache cache;

    public CacheLifecycleService(String cacheName, String appId) {
        this(cacheName, appId, null);
    }

    public CacheLifecycleService(String cacheName, String appId, ConfigurationCallback callback) {
        this.cacheName = cacheName;
        this.appId = appId;
        this.callback = callback;
    }

    public void start(StartContext context) throws StartException {
        final EmbeddedCacheManager cacheManager = ecmiv.getValue();
        final String fullCacheName = cacheName + "_" + appId; // impl detail!

        final ConfigurationCallback cc = (callback != null) ? callback : this;

        cache = cacheManager.getCache(fullCacheName, false);
        if (cache != null) {
            final ComponentStatus status = cache.getStatus();
            if (status != ComponentStatus.INITIALIZING && status != ComponentStatus.RUNNING) {
                cc.start(cacheManager);
                cache.start(); // re-start stopped cache
                cc.start(cache);
            }
            return;
        }

        final ConfigurationBuilder builder = cc.configure(civ.getValue());
        cacheManager.defineConfiguration(fullCacheName, builder.build());

        cc.start(cacheManager);
        cache = cacheManager.getCache(fullCacheName, true);
        cc.start(cache);
    }

    public void stop(StopContext context) {
        final Cache tmp = cache;
        cache = null;
        if (tmp != null) {
            final ConfigurationCallback cc = (callback != null) ? callback : this;
            try {
                cc.stop(tmp);
            } finally {
                tmp.stop();
                cc.stop(ecmiv.getValue());
            }
        }
    }

    public Cache getValue() throws IllegalStateException, IllegalArgumentException {
        return cache;
    }

    public ConfigurationBuilder configure(Configuration configuration) {
        final ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.read(configuration);
        return builder;
    }

    public InjectedValue<EmbeddedCacheManager> getEcmiv() {
        return ecmiv;
    }

    public InjectedValue<Configuration> getCiv() {
        return civ;
    }
}
