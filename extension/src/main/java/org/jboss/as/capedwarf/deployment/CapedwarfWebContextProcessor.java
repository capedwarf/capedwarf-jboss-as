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

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.SetupAction;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceName;
import org.jboss.vfs.VirtualFile;

/**
 * Prepare web context handles.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfWebContextProcessor extends CapedwarfDeploymentUnitProcessor {

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final VirtualFile deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module == null) {
            throw new DeploymentUnitProcessingException("No CL module: " + unit);
        }
        final ClassLoader classLoader = module.getClassLoader();
        try {
            // appengine-web.xml
            final InputStream appIs = getInputStream(deploymentRoot, "WEB-INF/appengine-web.xml", true);
            Object appConfig = read(appIs, classLoader, "org.jboss.capedwarf.common.config.AppEngineWebXmlParser");

            // capedwarf-web.xml
            final InputStream cdIs = getInputStream(deploymentRoot, "WEB-INF/capedwarf-web.xml", false);
            Object cdConfig = read(cdIs, classLoader, "org.jboss.capedwarf.common.config.CapedwarfConfigurationParser");

            // queue.xml
            final InputStream queueIs = getInputStream(deploymentRoot, "WEB-INF/queue.xml", false);
            Object queueConfig = read(queueIs, classLoader, "org.jboss.capedwarf.common.config.QueueXmlParser");

            final CapedwarfSetupAction cas = new CapedwarfSetupAction(classLoader, appConfig, cdConfig, queueConfig);
            unit.addToAttachmentList(org.jboss.as.ee.component.Attachments.WEB_SETUP_ACTIONS, cas);
        } catch (DeploymentUnitProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    protected static InputStream getInputStream(VirtualFile root, String path, boolean mandatory) throws Exception {
        final VirtualFile child = root.getChild(path);
        if (child == null || child.exists() == false) {
            if (mandatory) {
                throw new DeploymentUnitProcessingException("No such file: " + path);
            } else {
                return null;
            }
        }
        return child.openStream();
    }

    protected static Object read(InputStream is, ClassLoader appCL, String parserClassName) throws Exception {
        Class<?> parserClass = appCL.loadClass(parserClassName);
        Method parse = parserClass.getDeclaredMethod("parse", InputStream.class);
        try {
            return parse.invoke(null, is);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private static class CapedwarfSetupAction implements SetupAction {
        private final ClassLoader appCL;
        private final Object appConfig;
        private final Object cdConfig;
        private final Object queueConfig;

        private CapedwarfSetupAction(ClassLoader appCL, Object appConfig, Object cdConfig, Object queueConfig) {
            this.appCL = appCL;
            this.appConfig = appConfig;
            this.cdConfig = cdConfig;
            this.queueConfig = queueConfig;
        }

        public void setup(Map<String, Object> properties) {
            if (isContextSetup()) {
                setTL(appCL, "setAppEngineWebXml", appConfig);
                setTL(appCL, "setCapedwarfConfiguration", cdConfig);
                setTL(appCL, "setQueueXml", queueConfig);

                invokeListener(appCL, "setup");
            }
        }

        public void teardown(Map<String, Object> properties) {
            if (isContextSetup()) {
                invokeListener(appCL, "teardown");

                resetTL(appCL, "setAppEngineWebXml");
                resetTL(appCL, "setCapedwarfConfiguration");
                resetTL(appCL, "setQueueXml");
            }
        }

        public int priority() {
            return Integer.MAX_VALUE - 1;
        }

        public Set<ServiceName> dependencies() {
            return Collections.emptySet();
        }

        protected static boolean isContextSetup() {
            StackTraceElement[] elements = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : elements) {
                if (element.getClassName().equals("org.apache.catalina.core.StandardContext"))
                    return true;
            }
            return false;
        }

        protected static void setTL(ClassLoader appCL, String method, Object value){
            try {
                Class<?> gaeListenerClass = appCL.loadClass("org.jboss.capedwarf.appidentity.GAEListener");
                Method m = gaeListenerClass.getDeclaredMethod(method, Object.class);
                m.invoke(null, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        protected static void resetTL(ClassLoader appCL, String method){
            setTL(appCL, method, null);
        }

        protected static void invokeListener(ClassLoader appCL, String method) {
            try {
                Class<?> gaeListenerClass = appCL.loadClass("org.jboss.capedwarf.appidentity.GAEListener");
                Method m = gaeListenerClass.getDeclaredMethod(method);
                m.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
