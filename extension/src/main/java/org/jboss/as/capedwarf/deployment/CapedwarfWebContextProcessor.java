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

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.SetupAction;
import org.jboss.capedwarf.shared.config.ApplicationConfiguration;
import org.jboss.capedwarf.shared.config.ConfigurationAware;
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
            ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration(
                unit.getAttachment(CapedwarfAttachments.APP_ENGINE_WEB_XML),
                unit.getAttachment(CapedwarfAttachments.CAPEDWARF_WEB_XML),
                unit.getAttachment(CapedwarfAttachments.QUEUE_XML),
                unit.getAttachment(CapedwarfAttachments.BACKENDS_XML),
                unit.getAttachment(CapedwarfAttachments.INDEXES_XML));

            final String appId = CapedwarfDeploymentMarker.getAppId(unit);
            final Set<ServiceName> dependencies = CapedwarfDependenciesProcessor.getDependecies(appId);
            final CapedwarfSetupAction cas = new CapedwarfSetupAction(dependencies, classLoader, applicationConfiguration);
            unit.addToAttachmentList(org.jboss.as.ee.component.Attachments.WEB_SETUP_ACTIONS, cas);
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        } finally {
            SecurityActions.setTCCL(previous);
        }
    }

    private static class CapedwarfSetupAction extends ConfigurationAware implements SetupAction {
        private final Set<ServiceName> dependencies;
        private final ClassLoader appCL;

        private CapedwarfSetupAction(Set<ServiceName> dependencies, ClassLoader appCL, ApplicationConfiguration applicationConfiguration) {
            super(applicationConfiguration);
            this.dependencies = dependencies;
            this.appCL = appCL;
        }

        public void setup(Map<String, Object> properties) {
            ConfigurationAware.setApplicationConfiguration(applicationConfiguration);

            try {
                invokeListener(appCL, "setup");
            } catch (RuntimeException e) {
                reset();
                throw e;
            }
        }

        public void teardown(Map<String, Object> properties) {
            try {
                if (isContextSetup()) {
                    invokeListener(appCL, "teardown");
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
            return (Boolean) invokeListener(appCL, "isSetup");
        }

        protected static Object invokeListener(ClassLoader appCL, String method) {
            return invokeListener(appCL, method, new Class[0], new Object[0]);
        }

        protected static Object invokeListener(ClassLoader appCL, String method, Class<?>[] types, Object[] args) {
            try {
                Class<?> gaeListenerClass = appCL.loadClass("org.jboss.capedwarf.appidentity.GAEListener");
                Method m = gaeListenerClass.getDeclaredMethod(method, types);
                return m.invoke(null, args);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }
}
