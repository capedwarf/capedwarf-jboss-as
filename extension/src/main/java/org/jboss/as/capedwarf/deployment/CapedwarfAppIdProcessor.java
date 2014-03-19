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

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.modules.ModuleInfo;

/**
 * Check app id.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfAppIdProcessor extends CapedwarfTopDeploymentUnitProcessor {
    private Set<String> apps = new ConcurrentSkipListSet<>();

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final String appId = CapedwarfDeploymentMarker.getAppId(phaseContext.getDeploymentUnit());

        if (appId == null || appId.length() == 0) {
            throw new IllegalArgumentException("App id is null or empty!");
        }

        if (apps.add(appId) == false) {
            throw new IllegalArgumentException("App id already exists: " + appId);
        }

        // put any per-app prepared components
        ModuleInfo.putModules(appId);

        log.info(String.format("Processed app id: %s", appId));
    }

    protected void doUndeploy(DeploymentUnit unit) {
        apps.remove(CapedwarfDeploymentMarker.getAppId(unit));
    }
}
