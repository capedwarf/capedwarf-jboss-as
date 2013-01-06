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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.modules.Module;

/**
 * Setup system environment properties.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfEnvironmentProcessor extends CapedwarfDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            try {
                ClassLoader classLoader = module.getClassLoader();
                Class<?> spc = classLoader.loadClass("com.google.appengine.api.utils.SystemProperty");
                Method key = spc.getMethod("key");
                Method set = spc.getMethod("set", String.class);

                Field environmentField = spc.getField("environment");
                Field versionField = spc.getField("version");
                Field appIdField = spc.getField("applicationId");
                Field appVersionField = spc.getField("applicationVersion");

                String environment = System.getProperty((String)key.invoke(environmentField.get(null)), "Development");

                String version = CapedwarfDeploymentMarker.getVersion(unit);
                String appId = CapedwarfDeploymentMarker.getAppId(unit);
                String appVersion = CapedwarfDeploymentMarker.getAppVersion(unit);

                setValue(environmentField, set, environment);
                setValue(versionField, set, version);
                setValue(appIdField, set, appId);
                setValue(appVersionField, set, appVersion);
            } catch (Exception e) {
                throw new DeploymentUnitProcessingException(e);
            }
        }
    }

    private static void setValue(Field field, Method set, String value) throws Exception {
        Object property = field.get(null);
        set.invoke(property, value);
    }
}
