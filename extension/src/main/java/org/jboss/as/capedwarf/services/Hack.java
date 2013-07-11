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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.servlet.spec.RequestDispatcherImpl;

/**
 * Hack around to dispatch custom request from static view.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
class Hack {

    static HttpServletResponse invoke(final RequestDispatcher dispatcher, final HttpServletRequest request) throws ServletException, IOException {
        if (dispatcher instanceof RequestDispatcherImpl == false) {
            throw new IllegalStateException("Can only invoke on " + RequestDispatcherImpl.class.getSimpleName());
        }

        RequestDispatcherImpl rd = RequestDispatcherImpl.class.cast(dispatcher);

        NoopServletResponse response = new NoopServletResponse();
        rd.mock(request, response);

        // check for dispatch error
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (attribute == null)
            return response;

        if (attribute instanceof RuntimeException) {
            throw RuntimeException.class.cast(attribute);
        } else if (attribute instanceof IOException) {
            throw IOException.class.cast(attribute);
        } else {
            throw new IOException("Dispatch error: " + attribute);
        }
    }

}
