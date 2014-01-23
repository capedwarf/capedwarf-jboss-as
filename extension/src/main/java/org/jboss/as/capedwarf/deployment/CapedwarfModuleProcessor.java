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

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.servlet.CapedwarfApiProxy;
import org.jboss.modules.Module;

/**
 * Setup any module / classloader related things.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfModuleProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            final ClassLoader classLoader = module.getClassLoader();
            CapedwarfApiProxy.initialize(classLoader, CapedwarfDeploymentMarker.getAppId(unit), CapedwarfDeploymentMarker.getModule(unit));
        }
    }

    protected void doUndeploy(DeploymentUnit unit) {
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            final ClassLoader classLoader = module.getClassLoader();
            CapedwarfApiProxy.destroy(classLoader);
        }
    }
}
