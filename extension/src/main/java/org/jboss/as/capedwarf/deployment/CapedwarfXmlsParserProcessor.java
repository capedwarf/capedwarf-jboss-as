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

import java.io.InputStream;
import java.util.Map;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.SimpleKey;
import org.jboss.capedwarf.shared.config.AppEngineWebXml;
import org.jboss.capedwarf.shared.config.AppEngineWebXmlParser;
import org.jboss.capedwarf.shared.config.ApplicationConfiguration;
import org.jboss.capedwarf.shared.config.BackendsXml;
import org.jboss.capedwarf.shared.config.BackendsXmlParser;
import org.jboss.capedwarf.shared.config.CapedwarfConfiguration;
import org.jboss.capedwarf.shared.config.CapedwarfConfigurationParser;
import org.jboss.capedwarf.shared.config.CronXml;
import org.jboss.capedwarf.shared.config.CronXmlParser;
import org.jboss.capedwarf.shared.config.IndexesXml;
import org.jboss.capedwarf.shared.config.IndexesXmlParser;
import org.jboss.capedwarf.shared.config.QueueXml;
import org.jboss.capedwarf.shared.config.QueueXmlParser;
import org.jboss.capedwarf.shared.modules.ModuleInfo;
import org.jboss.vfs.VirtualFile;

import static org.jboss.capedwarf.shared.util.ParseUtils.safeClose;

/**
 * Parse GAE / CD xmls.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfXmlsParserProcessor extends CapedwarfWebDeploymentUnitProcessor {
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final VirtualFile deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
        try {
            final String appId = CapedwarfDeploymentMarker.getAppId(unit);
            final String moduleId = CapedwarfDeploymentMarker.getModule(unit);

            // appengine-web.xml
            final InputStream appIs = getInputStream(deploymentRoot, "WEB-INF/appengine-web.xml", true);
            try {
                AppEngineWebXml appConfig = AppEngineWebXmlParser.parse(appIs);

                if (CapedwarfDeploymentMarker.hasModules(unit)) {
                    AppEngineWebXml.override(appConfig, appId);
                }

                Map<String, ModuleInfo> map = ModuleInfo.getModules(appId);
                map.put(appConfig.getModule(), new ModuleInfo(appConfig));

                unit.putAttachment(CapedwarfAttachments.APP_ENGINE_WEB_XML, appConfig);
            } finally {
                safeClose(appIs);
            }

            // capedwarf-web.xml
            final InputStream cdIs = getInputStream(deploymentRoot, "WEB-INF/capedwarf-web.xml", false);
            try {
                CapedwarfConfiguration cdConfig = CapedwarfConfigurationParser.parse(cdIs);
                unit.putAttachment(CapedwarfAttachments.CAPEDWARF_WEB_XML, cdConfig);
            } finally {
                safeClose(cdIs);
            }

            // queue.xml
            final InputStream queueIs = getInputStream(deploymentRoot, "WEB-INF/queue.xml", false);
            try {
                QueueXml queueConfig = QueueXmlParser.parse(queueIs);
                unit.putAttachment(CapedwarfAttachments.QUEUE_XML, queueConfig);
            } finally {
                safeClose(queueIs);
            }

            // backends.xml
            final InputStream backendsIs = getInputStream(deploymentRoot, "WEB-INF/backends.xml", false);
            try {
                BackendsXml backendsConfig = BackendsXmlParser.parse(backendsIs);
                unit.putAttachment(CapedwarfAttachments.BACKENDS_XML, backendsConfig);
            } finally {
                safeClose(backendsIs);
            }

            // datastore-indexes.xml
            final InputStream indexesIs = getInputStream(deploymentRoot, "WEB-INF/datastore-indexes.xml", false);
            try {
                IndexesXml indexesConfig = IndexesXmlParser.parse(indexesIs);
                unit.putAttachment(CapedwarfAttachments.INDEXES_XML, indexesConfig);
                getTopDeploymentUnit(unit).addToAttachmentList(CapedwarfAttachments.INDEXES_LIST, indexesConfig);
            } finally {
                safeClose(indexesIs);
            }

            // cron.xml
            final InputStream cronIS = getInputStream(deploymentRoot, "WEB-INF/cron.xml", false);
            try {
                CronXml cronXml = CronXmlParser.parse(cronIS);
                unit.putAttachment(CapedwarfAttachments.CRON_XML, cronXml);
            } finally {
                safeClose(cronIS);
            }

            ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration(
                unit.getAttachment(CapedwarfAttachments.APP_ENGINE_WEB_XML),
                unit.getAttachment(CapedwarfAttachments.CAPEDWARF_WEB_XML),
                unit.getAttachment(CapedwarfAttachments.QUEUE_XML),
                unit.getAttachment(CapedwarfAttachments.BACKENDS_XML),
                unit.getAttachment(CapedwarfAttachments.INDEXES_XML),
                unit.getAttachment(CapedwarfAttachments.CRON_XML));
            unit.putAttachment(CapedwarfAttachments.APPLICATION_CONFIGURATION, applicationConfiguration);

            ComponentRegistry.getInstance().setComponent(new SimpleKey<>(appId, moduleId, ApplicationConfiguration.class), applicationConfiguration);

        } catch (DeploymentUnitProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    protected static InputStream getInputStream(VirtualFile root, String path, boolean mandatory) throws Exception {
        final VirtualFile child = root.getChild(path);
        if (child == null || child.exists() == false) {
            if (mandatory) {
                throw new DeploymentUnitProcessingException("No such file: " + path);
            } else {
                return null;
            }
        }
        return child.openStream();
    }
}
