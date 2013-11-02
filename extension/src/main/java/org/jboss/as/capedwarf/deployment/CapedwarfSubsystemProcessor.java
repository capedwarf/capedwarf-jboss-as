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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jboss.as.capedwarf.subsystems.CompatibilitySubsystemHook;
import org.jboss.as.capedwarf.subsystems.SubsystemHook;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;

/**
 * Handle subsystems per deployment.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfSubsystemProcessor extends CapedwarfDeploymentUnitProcessor {
    private static final Set<String> EXCLUDED_SUBSYSTEMS;
    private static final List<SubsystemHook> SUBSYSTEM_HOOKS;

    static {
        EXCLUDED_SUBSYSTEMS = new HashSet<>();
        EXCLUDED_SUBSYSTEMS.add(SubsystemHook.JAXRS);

        SUBSYSTEM_HOOKS = new ArrayList<>();
        SUBSYSTEM_HOOKS.add(new CompatibilitySubsystemHook());
    }

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        Set<String> enabledSubsystems = new HashSet<>();
        Set<String> disabledSubsystems = new HashSet<>();
        applySubsystemHooks(unit, enabledSubsystems, disabledSubsystems);

        Set<String> excludedSubsystems = new HashSet<>(EXCLUDED_SUBSYSTEMS);
        excludedSubsystems.addAll(disabledSubsystems);
        excludedSubsystems.removeAll(enabledSubsystems);

        if (excludedSubsystems.size() > 0) {
            Set<String> subsystems = unit.getAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
            if (subsystems == null) {
                subsystems = new ConcurrentSkipListSet<>();
                unit.putAttachment(Attachments.EXCLUDED_SUBSYSTEMS, subsystems);
            }
            subsystems.addAll(excludedSubsystems);
        }
    }

    protected void applySubsystemHooks(DeploymentUnit unit, Set<String> enabled, Set<String> disabled) throws DeploymentUnitProcessingException {
        try {
            for (SubsystemHook hook : SUBSYSTEM_HOOKS) {
                hook.apply(unit, enabled, disabled);
            }
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }
}
