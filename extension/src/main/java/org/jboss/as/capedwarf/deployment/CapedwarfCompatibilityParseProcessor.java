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

import java.io.IOException;

import org.jboss.as.capedwarf.utils.LibUtils;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.SimpleKey;
import org.jboss.vfs.VirtualFile;

/**
 * Parse compatibility props.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfCompatibilityParseProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        try {
            DeploymentUnit unit = phaseContext.getDeploymentUnit();
            ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
            VirtualFile root = deploymentRoot.getRoot();

            DeploymentType type = CapedwarfDeploymentMarker.getDeploymentType(unit);
            VirtualFile cf = LibUtils.getCompatibilityFile(type, root);
            Compatibility compatibility = Compatibility.readCompatibility(cf.exists() ? cf.openStream() : null);
            String appId = CapedwarfDeploymentMarker.getAppId(unit);
            String moduleId = CapedwarfDeploymentMarker.getModule(unit);
            Key<Compatibility> key = new SimpleKey<>(appId, moduleId, Compatibility.class);
            ComponentRegistry.getInstance().setComponent(key, compatibility);
        } catch (IOException e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }
}
