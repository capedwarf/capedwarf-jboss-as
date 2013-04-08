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

import java.util.Set;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.SetKey;
import org.jboss.capedwarf.shared.components.SimpleKey;
import org.jboss.capedwarf.shared.components.Slot;

/**
 * Datastore cache configuration callback.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class DatastoreConfigurationCallback extends BasicConfigurationCallback {
    public DatastoreConfigurationCallback(String appId, ClassLoader classLoader) {
        super(CacheName.DEFAULT, appId, classLoader);
    }

    public ConfigurationBuilder configure(Configuration configuration) {
        ConfigurationBuilder builder = super.configure(configuration);

        ComponentRegistry registry = ComponentRegistry.getInstance();
        Compatibility compatibility = registry.getComponent(new SimpleKey<Compatibility>(appId, Compatibility.class));
        if (compatibility.isEnabled(Compatibility.Feature.FORCE_ASYNC_DATASTORE)) {
            Set<String> callers = registry.getComponent(new SetKey<String>(appId, Slot.SYNC_HACK));
            if (callers == null || callers.isEmpty()) {
                log.info("Forcing async cache mode -- compatibility setting.");
                builder.clustering().cacheMode(CacheMode.DIST_ASYNC);
            } else {
                log.warning("Ignoring async force, found callers: " + callers);
            }
        }

        return builder;
    }
}
