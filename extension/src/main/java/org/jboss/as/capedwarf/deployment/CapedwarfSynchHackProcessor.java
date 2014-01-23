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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.SetKey;
import org.jboss.capedwarf.shared.components.Slot;
import org.jboss.modules.Module;

/**
 * Check for sync callers classes.
 * <p/>
 * TODO -- configure from config / management
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfSynchHackProcessor extends CapedwarfWebDeploymentUnitProcessor {
    private static final String FORCE_SYNC = "jboss.capedwarf.forceSync";
    private static final Set<String> SYNC_CALLERS = new LinkedHashSet<>();

    static {
        SYNC_CALLERS.add("com.google.appengine.tools.pipeline.impl.backend.AppEngineBackEnd");
        String sysProp = System.getProperty(FORCE_SYNC);
        if (sysProp != null) {
            Collections.addAll(SYNC_CALLERS, sysProp.split(","));
        }
    }

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            final ClassLoader cl = module.getClassLoader();
            final Set<String> callers = new HashSet<>();
            for (String caller : SYNC_CALLERS) {
                try {
                    cl.loadClass(caller);
                    callers.add(caller);
                } catch (Exception ignore) {
                }
            }
            if (callers.size() > 0) {
                String appId = CapedwarfDeploymentMarker.getAppId(unit);
                String moduleId = CapedwarfDeploymentMarker.getModule(unit);
                Key<Set<String>> key = new SetKey<>(appId, moduleId, Slot.SYNC_HACK);
                ComponentRegistry.getInstance().setComponent(key, Collections.unmodifiableSet(callers));
            }
        }
    }
}
