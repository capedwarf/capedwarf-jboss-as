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

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.VersioningScheme;
import org.infinispan.util.concurrent.IsolationLevel;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.capedwarf.shared.components.SimpleKey;

/**
 * Datastore versions cache configuration callback.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class DatastoreVersionsConfigurationCallback extends BasicConfigurationCallback {
    public DatastoreVersionsConfigurationCallback(String appId, ClassLoader classLoader) {
        super(CacheName.DEFAULT, appId, classLoader);
    }

    public ConfigurationBuilder configure(Configuration configuration) {
        ConfigurationBuilder builder = super.configure(configuration);
        Compatibility compatibility = Compatibility.getInstance(classLoader, new SimpleKey<Compatibility>(appId, Compatibility.class));
        if (compatibility.isEnabled(Compatibility.Feature.DISABLE_METADATA) == false) {
            builder.clustering().cacheMode(CacheMode.DIST_SYNC);
            builder.locking().isolationLevel(IsolationLevel.REPEATABLE_READ).writeSkewCheck(true);
            builder.versioning().enable().scheme(VersioningScheme.SIMPLE);
        }
        return builder;
    }
}
