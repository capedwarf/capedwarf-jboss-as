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

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StoreAsBinaryConfigurationBuilder;

/**
 * Apply storeAsBinary config.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class StoreAsBinaryConfigurationCallback extends AbstractConfigurationCallback {
    private boolean keys;
    private boolean values;
    private boolean defensive;

    public StoreAsBinaryConfigurationCallback() {
    }

    public StoreAsBinaryConfigurationCallback(boolean keys, boolean values, boolean defensive) {
        this.keys = keys;
        this.values = values;
        this.defensive = defensive;
    }

    protected void applyBuilder(ConfigurationBuilder builder) {
        StoreAsBinaryConfigurationBuilder sabbc = builder.storeAsBinary();
        if (keys) {
            sabbc.storeKeysAsBinary(true);
        }
        if (values) {
            sabbc.storeValuesAsBinary(true);
        }
        if (defensive) {
            sabbc.defensive(true);
        }
    }

    public void setKeys(boolean keys) {
        this.keys = keys;
    }

    public void setValues(boolean values) {
        this.values = values;
    }

    public void setDefensive(boolean defensive) {
        this.defensive = defensive;
    }
}
