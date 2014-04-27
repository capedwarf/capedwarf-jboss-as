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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jboss.as.capedwarf.services.ServletExecutorConsumerService;
import org.jboss.as.capedwarf.utils.Constants;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.Keys;
import org.jboss.msc.service.ServiceName;

/**
 * Define any MSC dependencies.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfDependenciesProcessor extends CapedwarfTopDeploymentUnitProcessor {
    private static final Set<ServiceName> STATIC_DEPENDECIES;

    @SuppressWarnings("unchecked")
    private static final List<Key<?>> KEYS = Arrays.asList(
        Keys.TM,
        Keys.USER_TX,
        Keys.CHANNEL,
        Keys.EXECUTOR_SERVICE,
        Keys.THREAD_FACTORY,
        Keys.CACHE_MANAGER,
        Keys.MAIL_SESSION,
        Keys.HTTP_CLIENT,
        Keys.MODULE_LOADER
    );

    private static final List<ServiceName> keys = Lists.transform(KEYS, new Function<Key<?>, ServiceName>() {
        public ServiceName apply(Key<?> key) {
            return Constants.CAPEDWARF_NAME.append(String.valueOf(key.getSlot()));
        }
    });

    static {
        STATIC_DEPENDECIES = new HashSet<>();
        STATIC_DEPENDECIES.add(ServletExecutorConsumerService.NAME); // default queue
        STATIC_DEPENDECIES.add(Constants.JMSXA_BIND_INFO.getBinderServiceName()); // we need jms xa
        STATIC_DEPENDECIES.add(Constants.QUEUE_BIND_INFO.getBinderServiceName()); // we need queue
        // Components
        for (ServiceName sn : keys) {
            STATIC_DEPENDECIES.add(sn);
        }
    }

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        // no need for explicit dependencies, web app deployment service depends on them
    }

    static Set<ServiceName> getDependecies(String appId) {
        Set<ServiceName> set = new HashSet<>();
        set.addAll(STATIC_DEPENDECIES);
        set.addAll(CapedwarfCacheProcessor.getDependencies(appId));
        set.add(CapedwarfMuxIdProcessor.getDependency(appId));
        set.addAll(CapedwarfInstanceInfoProcessor.getDependencies(appId));
        return Collections.unmodifiableSet(set);
    }
}
