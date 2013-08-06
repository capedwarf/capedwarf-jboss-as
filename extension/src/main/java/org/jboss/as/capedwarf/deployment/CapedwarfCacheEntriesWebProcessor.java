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

import java.util.Map;

import org.jboss.as.capedwarf.services.CacheConfig;
import org.jboss.as.capedwarf.services.CacheConfigs;
import org.jboss.as.capedwarf.services.CacheIndexing;
import org.jboss.as.capedwarf.services.CacheName;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;

/**
 * Define cache configs and its classes.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfCacheEntriesWebProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final ClassLoader cl = unit.getAttachment(Attachments.MODULE).getClassLoader();

        final Map<CacheName, CacheConfig> configs = getTopDeploymentUnit(unit).getAttachment(CapedwarfAttachments.CONFIGS);
        final boolean modular = CapedwarfDeploymentMarker.hasModules(unit);
        try {
            addExactClass(cl, configs, CacheName.DEFAULT, modular, "com.google.appengine.api.datastore.Entity");
            addExactClass(cl, configs, CacheName.SEARCH, modular, "org.jboss.capedwarf.search.CacheValue");
            addExactClass(cl, configs, CacheName.PROSPECTIVE_SEARCH, modular, "org.jboss.capedwarf.prospectivesearch.SubscriptionHolder");
            addExactClass(cl, configs, CacheName.TASKS, modular, "org.jboss.capedwarf.tasks.Task");
            addExactClass(cl, configs, CacheName.LOGS, modular, "org.jboss.capedwarf.log.CapedwarfAppLogLine", "org.jboss.capedwarf.log.CapedwarfRequestLogs");
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    private static void addExactClass(ClassLoader cl, Map<CacheName, CacheConfig> configs, CacheName cn, boolean modular, String... classes) throws Exception {
        CacheConfig config = configs.get(cn);

        if (modular) {
            CacheConfigs.storeAsBinary(config);
            CacheConfigs.defensive(config);
        }

        CacheIndexing indexing = config.getIndexing();
        for (String className : classes) {
            indexing.addExactClass(cl.loadClass(className));
        }
    }
}
