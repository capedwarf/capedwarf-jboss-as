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

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.vfs.VirtualFile;

import static org.jboss.capedwarf.shared.util.ParseUtils.APPLICATION;
import static org.jboss.capedwarf.shared.util.ParseUtils.MODULE;
import static org.jboss.capedwarf.shared.util.ParseUtils.THREADSAFE;
import static org.jboss.capedwarf.shared.util.ParseUtils.VERSION;

/**
 * Parse WAR app info - id, version.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfWebAppInfoParseProcessor extends CapedwarfAppEngineWebXmlParseProcessor {
    protected void doParseAppEngineWebXml(DeploymentPhaseContext context, DeploymentUnit unit, VirtualFile root, VirtualFile xml) throws Exception {
        final Map<String, String> results = DeploymentParseUtils.parseTokens(xml, APPLICATION, VERSION, THREADSAFE, MODULE);

        final String appId;
        if (CapedwarfDeploymentMarker.hasModules(unit)) {
            appId = CapedwarfDeploymentMarker.getTopMarker(unit).getAppId();
        } else {
            appId = results.get(APPLICATION);
        }

        CapedwarfDeploymentMarker.setAppId(unit, appId);
        CapedwarfDeploymentMarker.setAppVersion(unit, results.get(VERSION));
        CapedwarfDeploymentMarker.setThreadsafe(unit, Boolean.parseBoolean(results.get(THREADSAFE)));
        String module = results.get(MODULE);
        CapedwarfDeploymentMarker.setModule(unit, module);
    }
}
