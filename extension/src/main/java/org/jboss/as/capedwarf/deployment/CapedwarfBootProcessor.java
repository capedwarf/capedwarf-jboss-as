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

import org.jboss.as.capedwarf.services.ServletExecutorConsumerService;
import org.jboss.as.capedwarf.services.WarmupService;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentCompleteServiceProcessor;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.config.AppEngineWebXml;
import org.jboss.capedwarf.shared.config.ApplicationConfiguration;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Boot war modules.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfBootProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        ApplicationConfiguration configuration = unit.getAttachment(CapedwarfAttachments.APPLICATION_CONFIGURATION);
        AppEngineWebXml appEngineWebXml = configuration.getAppEngineWebXml();
        if (appEngineWebXml.isWarmupRequests()) {
            final Module module = unit.getAttachment(Attachments.MODULE);
            ServiceTarget target = phaseContext.getServiceTarget();
            ServiceName serviceName = CAPEDWARF_SERVICE_NAME.append("warmup").append(appEngineWebXml.getApplication());
            ServiceBuilder<Void> builder = target.addService(serviceName, new WarmupService(module));
            builder.addDependency(ServletExecutorConsumerService.NAME);
            builder.addDependency(DeploymentCompleteServiceProcessor.serviceName(unit.getServiceName()));
            builder.setInitialMode(ServiceController.Mode.ACTIVE).install();
        }
    }
}
