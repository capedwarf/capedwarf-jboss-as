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

import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.UndertowService;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class UTInstanceInfo extends ServerInstanceInfo {
    private Server server;

    @Override
    protected Server getServer() {
        return server;
    }

    protected Server findServer() {
        UndertowService ut = getUndertowServiceInjectedValue().getValue();
        for (Server s : ut.getServers()) {
            if (ut.getDefaultServer().equals(s.getName())) {
                return s;
            }
        }
        throw new IllegalStateException(String.format("No default server found?! - [%s, %s]", ut.getDefaultServer(), ut.getServers()));
    }

    public void start(StartContext startContext) throws StartException {
        server = findServer();
        super.start(startContext);
    }
}
