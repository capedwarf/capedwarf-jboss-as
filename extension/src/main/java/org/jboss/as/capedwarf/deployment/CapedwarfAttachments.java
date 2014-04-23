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

import java.util.Map;

import org.jboss.as.capedwarf.services.CacheConfig;
import org.jboss.as.capedwarf.services.ServerInstanceInfo;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.capedwarf.shared.config.AppEngineWebXml;
import org.jboss.capedwarf.shared.config.ApplicationConfiguration;
import org.jboss.capedwarf.shared.config.BackendsXml;
import org.jboss.capedwarf.shared.config.CacheName;
import org.jboss.capedwarf.shared.config.CapedwarfConfiguration;
import org.jboss.capedwarf.shared.config.CronXml;
import org.jboss.capedwarf.shared.config.IndexesXml;
import org.jboss.capedwarf.shared.config.QueueXml;

/**
 * Attachments.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
final class CapedwarfAttachments {
    // app config
    static final AttachmentKey<ApplicationConfiguration> APPLICATION_CONFIGURATION = AttachmentKey.create(ApplicationConfiguration.class);
    // xmls
    static final AttachmentKey<AppEngineWebXml> APP_ENGINE_WEB_XML = AttachmentKey.create(AppEngineWebXml.class);
    static final AttachmentKey<CapedwarfConfiguration> CAPEDWARF_WEB_XML = AttachmentKey.create(CapedwarfConfiguration.class);
    static final AttachmentKey<QueueXml> QUEUE_XML = AttachmentKey.create(QueueXml.class);
    static final AttachmentKey<BackendsXml> BACKENDS_XML = AttachmentKey.create(BackendsXml.class);
    static final AttachmentKey<IndexesXml> INDEXES_XML = AttachmentKey.create(IndexesXml.class);
    static final AttachmentKey<CronXml> CRON_XML = AttachmentKey.create(CronXml.class);
    static final AttachmentKey<AttachmentList<IndexesXml>> INDEXES_LIST = AttachmentKey.createList(IndexesXml.class);
    // cache
    static final AttachmentKey<Map<CacheName, CacheConfig>> CONFIGS = AttachmentKey.create(Map.class);
    // instance info
    static final AttachmentKey<ServerInstanceInfo> INSTANCE_INFO = AttachmentKey.create(ServerInstanceInfo.class);
}
