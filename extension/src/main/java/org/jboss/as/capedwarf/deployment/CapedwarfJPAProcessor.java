/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.jboss.as.capedwarf.utils.LibUtils;
import org.jboss.as.jpa.config.Configuration;
import org.jboss.as.jpa.config.PersistenceUnitMetadataHolder;
import org.jboss.as.jpa.persistenceprovider.PersistenceProviderLoader;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.jipijapa.plugin.spi.PersistenceUnitMetadata;

/**
 * Fix CapeDwarf JPA usage - ignore PU service.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfJPAProcessor extends CapedwarfPersistenceProcessor {
    private static final ModuleIdentifier DN_CORE = ModuleIdentifier.create("org.datanucleus");
    private static final ModuleIdentifier DN_GAE = ModuleIdentifier.create("org.datanucleus.appengine");
    private static final ModuleIdentifier JDO = ModuleIdentifier.create("javax.jdo.api");

    private static final String DN_TRANSFORMER = "org.jboss.capedwarf.bytecode.datanucleus.DataNucleusTransformer";

    private String datanucleusLib = "datanucleus-core"; // TODO -- configurable
    private boolean flag;

    protected void modifyPersistenceInfo(DeploymentUnit unit, ResourceRoot resourceRoot, ResourceType type) throws IOException {
        final ModuleSpecification moduleSpecification = unit.getAttachment(Attachments.MODULE_SPECIFICATION);

        final boolean hasDataNucleusLib = LibUtils.hasLibrary(unit, datanucleusLib);
        if (hasDataNucleusLib || (type == ResourceType.DEPLOYMENT_ROOT && isJDO(resourceRoot))) {
            addTransformer(moduleSpecification);
        }

        final PersistenceUnitMetadataHolder holder = resourceRoot.getAttachment(PersistenceUnitMetadataHolder.PERSISTENCE_UNITS);
        if (holder != null) {
            final List<PersistenceUnitMetadata> pus = holder.getPersistenceUnits();
            if (pus != null && pus.isEmpty() == false) {
                for (PersistenceUnitMetadata pumd : pus) {
                    final String providerClass = pumd.getPersistenceProviderClassName();

                    CapedwarfDeploymentMarker.addPersistenceProvider(unit, providerClass);

                    if (Configuration.PROVIDER_CLASS_DATANUCLEUS.equals(providerClass) || Configuration.PROVIDER_CLASS_DATANUCLEUS_GAE.equals(providerClass)) {
                        // do not start PU service, should we do it for all PUs, not just DNs?
                        final Properties properties = pumd.getProperties();
                        if (properties.containsKey(Configuration.JPA_CONTAINER_MANAGED) == false) {
                            properties.put(Configuration.JPA_CONTAINER_MANAGED, Boolean.FALSE.toString());
                        }

                        if (hasDataNucleusLib) {
                            // ignore if DN is bundled
                            ModuleIdentifier mi = ModuleIdentifier.create(Configuration.getProviderModuleNameFromProviderClassName(providerClass));
                            moduleSpecification.addExclusion(mi);
                        } else {
                            // add DN module manually, since we ignore start-up
                            addDataNucleus();
                            // add transformer
                            addTransformer(moduleSpecification);

                            // it's not bundled, add it; JDO is also direct as entities need it
                            final ModuleLoader loader = Module.getBootModuleLoader();
                            moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, DN_CORE));
                            moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, DN_GAE));
                            moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, JDO));
                        }
                    }
                }
            }
        }
    }

    protected static boolean isJDO(ResourceRoot rr) {
        VirtualFile root = rr.getRoot();
        VirtualFile jdoconfig = root.getChild("WEB-INF/classes/META-INF/jdoconfig.xml");
        return (jdoconfig != null && jdoconfig.exists());
    }

    protected static void addTransformer(ModuleSpecification spec) {
        List<String> transformers = spec.getClassFileTransformers();
        if (transformers.contains(DN_TRANSFORMER) == false) {
            spec.addClassFileTransformer(DN_TRANSFORMER);
        }
    }

    protected synchronized void addDataNucleus() throws IOException {
        if (flag == false) {
            flag = true;
            try {
                PersistenceProviderLoader.loadProviderModuleByName(DN_CORE.toString());
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}

