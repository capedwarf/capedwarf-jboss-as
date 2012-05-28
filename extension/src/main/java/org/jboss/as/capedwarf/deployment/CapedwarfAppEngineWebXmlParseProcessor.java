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

import java.io.Closeable;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.vfs.VirtualFile;

/**
 * Parse appengine-web.xml
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class CapedwarfAppEngineWebXmlParseProcessor extends CapedwarfDeploymentUnitProcessor {
    private static final String APPENGINE_WEB_XML = "WEB-INF/appengine-web.xml";

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();
        VirtualFile xml = root.getChild(APPENGINE_WEB_XML);

        try {
            doParseAppEngineWebXml(unit, root, xml);
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    protected abstract void doParseAppEngineWebXml(DeploymentUnit unit, VirtualFile root, VirtualFile xml) throws Exception;

    protected static void safeClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
