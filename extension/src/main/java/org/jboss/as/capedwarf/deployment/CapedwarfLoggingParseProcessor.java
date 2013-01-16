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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;

import org.jboss.as.capedwarf.utils.Constants;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.LoggingDeploymentUnitProcessor;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.stdio.LogContextStdioContextSelector;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logmanager.ContextClassLoaderLogContextSelector;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.handlers.FileHandler;
import org.jboss.modules.Module;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.StdioContext;
import org.jboss.vfs.VirtualFile;

/**
 * Parse logging config.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class CapedwarfLoggingParseProcessor extends CapedwarfAppEngineWebXmlParseProcessor {
    private static final char DOT = '.';
    private static final String LOGGER = "logger";
    private static final String LOGGER_DOT = LOGGER + DOT;
    private static final String USE_PARENT_HANDLERS = ".useParentHandlers";
    private static final String LOGGING = "\"java.util.logging.config.file\"";
    private static final AttachmentKey<PropertyConfigurator> PROPERTY_CONFIGURATOR_KEY = AttachmentKey.create(PropertyConfigurator.class);

    private final static Set<String> excludedLoggers = new HashSet<String>();
    static {
        excludedLoggers.add("org.jboss.capedwarf");
        excludedLoggers.add("org.jboss.as");
        excludedLoggers.add("org.jboss.modules");
        excludedLoggers.add("org.jboss.vfs");
        excludedLoggers.add("org.apache.lucene");
        excludedLoggers.add("org.apache.velocity");
        excludedLoggers.add("org.hibernate.search");
        excludedLoggers.add("org.hornetq");
        excludedLoggers.add("org.infinispan");
        excludedLoggers.add("org.javassist");
        excludedLoggers.add("org.jgroups");
        excludedLoggers.add("org.picketbox");
        excludedLoggers.add("org.picketlink");
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
            fixed.put(getPropertyKey("handler", capedwarfLogger), org.jboss.capedwarf.shared.log.Logger.class.getName());
            fixed.put(getPropertyKey("handler", capedwarfLogger, "module"), "org.jboss.capedwarf.shared");
            fixed.put(getPropertyKey("handler", capedwarfLogger, "level"), "ALL");
            // exclude AS7, CapeDwarf internals
            buildExcludedLoggers(fixed);
            // check for more fine-grained logging config
            final String path = parseLoggingConfigPath(xml);
            if (path.length() > 0) {
                VirtualFile config = root.getChild(path);
                if (config.exists()) {
                    Properties properties = loadLoggingProperties(config);
                    if (properties.containsKey(USE_PARENT_HANDLERS) == false) {
                        fixed.setProperty(LOGGER + USE_PARENT_HANDLERS, Boolean.TRUE.toString());
                    }
                    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                        String key = entry.getKey().toString();
                        if (key.length() == 0) continue;
                        if (key.startsWith(LOGGER_DOT) == false) {
                            if (key.charAt(0) == DOT) {
                                key = LOGGER + key;
                            } else {
                                key = LOGGER_DOT + key;
                            }
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
            PropertyConfigurator propertyConfigurator = new PropertyConfigurator(logContext);
            propertyConfigurator.configure(fixed);
            // Must keep reference to the configurator somewhere, otherwise LoggerNodes will not be reconfigured when they are recreated
            unit.putAttachment(PROPERTY_CONFIGURATOR_KEY, propertyConfigurator);

            // change stderr level
            applyStdioContext(logContext);
            // add console, file handlers
            //noinspection unchecked
            addHandlers(logContext, ConsoleHandler.class, FileHandler.class);
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

    protected void addHandlers(final LogContext logContext, final Class<? extends Handler>... handlerTypes) {
        final LogContext sysLogContext = LogContext.getSystemLogContext();
        final Logger systemRoot = sysLogContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME);
        final Handler[] handlers = systemRoot.getHandlers();
        if (handlers != null && handlers.length > 0) {
            final Logger currentRoot = logContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME);
            for (Handler handler : handlers) {
                for (Class<? extends Handler> handlerType : handlerTypes) {
                    if (handlerType.isInstance(handler)) {
                        currentRoot.addHandler(handler);
                    }
                }
            }
        }
    }

    private Properties loadLoggingProperties(VirtualFile config) throws IOException {
        Properties properties = new Properties();
        InputStream stream = config.openStream();
        try {
            properties.load(stream);
        } finally {
            safeClose(stream);
        }
        return properties;
    }

    protected static void buildExcludedLoggers(Properties fixed) {
        StringBuilder sb = new StringBuilder();
        for (String logger : excludedLoggers) {
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
