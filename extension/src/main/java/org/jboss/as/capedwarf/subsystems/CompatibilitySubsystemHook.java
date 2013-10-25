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

package org.jboss.as.capedwarf.subsystems;

import java.util.Arrays;
import java.util.Set;

import org.jboss.as.capedwarf.deployment.CapedwarfDeploymentMarker;
import org.jboss.as.capedwarf.utils.LibUtils;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CompatibilitySubsystemHook implements SubsystemHook {
    public void apply(DeploymentUnit unit, Set<String> enabledSubsystems, Set<String> disabledSubsystems) throws Exception {
        ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();
        Compatibility compatibility = LibUtils.getCompatibility(CapedwarfDeploymentMarker.getDeploymentType(unit), root);
        addSystems(compatibility, Compatibility.Feature.ENABLED_SUBSYSTEMS, enabledSubsystems);
        addSystems(compatibility, Compatibility.Feature.DISABLED_SUBSYSTEMS, disabledSubsystems);
    }

    private static void addSystems(Compatibility compatibility, Compatibility.Feature feature, Set<String> subsystems) {
        if (compatibility != null) {
            String value = compatibility.getValue(feature);
            if (value != null) {
                String[] split = value.split(",");
                subsystems.addAll(Arrays.asList(split));
            }
        }
    }
}
