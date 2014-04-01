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

package org.jboss.as.capedwarf.deployment;

import java.util.Map;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.capedwarf.shared.util.ParseUtils;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
final class DeploymentParseUtils {
    private DeploymentParseUtils() {
    }

    static VirtualFile getFile(DeploymentPhaseContext context, String path) {
        return getFile(context.getDeploymentUnit(), path);
    }

    static VirtualFile getFile(DeploymentUnit unit, String path) {
        ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();
        return root.getChild(path);
    }

    static Map<String, String> parseTokens(VirtualFile xml, final String... tokens) throws Exception {
        return ParseUtils.parseTokens(xml.openStream(), tokens);
    }
}
