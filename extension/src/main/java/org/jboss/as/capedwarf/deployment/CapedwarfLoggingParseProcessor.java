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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;

import org.jboss.as.capedwarf.utils.Constants;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.deployments.LoggingConfigDeploymentProcessor;
import org.jboss.as.logging.logmanager.WildFlyLogContextSelector;
import org.jboss.as.logging.stdio.LogContextStdioContextSelector;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.capedwarf.shared.util.ParseUtils;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.logmanager.handlers.AsyncHandler;
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
    private static final String ASYNC = "ASYNC";

    private static final AttachmentKey<PropertyConfigurator> PROPERTY_CONFIGURATOR_KEY = AttachmentKey.create(PropertyConfigurator.class);

    private final static Set<String> excludedLoggers = new HashSet<>();

    static {
        excludedLoggers.add("org.jboss.capedwarf");
        excludedLoggers.add("org.jboss.as");
        excludedLoggers.add("org.jboss.modules");
        excludedLoggers.add("org.jboss.vfs");
        excludedLoggers.add("org.apache.jasper");
        excludedLoggers.add("org.apache.lucene");
        excludedLoggers.add("org.apache.velocity");
        excludedLoggers.add("org.hibernate.search");
        excludedLoggers.add("org.hornetq");
        excludedLoggers.add("org.infinispan");
        excludedLoggers.add("org.javassist");
        excludedLoggers.add("org.jgroups");
        excludedLoggers.add("org.picketbox");
        excludedLoggers.add("org.picketlink");
        excludedLoggers.add("io.undertow");
    }

    private WildFlyLogContextSelector contextSelector;

    protected synchronized WildFlyLogContextSelector getContextSelector() {
        if (contextSelector == null) {
            try {
                Class<LoggingExtension> cle = LoggingExtension.class;
                Field cs = cle.getDeclaredField("CONTEXT_SELECTOR");
                cs.setAccessible(true);
                contextSelector = (WildFlyLogContextSelector) cs.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return contextSelector;
    }

    protected void doParseAppEngineWebXml(DeploymentPhaseContext context, DeploymentUnit unit, VirtualFile root, VirtualFile xml) throws Exception {
        if (getCompatibility(unit).isEnabled(Compatibility.Feature.WILDFLY_LOGGING)) {
            return;
        }

        final Module module = unit.getAttachment(Attachments.MODULE);
        if (module != null) {
            // async file marker
            final VirtualFile async = root.getChild("WEB-INF/logging-async.properties");
            final boolean asyncExists = async.exists();
            // Always set this - for CapeDwarf subsystem to control logging,
            // as there might be logging config files, but no sys property
            final Properties fixed = new Properties();
            final String capedwarfLogger = Constants.CAPEDWARF.toUpperCase();
            // Add the capedwarf handler to the root logger
            if (asyncExists) {
                fixed.put("logger.handlers", ASYNC + "," + capedwarfLogger);
                defineHandler(fixed, ASYNC, AsyncHandler.class, "org.jboss.logmanager");
                // additional async config
                Properties ap = loadLoggingProperties(async);
                addHandlerProperty(fixed, ASYNC, "properties", ap.getProperty("properties", "enabled,queueLength,overflowAction"));
                addHandlerProperty(fixed, ASYNC, "constructorProperties", ap.getProperty("constructorProperties", "queueLength"));
                addHandlerProperty(fixed, ASYNC, "enabled", ap.getProperty("enabled", "true"));
                addHandlerProperty(fixed, ASYNC, "queueLength", ap.getProperty("queueLength", "1024"));
                addHandlerProperty(fixed, ASYNC, "overflowAction", ap.getProperty("overflowAction", "BLOCK"));
            } else {
                // define root handlers
                fixed.put("logger.handlers", capedwarfLogger);
            }
            defineHandler(fixed, capedwarfLogger, org.jboss.capedwarf.shared.log.Logger.class, "org.jboss.capedwarf.shared");
            // exclude AS7, CapeDwarf internals
            addExcludedLoggers(fixed);

            boolean loggingPropertiesImported = false;
            // check for more fine-grained logging config
            final String path = parseLoggingConfigPath(xml);
            if (path.length() > 0) {
                VirtualFile loggingProperties = root.getChild(path);
                if (loggingProperties.exists()) {
                    importLoggingProperties(fixed, loggingProperties);
                    loggingPropertiesImported = true;
                } else {
                    log.warn("No such logging config file exists: " + path);
                }
            }

            if (!loggingPropertiesImported) {
                fixed.setProperty(LOGGER + ".level", "INFO");
            }

            addListOfLoggers(fixed);

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
            addHandlers(logContext, asyncExists, ConsoleHandler.class, FileHandler.class);
            // register log context
            getContextSelector().registerLogContext(module.getClassLoader(), logContext);
            // Add as attachment / marker
            unit.putAttachment(LoggingConfigDeploymentProcessor.LOG_CONTEXT_KEY, logContext);
        }
    }

    private void importLoggingProperties(Properties fixed, VirtualFile config) throws IOException {
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
    }

    protected void addHandlerProperty(Properties fixed, String handler, String key, String value) {
        fixed.put(getPropertyKey("handler", handler, key), value);
    }

    protected void defineHandler(Properties fixed, String logger, Class<? extends Handler> handlerClass, String module) {
        // Configure the handler
        fixed.put(getPropertyKey("handler", logger), handlerClass.getName());
        addHandlerProperty(fixed, logger, "module", module);
        addHandlerProperty(fixed, logger, "level", "ALL");
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

    @SuppressWarnings("unchecked")
    protected void addHandlers(final LogContext logContext, boolean async, final Class<? extends Handler>... handlerTypes) {
        final LogContext sysLogContext = LogContext.getSystemLogContext();
        final Logger systemRoot = sysLogContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME);
        final Handler[] handlers = systemRoot.getHandlers();
        if (handlers != null && handlers.length > 0) {
            final Logger currentRoot = (async ? logContext.getLogger(ASYNC) : logContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME));
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
            ParseUtils.safeClose(stream);
        }
        return properties;
    }

    protected static void addExcludedLoggers(Properties properties) {
        for (String logger : excludedLoggers) {
            properties.put(getPropertyKey(LOGGER, logger, "level"), "OFF");
        }
    }

    private void addListOfLoggers(Properties properties) {
        StringBuilder builder = new StringBuilder();
        Enumeration<?> enumeration = properties.propertyNames();
        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            if (key.startsWith(LOGGER_DOT)) {
                int lastDotIndex = key.lastIndexOf('.');
                if (lastDotIndex > LOGGER_DOT.length()) {
                    String loggerName = key.substring(LOGGER_DOT.length(), lastDotIndex);
                    if (builder.length() > 0) {
                        builder.append(",");
                    }
                    builder.append(loggerName);
                }
            }
        }
        if (builder.length() > 0) {
            properties.put("loggers", builder.toString());
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
            ParseUtils.safeClose(is);
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
