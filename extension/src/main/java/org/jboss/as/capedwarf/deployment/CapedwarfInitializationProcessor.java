/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.capedwarf.shared.util.ParseUtils;
import org.jboss.logging.Logger;
import org.jboss.vfs.VirtualFile;

/**
 * Recognize Capedwarf deployments.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfInitializationProcessor implements DeploymentUnitProcessor {
    private final Logger log = Logger.getLogger(CapedwarfInitializationProcessor.class);

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        if (unit.getParent() == null) {
            // allow top level .ear and .war with proper xmls
            if (DeploymentTypeMarker.isType(DeploymentType.EAR, unit)) {
                if (hasAppEngineXml(unit, ParseUtils.APPENGINE_APPLICATION_XML)) {
                    log.info("Found GAE / CapeDwarf EAR deployment: " + unit);
                    CapedwarfDeploymentMarker.mark(unit);
                    CapedwarfDeploymentMarker.setDeploymentType(unit, DeploymentType.EAR);
                }
            } else if (DeploymentTypeMarker.isType(DeploymentType.WAR, unit)) {
                handleWarDeployment(unit);
            }
        } else {
            if (DeploymentTypeMarker.isType(DeploymentType.WAR, unit)) {
                handleWarDeployment(unit);
            }
        }
    }

    protected void handleWarDeployment(DeploymentUnit unit) {
        if (hasAppEngineXml(unit, ParseUtils.APPENGINE_WEB_XML)) {
            log.info("Found GAE / CapeDwarf WAR deployment: " + unit);
            CapedwarfDeploymentMarker.mark(unit);
            CapedwarfDeploymentMarker.setDeploymentType(unit, DeploymentType.WAR);
        }
    }

    protected boolean hasAppEngineXml(DeploymentUnit deploymentUnit, String path) {
        ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();
        VirtualFile xml = root.getChild(path);
        return xml.exists();
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }

}
