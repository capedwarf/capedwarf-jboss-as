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

import org.jboss.as.network.SocketBinding;
import org.jboss.capedwarf.shared.modules.DefaultInstanceInfo;
import org.jboss.capedwarf.shared.modules.InstanceInfo;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.undertow.HttpListenerService;
import org.wildfly.extension.undertow.ListenerService;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.UndertowService;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ServerInstanceInfo implements Service<InstanceInfo>, InstanceInfo {
    private InjectedValue<UndertowService> undertowServiceInjectedValue = new InjectedValue<>();
    private InjectedValue<Server> serverInjectedValue = new InjectedValue<>();
    private String serverName;
    private int port;

    protected ServerInstanceInfo() {
    }

    public ServerInstanceInfo(String serverName) {
        this.serverName = serverName;
    }

    public String getServerName() {
        return serverName;
    }

    protected Server getServer() {
        return serverInjectedValue.getValue();
    }

    @SuppressWarnings("unchecked")
    private <T extends ListenerService> ListenerService<T> getListener(Class<T> type) {
        for (ListenerService ls : getServer().getListeners()) {
            if (type.isInstance(ls)) {
                return ls;
            }
        }
        throw new IllegalArgumentException(String.format("No such listener: %s", type.getName()));
    }

    public String getHostname() {
        return getHost() + ":" + getPort();
    }

    public String getId() {
        return getServer().getName();
    }

    public String getHost() {
        return DefaultInstanceInfo.LOCALHOST;
    }

    public int getPort() {
        return port;
    }

    public void start(StartContext startContext) throws StartException {
        InjectedValue<SocketBinding> binding = getListener(HttpListenerService.class).getBinding();
        port = binding.getValue().getPort();
    }

    public void stop(StopContext stopContext) {
    }

    public InstanceInfo getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<UndertowService> getUndertowServiceInjectedValue() {
        return undertowServiceInjectedValue;
    }

    public InjectedValue<Server> getServerInjectedValue() {
        return serverInjectedValue;
    }
}
