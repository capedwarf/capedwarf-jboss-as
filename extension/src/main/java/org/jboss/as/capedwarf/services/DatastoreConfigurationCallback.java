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

import java.util.Collection;
import java.util.Set;

import org.hibernate.search.cfg.SearchMapping;
import org.hibernate.search.filter.ShardSensitiveOnlyFilter;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.IndexingConfigurationBuilder;
import org.infinispan.query.backend.SearchWorkCreator;
import org.infinispan.query.impl.ComponentRegistryUtils;
import org.jboss.as.capedwarf.CapedwarfIndexShardingStrategy;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.SetKey;
import org.jboss.capedwarf.shared.components.SimpleKey;
import org.jboss.capedwarf.shared.components.Slot;
import org.jboss.capedwarf.shared.config.IndexesXml;

/**
 * Datastore cache configuration callback.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class DatastoreConfigurationCallback extends BasicConfigurationCallback {

    private final Collection<IndexesXml.Index> indexes;

    public DatastoreConfigurationCallback(String appId, ClassLoader classLoader, IndexesXml indexesXml) {
        super(CacheName.DEFAULT, appId, classLoader);
        indexes = indexesXml.getIndexes().values();
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

    @Override
    protected SearchMapping applyIndexing(ConfigurationBuilder builder) {
        SearchMapping mapping = super.applyIndexing(builder);

        String infinispanIndexName = getIndexName("com.google.appengine.api.datastore.Entity");
        IndexingConfigurationBuilder indexing = builder.indexing();
        indexing.setProperty("hibernate.search." + infinispanIndexName + ".sharding_strategy", CapedwarfIndexShardingStrategy.class.getName());
        indexing.setProperty("hibernate.search." + infinispanIndexName + ".sharding_strategy.nbr_of_shards", String.valueOf(1 + indexes.size()));

        int i=1;
        for (IndexesXml.Index index : indexes) {
            mapping.fullTextFilterDef(index.getName(), ShardSensitiveOnlyFilter.class);
            indexing.setProperty("hibernate.search." + infinispanIndexName + "." + i + ".indexName", index.getName());
            indexing.setProperty("hibernate.search." + infinispanIndexName + ".sharding_strategy.index_name." + i, index.getName());
            i++;
        }

        return mapping;
    }

    @Override
    public void start(Cache cache) {
        super.start(cache);
        ComponentRegistryUtils.getQueryInterceptor(cache).setSearchWorkCreator(createSearchWorkCreator());
    }

    @SuppressWarnings("unchecked")
    private SearchWorkCreator<Object> createSearchWorkCreator() {
        try {
            Class<?> clazz = classLoader.loadClass("org.jboss.capedwarf.datastore.CapedwarfSearchWorkCreator");
            return (SearchWorkCreator<Object>) clazz.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
