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

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.modules.filter.PathFilters;

/**
 * Add CapeDwarf modules.
 *
 * @author <a href="mailto:marko.luksa@gmail.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfDeploymentProcessor extends CapedwarfDeploymentUnitProcessor {

    private static final ModuleIdentifier CAPEDWARF_AS = ModuleIdentifier.create("org.jboss.as.capedwarf");

    private static final ModuleIdentifier APPENGINE = ModuleIdentifier.create("com.google.appengine");
    private static final ModuleIdentifier CAPEDWARF = ModuleIdentifier.create("org.jboss.capedwarf");
    private static final ModuleIdentifier INFINISPAN = ModuleIdentifier.create("org.infinispan");

    private static final ModuleIdentifier MODULES = ModuleIdentifier.create("org.jboss.modules");
    private static final ModuleIdentifier VELOCITY = ModuleIdentifier.create("org.apache.velocity");
    private static final ModuleIdentifier TX = ModuleIdentifier.create("javax.transaction.api");
    private static final ModuleIdentifier ACTIVATION = ModuleIdentifier.create("javax.activation.api");
    private static final ModuleIdentifier MAIL = ModuleIdentifier.create("org.javassist");
    private static final ModuleIdentifier JAVASSIST = ModuleIdentifier.create("javax.mail.api");
    private static final ModuleIdentifier JGROUPS = ModuleIdentifier.create("org.jgroups");
    private static final ModuleIdentifier INFINISPAN_QUERY = ModuleIdentifier.create("org.infinispan.query");
    private static final ModuleIdentifier HIBERNATE_SEARCH = ModuleIdentifier.create("org.hibernate.search");
    private static final ModuleIdentifier LUCENE = ModuleIdentifier.create("org.apache.lucene");
    private static final ModuleIdentifier HTTP_COMPONENTS = ModuleIdentifier.create("org.apache.httpcomponents");
    private static final ModuleIdentifier PICKETLINK = ModuleIdentifier.create("org.picketlink");
    private static final ModuleIdentifier PICKETLINK_SOCIAL = ModuleIdentifier.create("org.picketlink.social");
    private static final ModuleIdentifier GUAVA = ModuleIdentifier.create("com.google.guava");
    private static final ModuleIdentifier SMACK = ModuleIdentifier.create("org.jivesoftware.smack");
    private static final ModuleIdentifier BOUNCY_CASTLE_MAIL = ModuleIdentifier.create("org.bouncycastle.bcmail");
    private static final ModuleIdentifier BOUNCY_CASTLE_PKIX = ModuleIdentifier.create("org.bouncycastle.bcpkix");
    private static final ModuleIdentifier BOUNCY_CASTLE_PROV = ModuleIdentifier.create("org.bouncycastle.bcprov");
    private static final ModuleIdentifier COMMON_CORE = ModuleIdentifier.create("org.jboss.common-core");
    // inline this module deps, if running with bundled
    private static final ModuleIdentifier[] INLINE = {
            MODULES,
            VELOCITY,
            TX,
            ACTIVATION,
            MAIL,
            JAVASSIST,
            JGROUPS,
            INFINISPAN_QUERY,
            HIBERNATE_SEARCH,
            LUCENE,
            HTTP_COMPONENTS,
            PICKETLINK,
            PICKETLINK_SOCIAL,
            GUAVA,
            SMACK,
            BOUNCY_CASTLE_MAIL,
            BOUNCY_CASTLE_PKIX,
            BOUNCY_CASTLE_PROV,
            COMMON_CORE
    };

    private static final FilenameFilter JARS_SDK = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(".jar");
        }
    };

    private List<ResourceLoaderSpec> capedwarfResources;

    private String appengingAPI;

    public CapedwarfDeploymentProcessor(String appengingAPI) {
        if (appengingAPI == null)
            appengingAPI = "appengine-api";
        this.appengingAPI = appengingAPI;
    }

    @Override
    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        final ModuleLoader loader = Module.getBootModuleLoader();
        final ModuleSpecification moduleSpecification = unit.getAttachment(Attachments.MODULE_SPECIFICATION);
        // CapeDwarf AS module -- api only
        final ModuleDependency cdas = LibUtils.createModuleDependency(loader, CAPEDWARF_AS);
        cdas.addExportFilter(PathFilters.isChildOf("org.jboss.as.capedwarf.api"), true);
        moduleSpecification.addSystemDependency(cdas);
        // always add Infinispan
        moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, INFINISPAN));
        // check if we bundle gae api jar
        if (LibUtils.hasLibrary(unit, appengingAPI)) {
            // set it in marker
            CapedwarfDeploymentMarker.setBundledAppEngineApi(unit);
            // add a transformer, modifying GAE service factories
            moduleSpecification.addClassFileTransformer("org.jboss.capedwarf.bytecode.FactoriesTransformer");
            // add CapeDwarf resources directly as libs
            for (ResourceLoaderSpec rls : getCapedwarfResources())
                moduleSpecification.addResourceLoader(rls);
            // add other needed dependencies
            for (ModuleIdentifier mi : INLINE)
                moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, mi));
        } else {
            // add CapeDwarf
            moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, CAPEDWARF));
            // add modified AppEngine
            moduleSpecification.addSystemDependency(LibUtils.createModuleDependency(loader, APPENGINE));
        }
    }

    protected synchronized List<ResourceLoaderSpec> getCapedwarfResources() throws DeploymentUnitProcessingException {
        try {
            if (capedwarfResources == null) {
                final List<File> mps;
                final String modulePaths = System.getProperty("module.path");
                if (modulePaths == null) {
                    mps = Collections.singletonList(new File(System.getProperty("jboss.home.dir"), "modules"));
                } else {
                    mps = new ArrayList<File>();
                    for (String s : modulePaths.split(":"))
                        mps.add(new File(s));
                }
                final List<File> capedwarfJars = findCapedwarfJars(mps);
                if (capedwarfJars.isEmpty())
                    throw new DeploymentUnitProcessingException("No CapeDwarf jars found!");

                final List<ResourceLoaderSpec> resources = new ArrayList<ResourceLoaderSpec>();
                for (File jar : capedwarfJars) {
                    final JarFile jarFile = new JarFile(jar);
                    final ResourceLoader rl = ResourceLoaders.createJarResourceLoader(jar.getName(), jarFile);
                    resources.add(ResourceLoaderSpec.createResourceLoaderSpec(rl));
                }
                capedwarfResources = resources;
            }
            return capedwarfResources;
        } catch (DeploymentUnitProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    protected List<File> findCapedwarfJars(List<File> mps) {
        final List<File> results = new ArrayList<File>();
        final Set<String> existing = new HashSet<String>();
        for (File mp : mps) {
            findCapedwarfJars(mp, results, existing);
        }
        return results;
    }

    protected void findCapedwarfJars(File mp, List<File> results, Set<String> existing) {
        final File cdModules = new File(mp, "org/jboss/capedwarf/main");
        if (cdModules.exists()) {
            for (File jar : cdModules.listFiles(JARS_SDK)) {
                if (existing.add(jar.getName())) {
                    results.add(jar);
                }
            }
        }
    }
}
