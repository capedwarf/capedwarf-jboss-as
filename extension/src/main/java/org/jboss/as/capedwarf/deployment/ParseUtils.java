/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.io.Closeable;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
final class ParseUtils {
    static final String APPENGINE_APPLICATION_XML = "META-INF/appengine-application.xml";
    static final String APPENGINE_WEB_XML = "WEB-INF/appengine-web.xml";

    static final String APPLICATION = "<application>";
    static final String VERSION = "<version>";
    static final String THREADSAFE = "<threadsafe>";
    static final String MODULE = "<module>";

    ParseUtils() {
    }

    static VirtualFile getFile(DeploymentPhaseContext context, String path) {
        return getFile(context.getDeploymentUnit(), path);
    }

    static VirtualFile getFile(DeploymentUnit unit, String path) {
        ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();
        return root.getChild(path);
    }

    static Map<String, String> parseTokens(VirtualFile xml, final Set<String> tokens) throws Exception {
        final Map<String, String> results = new HashMap<>();
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

    static void safeClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
