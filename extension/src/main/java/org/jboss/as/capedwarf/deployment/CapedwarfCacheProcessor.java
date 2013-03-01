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

package org.jboss.as.capedwarf.deployment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.as.capedwarf.services.BasicConfigurationCallback;
import org.jboss.as.capedwarf.services.CacheLifecycleService;
import org.jboss.as.capedwarf.services.CacheName;
import org.jboss.as.capedwarf.services.ConfigurationCallback;
import org.jboss.as.capedwarf.services.DefaultConfigurationCallback;
import org.jboss.as.capedwarf.services.IndexableConfigurationCallback;
import org.jboss.as.capedwarf.services.MuxIdGenerator;
import org.jboss.as.clustering.infinispan.subsystem.CacheConfigurationService;
import org.jboss.as.clustering.infinispan.subsystem.EmbeddedCacheManagerService;
import org.jboss.as.clustering.jgroups.subsystem.ChannelService;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jgroups.JChannel;

/**
 * Handle CapeDwarf caches.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfCacheProcessor extends CapedwarfDeploymentUnitProcessor {

    private static final ServiceName CLS_SERVICE_NAME = CAPEDWARF_SERVICE_NAME.append("cache-lifecycle");
    private static final ServiceName CACHE_CONTAINER = EmbeddedCacheManagerService.getServiceName(CAPEDWARF);

    private static ServiceName toServiceName(String appId, CacheName cn) {
        return CLS_SERVICE_NAME.append(cn.getName()).append(appId);
    }

    static Set<ServiceName> getDependencies(String appId) {
        final Set<ServiceName> set = new HashSet<ServiceName>();
        for (CacheName cn : CacheName.values()) {
            set.add(toServiceName(appId, cn));
        }
        return set;
    }

    protected void doDeploy(DeploymentPhaseContext context) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = context.getDeploymentUnit();
        final String appId = CapedwarfDeploymentMarker.getAppId(unit);
        final ClassLoader classLoader = unit.getAttachment(Attachments.MODULE).getClassLoader();

        final ServiceTarget serviceTarget = context.getServiceTarget();
        // default
        createBuilder(serviceTarget, CacheName.DEFAULT, appId, new DefaultConfigurationCallback(appId, classLoader));
        // search, ps cache
        for (CacheName cn : Arrays.asList(CacheName.SEARCH, CacheName.PROSPECTIVE_SEARCH)) {
            final ConfigurationCallback callback = new BasicConfigurationCallback(cn, appId, classLoader);
            createBuilder(serviceTarget, cn, appId, callback);
        }
        // data, metadata, memcache, dist
        for (CacheName cn : Arrays.asList(CacheName.DATA, CacheName.METADATA, CacheName.MEMCACHE, CacheName.DIST)) {
            createBuilder(serviceTarget, cn, appId, null);
        }
        // tasks cache
        final ConfigurationCallback tasksCallback = new BasicConfigurationCallback(CacheName.TASKS, appId, classLoader);
        createBuilder(serviceTarget, CacheName.TASKS, appId, tasksCallback);
    }

    protected ServiceController<Cache> createBuilder(ServiceTarget serviceTarget, CacheName cacheName, String appId, ConfigurationCallback callback) {
        final CacheLifecycleService cls = new CacheLifecycleService(cacheName.getName(), appId, callback);
        final ServiceBuilder<Cache> builder = serviceTarget.addService(toServiceName(appId, cacheName), cls);
        if (callback instanceof IndexableConfigurationCallback) {
            IndexableConfigurationCallback icb = (IndexableConfigurationCallback) callback;
            builder.addDependency(ChannelService.getServiceName(CAPEDWARF), JChannel.class, icb.getChannel());
            builder.addDependency(CapedwarfMuxIdProcessor.toServiceName(appId), MuxIdGenerator.class, icb.getGenerator());
        }
        builder.addDependency(CACHE_CONTAINER, EmbeddedCacheManager.class, cls.getEcmiv());
        builder.addDependency(CacheConfigurationService.getServiceName(CAPEDWARF, cacheName.getName()), Configuration.class, cls.getCiv());
        builder.setInitialMode(ServiceController.Mode.ACTIVE);
        return builder.install();
    }
}
