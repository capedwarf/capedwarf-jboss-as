/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.util.logging.Logger;

import org.jboss.capedwarf.shared.config.ApplicationConfiguration;
import org.jboss.capedwarf.shared.reflection.MethodInvocation;
import org.jboss.capedwarf.shared.reflection.ReflectionUtils;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CronService implements Service<String> {
    private static final Logger log = Logger.getLogger(CronService.class.getName());
    private final InjectedValue<ModuleLoader> loader = new InjectedValue<>();

    private final ModuleIdentifier identifier;
    private final ApplicationConfiguration configuration;

    private Object cron;

    public CronService(ModuleIdentifier identifier, ApplicationConfiguration configuration) {
        this.identifier = identifier;
        this.configuration = configuration;
    }

    public void start(StartContext startContext) throws StartException {
        log.info(String.format("Starting cron service - %s", getValue()));
        try {
            Class<?> clazz = loader.getValue().loadModule(identifier).getClassLoader().loadClass("org.jboss.capedwarf.cron.CapedwarfCron");
            cron = ReflectionUtils.invokeStaticMethod(clazz, "create", ApplicationConfiguration.class, configuration);
        } catch (Exception e) {
            throw new StartException(e);
        }
    }

    public void stop(StopContext stopContext) {
        log.info(String.format("Stopping cron service - %s", getValue()));
        MethodInvocation mi = ReflectionUtils.cacheTargetMethod(cron, "destroy");
        mi.invokeWithTarget(cron);
    }

    public String getValue() throws IllegalStateException, IllegalArgumentException {
        return identifier.toString();
    }

    public InjectedValue<ModuleLoader> getLoader() {
        return loader;
    }
}
