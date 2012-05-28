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
import java.lang.reflect.Field;

import org.jboss.as.logging.LoggingConfigurationProcessor;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logmanager.ContextClassLoaderLogContextSelector;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.modules.Module;
import org.jboss.vfs.VirtualFile;

/**
 * Parse logging config.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfLoggingParseProcessor extends CapedwarfAppEngineWebXmlParseProcessor {
    private static final String LOGGING = "\"java.util.logging.config.file\"";

    private ContextClassLoaderLogContextSelector contextSelector;

    protected synchronized ContextClassLoaderLogContextSelector getContextSelector() {
        if (contextSelector == null) {
            try {
                Class<LoggingExtension> cle = LoggingExtension.class;
                Field cs = cle.getDeclaredField("CONTEXT_SELECTOR");
                cs.setAccessible(true);
                contextSelector = (ContextClassLoaderLogContextSelector) cs.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return contextSelector;
    }

    protected void doParseAppEngineWebXml(DeploymentUnit unit, VirtualFile root, VirtualFile xml) throws Exception {
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            // Always set this - for CapeDwarf subsystem to control logging,
            // as there might be logging config files, but no sys property
            final LogContext logContext = LogContext.create();
            unit.putAttachment(LoggingConfigurationProcessor.LOG_CONTEXT_KEY, logContext);

            final String path = parseLoggingConfigPath(xml);
            if (path.length() > 0) {
                VirtualFile config = root.getChild(path);
                if (config.exists()) {
                    InputStream stream = config.openStream();
                    try {
                        new PropertyConfigurator(logContext).configure(stream);
                    } finally {
                        safeClose(stream);
                    }
                    getContextSelector().registerLogContext(module.getClassLoader(), logContext);
                } else {
                    log.warn("No such logging config file exists: " + path);
                }
            }
        }
    }

    protected String parseLoggingConfigPath(VirtualFile xml) throws Exception {
        InputStream is = xml.openStream();
        try {
            StringBuilder builder = new StringBuilder();
            int x;
            boolean hasLoggingConfig = false;
            int isLoggingConfig = 0;
            StringBuilder path = new StringBuilder();
            while ((x = is.read()) != -1) {
                char ch = (char) x;
                if (hasLoggingConfig) {
                    if (ch == '"') {
                        isLoggingConfig++;
                    } else if (isLoggingConfig == 1) {
                        path.append(ch);
                    } else if (isLoggingConfig > 1) {
                        break;
                    }
                } else {
                    builder.append(ch);
                }
                if (hasLoggingConfig == false && builder.toString().endsWith(LOGGING)) {
                    hasLoggingConfig = true;
                }
            }
            return path.toString();
        } finally {
            safeClose(is);
        }
    }
}
