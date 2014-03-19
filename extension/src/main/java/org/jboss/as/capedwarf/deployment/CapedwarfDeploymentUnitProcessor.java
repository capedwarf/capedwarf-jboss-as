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

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.SimpleKey;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;

/**
 * Capedwarf deployment unit processor.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class CapedwarfDeploymentUnitProcessor implements DeploymentUnitProcessor {

    protected static final String CAPEDWARF = "capedwarf";
    protected static final ServiceName CAPEDWARF_SERVICE_NAME = ServiceName.JBOSS.append(CAPEDWARF);
    protected final Logger log = Logger.getLogger(getClass());

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        if (CapedwarfDeploymentMarker.isCapedwarfDeployment(unit) && proceed(unit)) {
            doDeploy(phaseContext); // only handle CapeDwarf deployments
        }
    }

    protected boolean proceed(DeploymentUnit unit) {
        return (unit != null);
    }

    /**
     * Do deploy a Capedwarf deployment.
     *
     * @param phaseContext the phase context
     * @throws org.jboss.as.server.deployment.DeploymentUnitProcessingException for any deployment error
     */
    protected abstract void doDeploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException;

    public void undeploy(DeploymentUnit context) {
        if (CapedwarfDeploymentMarker.isCapedwarfDeployment(context) && proceed(context)) {
            doUndeploy(context);
        }
    }

    protected void doUndeploy(DeploymentUnit unit) {
    }

    protected static Compatibility getCompatibility(DeploymentUnit unit) {
        final String appId = CapedwarfDeploymentMarker.getAppId(unit);
        final String module = CapedwarfDeploymentMarker.getModule(unit);
        final Key<Compatibility> key = new SimpleKey<>(appId, module, Compatibility.class);
        return ComponentRegistry.getInstance().getComponent(key);
    }
}
