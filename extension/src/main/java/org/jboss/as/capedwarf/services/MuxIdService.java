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

import javax.transaction.TransactionManager;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jgroups.Channel;
import org.jgroups.UpHandler;
import org.jgroups.blocks.mux.Muxer;

/**
 * Provide mux id per app.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class MuxIdService implements Service<MuxIdGenerator> {
    private static final String MUX_GEN  = "mux_gen";

    private InjectedValue<Cache> cacheInjectedValue = new InjectedValue<Cache>();
    private InjectedValue<TransactionManager> tmInjectedValue = new InjectedValue<TransactionManager>();
    private InjectedValue<Channel> channelInjectedValue = new InjectedValue<Channel>();

    private final Action<Void> START = new Action<Void>() {
        public Void inTx(AdvancedCache<String, MuxIdGenerator> cache, Muxer<UpHandler> muxer) throws Exception {
            if (cache.lock(MUX_GEN)) {
                MuxIdGenerator generator = cache.get(MUX_GEN);
                if (generator == null) {
                    generator = new MuxIdGenerator();
                    cache.put(MUX_GEN, generator);
                }
                generator.increment(muxer, appId);
            }
            return null;
        }
    };

    private final Action<MuxIdGenerator> GET = new Action<MuxIdGenerator>() {
        public MuxIdGenerator inTx(AdvancedCache<String, MuxIdGenerator> cache, Muxer<UpHandler> muxer) throws Exception {
            if (cache.lock(MUX_GEN)) {
                return cache.get(MUX_GEN);
            } else {
                throw new IllegalArgumentException("Cannot get a lock on mux generator!");
            }
        }
    };

    private final Action<Void> STOP = new Action<Void>() {
        public Void inTx(AdvancedCache<String, MuxIdGenerator> cache, Muxer<UpHandler> muxer) throws Exception {
            if (cache.lock(MUX_GEN)) {
                MuxIdGenerator generator = cache.get(MUX_GEN);
                if (generator != null) {
                    generator.decrement(appId);
                }
            }
            return null;
        }
    };

    private final String appId;

    public MuxIdService(String appId) {
        this.appId = appId;
    }

    protected <T> T execute(Action<T> action) {
        @SuppressWarnings("unchecked")
        Cache<String, MuxIdGenerator> cache = cacheInjectedValue.getValue();
        TransactionManager tm = tmInjectedValue.getValue();
        Channel channel = channelInjectedValue.getValue();
        @SuppressWarnings("unchecked")
        Muxer<UpHandler> muxer = (Muxer<UpHandler>) channel.getUpHandler();
        try {
            boolean executed = false;
            tm.begin();
            try {
                T result = action.inTx(cache.getAdvancedCache(), muxer);
                executed = true;
                tm.commit();
                return result;
            } catch (Exception e) {
                if (executed == false) {
                    tm.rollback();
                }
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void start(StartContext context) throws StartException {
        execute(START);
    }

    public void stop(StopContext context) {
        execute(STOP);
    }

    public MuxIdGenerator getValue() throws IllegalStateException, IllegalArgumentException {
        return execute(GET);
    }

    private static interface Action<T> {
        T inTx(AdvancedCache<String, MuxIdGenerator> cache, Muxer<UpHandler> muxer) throws Exception;
    }

    public InjectedValue<Cache> getCacheInjectedValue() {
        return cacheInjectedValue;
    }

    public InjectedValue<TransactionManager> getTmInjectedValue() {
        return tmInjectedValue;
    }

    public InjectedValue<Channel> getChannelInjectedValue() {
        return channelInjectedValue;
    }
}
