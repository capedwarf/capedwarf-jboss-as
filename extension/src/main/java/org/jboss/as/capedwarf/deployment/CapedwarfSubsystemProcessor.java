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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.vfs.VirtualFile;

/**
 * Handle subsystems per deployment.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfSubsystemProcessor extends CapedwarfDeploymentUnitProcessor {
    private static final Set<String> EXCLUDED_SUBSYSTEMS;

    static {
        EXCLUDED_SUBSYSTEMS = new HashSet<>();
//        EXCLUDED_SUBSYSTEMS.add("jaxrs"); // exclude REST for now
    }

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        Set<String> excludedSubsystems = new HashSet<>(EXCLUDED_SUBSYSTEMS);
        excludedSubsystems.removeAll(getEnabledSubsystems(unit));

        if (excludedSubsystems.size() > 0) {
            Set<String> subsystems = unit.getAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
            if (subsystems == null) {
                subsystems = new ConcurrentSkipListSet<>();
                unit.putAttachment(Attachments.EXCLUDED_SUBSYSTEMS, subsystems);
            }
            subsystems.addAll(excludedSubsystems);
        }
    }

    protected Set<String> getEnabledSubsystems(DeploymentUnit unit) throws DeploymentUnitProcessingException {
        try {
            ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
            VirtualFile root = deploymentRoot.getRoot();
            Compatibility compatibility = LibUtils.getCompatibility(root);
            if (compatibility != null) {
                String value = compatibility.getValue(Compatibility.Feature.ENABLED_SUBSYSTEMS);
                if (value != null) {
                    String[] split = value.split(",");
                    return new HashSet<>(Arrays.asList(split));
                }
            }
            return Collections.emptySet();
        } catch (IOException e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }
}
