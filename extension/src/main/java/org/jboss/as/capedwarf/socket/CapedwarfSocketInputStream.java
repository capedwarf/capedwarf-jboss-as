/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.capedwarf.socket;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class CapedwarfSocketInputStream extends InputStream {
    private final CapedwarfSocket socket;
    private final InputStream delegate;

    CapedwarfSocketInputStream(CapedwarfSocket socket) throws IOException {
        this.socket = socket;
        this.delegate = socket.invoke("getInputStream", new Class[0], new Object[0]);
    }

    public int read() throws IOException {
        byte buff[] = new byte[1];
        int count = read(buff, 0, 1);
        if (count <= 0) {
            return -1;
        }
        return buff[0] & 0xff;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public synchronized void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();
        } finally {
            socket.reset();
        }
    }
}
