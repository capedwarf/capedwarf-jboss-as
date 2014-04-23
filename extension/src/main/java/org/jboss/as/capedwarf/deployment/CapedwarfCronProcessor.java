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

import org.jboss.as.capedwarf.services.CacheName;
import org.jboss.as.capedwarf.services.CronService;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.singleton.SingletonServiceBuilderFactory;
import org.wildfly.clustering.singleton.election.SimpleSingletonElectionPolicy;

/**
 * Add cron HA service.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfCronProcessor extends CapedwarfTopDeploymentUnitProcessor {
    private static final ServiceName CRON_SERVICE_NAME_PREFIX = CAPEDWARF_SERVICE_NAME.append("cron");

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final String appId = CapedwarfDeploymentMarker.getAppId(unit);

        final ModuleIdentifier mi = unit.getAttachment(Attachments.MODULE).getIdentifier();
        final ServiceName name = getDependecy(appId);
        final CronService service = new CronService(mi, unit.getAttachment(CapedwarfAttachments.APPLICATION_CONFIGURATION));
        final ServiceRegistry registry = phaseContext.getServiceRegistry();
        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();

        final ServiceName factoryServiceName = SingletonServiceBuilderFactory.SERVICE_NAME.append(CAPEDWARF, CacheName.DIST.getName());
        final ServiceController<?> factoryService = registry.getRequiredService(factoryServiceName);

        final SingletonServiceBuilderFactory factory = (SingletonServiceBuilderFactory) factoryService.getValue();
        factory.createSingletonServiceBuilder(name, service)
            .electionPolicy(new SimpleSingletonElectionPolicy())
            .requireQuorum(2)
            .build(serviceTarget)
            .addDependency(Services.JBOSS_SERVICE_MODULE_LOADER, ModuleLoader.class, service.getLoader())
            .install();
    }

    static ServiceName getDependecy(String appId) {
        return CRON_SERVICE_NAME_PREFIX.append(appId);
    }
}
