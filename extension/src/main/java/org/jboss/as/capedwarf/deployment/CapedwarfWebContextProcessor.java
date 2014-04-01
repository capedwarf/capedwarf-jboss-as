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

import java.util.Map;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.SetupAction;
import org.jboss.capedwarf.shared.config.ApplicationConfiguration;
import org.jboss.capedwarf.shared.config.ConfigurationAware;
import org.jboss.capedwarf.shared.reflection.MethodInvocation;
import org.jboss.capedwarf.shared.reflection.ReflectionUtils;
import org.jboss.capedwarf.shared.util.Utils;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceName;

/**
 * Prepare web context handles.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfWebContextProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module == null) {
            throw new DeploymentUnitProcessingException("No CL module: " + unit);
        }
        final ClassLoader classLoader = module.getClassLoader();
        final ClassLoader previous = SecurityActions.setTCCL(classLoader);
        try {
            final String appId = CapedwarfDeploymentMarker.getAppId(unit);
            final Set<ServiceName> dependencies = CapedwarfDependenciesProcessor.getDependecies(appId);
            final CapedwarfSetupAction cas = new CapedwarfSetupAction(dependencies, classLoader, unit.getAttachment(CapedwarfAttachments.APPLICATION_CONFIGURATION));
            unit.addToAttachmentList(org.jboss.as.ee.component.Attachments.WEB_SETUP_ACTIONS, cas);
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        } finally {
            SecurityActions.setTCCL(previous);
        }
    }

    private static class CapedwarfSetupAction extends ConfigurationAware implements SetupAction {
        private final Set<ServiceName> dependencies;
        private final MethodInvocation<Void> setup;
        private final MethodInvocation<Boolean> isSetup;
        private final MethodInvocation<Void> teardown;

        private CapedwarfSetupAction(Set<ServiceName> dependencies, ClassLoader appCL, ApplicationConfiguration applicationConfiguration) throws Exception {
            super(applicationConfiguration);
            this.dependencies = dependencies;
            Class<?> clazz = appCL.loadClass("org.jboss.capedwarf.appidentity.GAEListener");
            this.setup = ReflectionUtils.cacheMethod(clazz, "setup");
            this.isSetup = ReflectionUtils.cacheMethod(clazz, "isSetup");
            this.teardown = ReflectionUtils.cacheMethod(clazz, "teardown");
        }

        public void setup(Map<String, Object> properties) {
            ConfigurationAware.setApplicationConfiguration(applicationConfiguration);
            try {
                setup.invoke();
            } catch (Throwable t) {
                reset();
                throw Utils.toRuntimeException(t);
            }
        }

        public void teardown(Map<String, Object> properties) {
            try {
                if (isContextSetup()) {
                    teardown.invoke();
                }
            } finally {
                reset();
            }
        }

        private void reset() {
            ConfigurationAware.setApplicationConfiguration(null);
        }

        public int priority() {
            return Integer.MAX_VALUE - 1;
        }

        public Set<ServiceName> dependencies() {
            return dependencies;
        }

        protected boolean isContextSetup() {
            return isSetup.invoke();
        }
    }
}
