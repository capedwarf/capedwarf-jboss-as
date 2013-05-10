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

package org.jboss.as.capedwarf;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.lucene.document.Document;
import org.hibernate.search.filter.FullTextFilterImplementor;
import org.hibernate.search.indexes.spi.IndexManager;
import org.hibernate.search.store.IndexShardingStrategy;
import org.jboss.capedwarf.shared.datastore.DatastoreConstants;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class CapedwarfIndexShardingStrategy implements IndexShardingStrategy {


    private IndexManager[] indexManagers;
    private Map<String, IndexManager> indexManagerMap = new HashMap<>();

    @Override
    public void initialize(Properties properties, IndexManager[] providers) {
        this.indexManagers = providers;
        for (int i=1; i<providers.length; i++) {
            String indexName = properties.getProperty("index_name." + i);
            indexManagerMap.put(indexName, indexManagers[i]);
        }
    }

    @Override
    public IndexManager[] getIndexManagersForAllShards() {
        return indexManagers;
    }

    @Override
    public IndexManager getIndexManagerForAddition(Class<?> entity, Serializable id, String idInString, Document document) {
        return getIndexManager(extractIndexName(idInString));
    }

    @Override
    public IndexManager[] getIndexManagersForDeletion(Class<?> entity, Serializable id, String idInString) {
        return new IndexManager[] {getIndexManager(extractIndexName(idInString))};
    }

    @Override
    public IndexManager[] getIndexManagersForQuery(FullTextFilterImplementor[] fullTextFilters) {
        return new IndexManager[] {getIndexManager(determineIndexName(fullTextFilters))};
    }

    private String determineIndexName(FullTextFilterImplementor[] fullTextFilters) {
        if (fullTextFilters.length > 0) {
            return fullTextFilters[0].getName();
        } else {
            return null;
        }
    }

    private IndexManager getIndexManager(String indexName) {
        if (indexName == null) {
            return indexManagers[0];
        }
        IndexManager indexManager = indexManagerMap.get(indexName);
        if (indexManager == null) {
            throw new IllegalStateException("No IndexManager is associated with index named " + indexName);
        }
        return indexManager;
    }

    private String extractIndexName(String idInString) {
        int index = idInString.lastIndexOf(DatastoreConstants.SEPARATOR);
        if (index == -1) {
            return null;
        }
        return idInString.substring(index + DatastoreConstants.SEPARATOR.length());
    }
}

