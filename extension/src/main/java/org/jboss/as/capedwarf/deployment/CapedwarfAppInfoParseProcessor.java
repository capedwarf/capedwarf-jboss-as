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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.vfs.VirtualFile;

/**
 * Parse app info - id, version.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfAppInfoParseProcessor extends CapedwarfAppEngineWebXmlParseProcessor {
    private static final String APPLICATION = "<application>";
    private static final String VERSION = "<version>";
    private static final String THREADSAFE = "<threadsafe>";

    private Set<String> apps = new ConcurrentSkipListSet<String>();

    protected void doParseAppEngineWebXml(DeploymentPhaseContext context, DeploymentUnit unit, VirtualFile root, VirtualFile xml) throws Exception {
        final Map<String, String> results = parseTokens(xml, new LinkedHashSet<String>(Arrays.asList(APPLICATION, VERSION, THREADSAFE)));
        final String appId = results.get(APPLICATION);

        if (appId == null || appId.length() == 0)
            throw new IllegalArgumentException("App id is null or empty!");

        if (apps.add(appId) == false)
            throw new IllegalArgumentException("App id already exists: " + appId);

        CapedwarfDeploymentMarker.setAppId(unit, appId);
        CapedwarfDeploymentMarker.setAppVersion(unit, results.get(VERSION));
        CapedwarfDeploymentMarker.setThreadsafe(unit, Boolean.parseBoolean(results.get(THREADSAFE)));
    }

    private Map<String, String> parseTokens(VirtualFile xml, final Set<String> tokens) throws Exception {
        final Map<String, String> results = new HashMap<String, String>();
        InputStream is = xml.openStream();
        try {
            StringBuilder builder = new StringBuilder();
            int x;
            String token = null;
            StringBuilder tokenBuilder = new StringBuilder();
            while ((x = is.read()) != -1) {
                char ch = (char) x;
                if (token != null) {
                    if (ch == '<') {
                        results.put(token, tokenBuilder.toString());
                        if (tokens.isEmpty()) {
                            break;
                        } else {
                            token = null;
                            tokenBuilder.setLength(0); // reset builder
                        }
                    } else {
                        tokenBuilder.append(ch);
                    }
                } else {
                    builder.append(ch);
                }
                // check if we hit any token
                if (token == null) {
                    for (String t : tokens) {
                        if (builder.toString().endsWith(t)) {
                            token = t;
                            break;
                        }
                    }
                    if (token != null) {
                        tokens.remove(token);
                    }
                }
            }
            return results;
        } finally {
            safeClose(is);
        }
    }

    @Override
    protected void doUndeploy(DeploymentUnit unit) {
        String appId = CapedwarfDeploymentMarker.getAppId(unit);
        apps.remove(appId);
    }
}
