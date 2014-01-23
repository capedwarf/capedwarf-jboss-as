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

package org.jboss.as.capedwarf.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.MapKey;
import org.jboss.capedwarf.shared.components.SimpleKey;
import org.jboss.capedwarf.shared.components.Slot;
import org.jboss.capedwarf.shared.jms.MessageConstants;
import org.jboss.capedwarf.shared.jms.ServletRequestCreator;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * JMS consumer for servlet executor.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
class ServletExecutorConsumer implements MessageListener {
    private static final Logger log = Logger.getLogger(ServletExecutorConsumer.class);

    private static final String JMSX_DELIVERY_COUNT = "JMSXDeliveryCount";

    private final ModuleLoader loader;

    public ServletExecutorConsumer(ModuleLoader loader) {
        this.loader = loader;
    }

    protected String getValue(final Message message, final String key) throws Exception {
        final String value = message.getStringProperty(MessageConstants.PREFIX + key);
        if (value == null)
            throw new IllegalArgumentException("Null value for key: " + key);
        return value;
    }

    protected ModuleIdentifier parseModuleIdentifier(Message msg) throws Exception {
        String mi = getValue(msg, MessageConstants.MODULE);
        return ModuleIdentifier.fromString(mi);
    }

    private ServletRequestCreator getServletRequestCreator(String appId, String module, ClassLoader cl, Message message) throws Exception {
        final String factoryClass = getValue(message, MessageConstants.FACTORY);
        ServletRequestCreator factory;
        final Key<Map<String, ServletRequestCreator>> key = new MapKey<>(appId, module, Slot.SERVLET_REQUEST_CREATOR);
        ComponentRegistry registry = ComponentRegistry.getInstance();
        Map<String, ServletRequestCreator> map = new ConcurrentHashMap<>();
        Map<String, ServletRequestCreator> factories = registry.putIfAbsent(key, map);
        if (factories == null) {
            factories = map;
        }
        factory = factories.get(factoryClass);
        if (factory == null) {
            @SuppressWarnings("unchecked")
            Class<ServletRequestCreator> clazz = (Class<ServletRequestCreator>) cl.loadClass(factoryClass);
            factory = clazz.newInstance();
            factories.put(factoryClass, factory);
        }
        return factory;
    }

    public void onMessage(Message message) {
        try {
            int maxAttempts = message.getIntProperty(MessageConstants.MAX_ATTEMPTS);
            int currentAttemptNumber = message.getIntProperty(JMSX_DELIVERY_COUNT); // 1-based
            if (maxAttempts != -1 && currentAttemptNumber > maxAttempts) {
                return;
            }

            final ModuleIdentifier identifier = parseModuleIdentifier(message);
            final Module module = loadModule(identifier);
            if (module == null) {
                return;
            }

            final String appId = getValue(message, MessageConstants.APP_ID);
            final String moduleId = getValue(message, MessageConstants.MODULE_ID);
            final SimpleKey<ServletContext> key = new SimpleKey<>(appId, moduleId, ServletContext.class);
            final ServletContext context = ComponentRegistry.getInstance().getComponent(key);
            if (context == null) {
                log.warn("No matching ServletContext, app (" + appId + ") already undeployed?");
                return;
            }

            final ClassLoader cl = module.getClassLoader();
            final ServletRequestCreator creator = getServletRequestCreator(appId, moduleId, cl, message);
            final HttpServletRequest request = creator.createServletRequest(context, message);
            final String path = getValue(message, MessageConstants.PATH);

            creator.prepare(request, appId, moduleId);
            try {
                final HttpServletResponse response = ServletExecutor.dispatch(appId, path, context, request);
                if (creator.isValid(request, response) == false) {
                    throw new RuntimeException(String.format("Invalid response for path %s, status was %s", path, response.getStatus()));
                }
            } finally {
                creator.finish();
            }
        } catch (RuntimeException e) {
            log.error("Error handling servlet execution.", e);
            throw e;
        } catch (Exception e) {
            log.error("Error handling servlet execution.", e);
            throw new RuntimeException(e);
        }
    }

    protected Module loadModule(ModuleIdentifier identifier) {
        try {
            return loader.loadModule(identifier);
        } catch (ModuleLoadException e) {
            log.warn("Cannot load module, app (" + identifier + ") already undeployed? - " + e.getMessage());
            return null;
        }
    }
}
