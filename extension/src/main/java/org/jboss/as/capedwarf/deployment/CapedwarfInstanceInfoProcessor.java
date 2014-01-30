/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.capedwarf.services.ServerInstanceInfo;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.modules.InstanceInfo;
import org.jboss.capedwarf.shared.modules.ModuleInfo;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.UndertowService;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfInstanceInfoProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final ServerInstanceInfo info = unit.getAttachment(CapedwarfAttachments.INSTANCE_INFO);
        if (info != null) {
            final String appId = CapedwarfDeploymentMarker.getAppId(unit);
            final String module = CapedwarfDeploymentMarker.getModule(unit);
            final ModuleInfo moduleInfo = ModuleInfo.getModuleInfo(appId, module);
            moduleInfo.addInstance(info);

            final ServiceTarget target = phaseContext.getServiceTarget();
            final ServiceBuilder<InstanceInfo> builder = target.addService(getServiceName(appId, module), info);
            builder.addDependency(UndertowService.UNDERTOW, UndertowService.class, info.getUndertowServiceInjectedValue());
            if (info.getServerName() != null) {
                builder.addDependency(UndertowService.SERVER.append(info.getServerName()), Server.class, info.getServerInjectedValue());
            }
            builder.setInitialMode(ServiceController.Mode.ACTIVE).install();
        }
    }

    private static ServiceName getServiceName(String appId, String module) {
        return CAPEDWARF_SERVICE_NAME.append("InstanceInfo").append(appId).append(module);
    }

    static Set<ServiceName> getDependencies(String appId) {
        Set<ServiceName> names = new HashSet<>();
        Map<String, ModuleInfo> modules = ModuleInfo.getModules(appId);
        for (String module : modules.keySet()) {
            names.add(getServiceName(appId, module));
        }
        return names;
    }
}
