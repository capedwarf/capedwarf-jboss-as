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

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.SimpleKey;

/**
 * Execute servlet from an async invocation.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class ServletExecutor {
    /**
     * Dispatch custom request.
     *
     * @param appId the appId
     * @param path the dispatcher path
     * @param request the custom request
     * @throws IOException for any I/O exception
     * @throws ServletException for any servlet exception
     */
    static HttpServletResponse dispatch(final String appId, final String module, final String path, final HttpServletRequest request) throws IOException, ServletException {
        SimpleKey<ServletContext> key = new SimpleKey<>(appId, module, ServletContext.class);
        ServletContext context = ComponentRegistry.getInstance().getComponent(key);
        return dispatch(appId, path, context, request);
    }

    /**
     * Dispatch custom request.
     *
     * @param appId the appId
     * @param path the dispatcher path
     * @param context the servlet context
     * @param request the custom request
     * @throws IOException for any I/O exception
     * @throws ServletException for any servlet exception
     */
    static HttpServletResponse dispatch(final String appId, final String path, final ServletContext context, final HttpServletRequest request) throws IOException, ServletException {
        if (appId == null)
            throw new IllegalArgumentException("Null appId");
        if (path == null)
            throw new IllegalArgumentException("Null path");
        if (request == null)
            throw new IllegalArgumentException("Null request");
        if (context == null)
            throw new IllegalArgumentException("No context registered for appId: " + appId);

        final RequestDispatcher dispatcher = context.getRequestDispatcher(path);
        if (dispatcher == null)
            throw new IllegalArgumentException("No dispatcher for path: " + path);

        return Hack.invoke(dispatcher, request);
    }
}
