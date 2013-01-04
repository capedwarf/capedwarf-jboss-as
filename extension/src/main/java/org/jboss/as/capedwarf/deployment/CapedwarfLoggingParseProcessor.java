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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jboss.as.capedwarf.api.Constants;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.LoggingDeploymentUnitProcessor;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.stdio.LogContextStdioContextSelector;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logmanager.ContextClassLoaderLogContextSelector;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.modules.Module;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.StdioContext;
import org.jboss.vfs.VirtualFile;

/**
 * Parse logging config.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfLoggingParseProcessor extends CapedwarfAppEngineWebXmlParseProcessor {
    private static final char DOT = '.';
    private static final String LOGGER = "logger";
    private static final String LOGGER_DOT = LOGGER + DOT;
    private static final String USE_PARENT_HANDLERS = ".useParentHandlers";
    private static final String LOGGING = "\"java.util.logging.config.file\"";

    // excluded loggers
    private final static Set<String> loggers = new HashSet<String>();
    static {
        loggers.add("org.jboss.capedwarf");
        loggers.add("org.jboss.as");
        loggers.add("org.jboss.modules");
        loggers.add("org.jboss.vfs");
        loggers.add("org.apache.lucene");
        loggers.add("org.apache.velocity");
        loggers.add("org.hibernate.search");
        loggers.add("org.hornetq");
        loggers.add("org.infinispan");
        loggers.add("org.javassist");
        loggers.add("org.jgroups");
        loggers.add("org.picketbox");
        loggers.add("org.picketlink");
    }

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

    protected void doParseAppEngineWebXml(DeploymentPhaseContext context, DeploymentUnit unit, VirtualFile root, VirtualFile xml) throws Exception {
        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            // Always set this - for CapeDwarf subsystem to control logging,
            // as there might be logging config files, but no sys property
            final Properties fixed = new Properties();
            // Add the capedwarf handler to the root logger
            final String capedwarfLogger = Constants.CAPEDWARF.toUpperCase();
            final String rootHandlersKey = "logger.handlers";
            // Just add the configuration to the fixed properties and let the PropertyConfigurator handle the rest
            if (fixed.contains(rootHandlersKey)) {
                fixed.put(rootHandlersKey, fixed.get(rootHandlersKey) + "," + capedwarfLogger);
            } else {
                fixed.put(rootHandlersKey, capedwarfLogger);
            }
            // Configure the capedwarf handler
            fixed.put(getPropertyKey("handler", capedwarfLogger), org.jboss.as.capedwarf.api.Logger.class.getName());
            fixed.put(getPropertyKey("handler", capedwarfLogger, "module"), "org.jboss.as.capedwarf");
            fixed.put(getPropertyKey("handler", capedwarfLogger, "level"), "ALL");
            // exclude AS7, CapeDwarf internals
            buildExcludedLoggers(fixed);
            // check for more fine-grained logging config
            final String path = parseLoggingConfigPath(xml);
            if (path.length() > 0) {
                VirtualFile config = root.getChild(path);
                if (config.exists()) {
                    final Properties properties = new Properties();
                    final InputStream stream = config.openStream();
                    try {
                        properties.load(stream);
                    } finally {
                        safeClose(stream);
                    }
                    if (properties.containsKey(USE_PARENT_HANDLERS) == false) {
                        fixed.setProperty(LOGGER + USE_PARENT_HANDLERS, Boolean.TRUE.toString());
                    }
                    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                        String key = entry.getKey().toString();
                        if (key.length() == 0) continue;
                        if (key.startsWith(LOGGER_DOT) == false) {
                            final StringBuilder builder = new StringBuilder(key);
                            if (builder.charAt(0) != DOT) {
                                builder.insert(0, LOGGER_DOT);
                            } else {
                                builder.insert(0, LOGGER);
                            }
                            key = builder.toString();
                        }
                        Object value = entry.getValue();
                        fixed.put(key, value);
                    }
                } else {
                    log.warn("No such logging config file exists: " + path);
                }
            }
            // Create a new log context for the deployment
            final LogContext logContext = LogContext.create();
            // Configure the logger
            new PropertyConfigurator(logContext).configure(fixed);
            // change stderr level
            applyStdioContext(logContext);
            // register log context
            getContextSelector().registerLogContext(module.getClassLoader(), logContext);
            // Add as attachment / marker
            unit.putAttachment(LoggingDeploymentUnitProcessor.LOG_CONTEXT_KEY, logContext);
        }
    }

    protected StdioContext applyStdioContext(final LogContext logContext) {
        final Logger root = logContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME);
        StdioContext stdioContext = root.getAttachment(LogContextStdioContextSelector.STDIO_CONTEXT_ATTACHMENT_KEY);
        if (stdioContext == null) {
            stdioContext = StdioContext.create(
                    new NullInputStream(),
                    new LoggingOutputStream(logContext.getLogger("stdout"), Level.INFO),
                    new LoggingOutputStream(logContext.getLogger("stderr"), Level.WARN)
            );
            final StdioContext appearing = root.attachIfAbsent(LogContextStdioContextSelector.STDIO_CONTEXT_ATTACHMENT_KEY, stdioContext);
            if (appearing != null) {
                stdioContext = appearing;
            }
        }
        return stdioContext;
    }

    protected static void buildExcludedLoggers(Properties fixed) {
        StringBuilder sb = new StringBuilder();
        for (String logger : loggers) {
            fixed.put(getPropertyKey(LOGGER, logger, "level"), "OFF");
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(logger);
        }
        fixed.put("loggers", sb.toString());
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

    private static String getPropertyKey(final String... keys) {
        final StringBuilder sb = new StringBuilder();
        int counter = 0;
        for (String key : keys) {
            sb.append(key);
            if (++counter < keys.length) sb.append(".");
        }
        return sb.toString();
    }
}
