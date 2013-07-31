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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.capedwarf.shared.modules.ModuleInfo;

/**
 * Marks CapeDwarf deployment / app.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfDeploymentMarker {
    private static final AttachmentKey<CapedwarfDeploymentMarker> MARKER = AttachmentKey.create(CapedwarfDeploymentMarker.class);

    private DeploymentType deploymentType;
    private boolean bundledAppEngineApi;
    private boolean cdiApp;
    private String version;
    private String appId;
    private String appVersion;
    private String module = ModuleInfo.DEFAULT_MODULE_NAME;
    private boolean threadsafe;
    private Set<String> persistenceProviders;
    private Set<String> entities;

    private CapedwarfDeploymentMarker() {
    }

    public String getAppId() {
        return appId;
    }

    /**
     * Mark the top level deployment as being a CapeDwarf deployment.
     *
     * @param unit the deployment unit
     */
    static void mark(DeploymentUnit unit) {
        unit.putAttachment(MARKER, new CapedwarfDeploymentMarker());
    }

    /**
     * Get unit's marker.
     *
     * @param unit the deployment unit
     * @return marker or null if it doesn't exist
     */
    private static CapedwarfDeploymentMarker getMarker(DeploymentUnit unit) {
        return unit.getAttachment(MARKER);
    }

    /**
     * retuns true if the {@link DeploymentUnit} is a GAE app -- has appengine-web.xml in WEB-INF,
     * while it's a single .war deployment.
     *
     * @param unit the deployment unit
     * @return true if CapeDwarf deployment, false otherwise
     */
    public static boolean isCapedwarfDeployment(DeploymentUnit unit) {
        return unit.hasAttachment(MARKER);
    }

    /**
     * Get top marker.
     *
     * @param unit the deployment unit
     * @return parent marker
     */
    public static CapedwarfDeploymentMarker getTopMarker(DeploymentUnit unit) {
        while (unit.getParent() != null) {
            unit = unit.getParent();
        }
        return getMarker(unit);
    }

    /**
     * Is this GAE modules app?
     *
     * @param unit the deployment unit
     * @return true if this is GAE modules app
     */
    public static boolean hasModules(DeploymentUnit unit) {
        return ((getDeploymentType(unit) == DeploymentType.EAR) || (getTopMarker(unit) != getMarker(unit)));
    }

    /**
     * Get deployment type.
     *
     * @param unit the deployment unit
     * @return deployment type
     */
    public static DeploymentType getDeploymentType(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return (marker != null ? marker.deploymentType : null);
    }

    /**
     * Set deployment type.
     *
     * @param unit the deployment unit
     * @param type the deployment type
     */
    static void setDeploymentType(DeploymentUnit unit, DeploymentType type) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.deploymentType = type;
    }

    /**
     * Keep info weather GAE api is bundled.
     *
     * @param unit the deployment unit
     */
    public static void setBundledAppEngineApi(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.bundledAppEngineApi = true;
    }

    /**
     * Is GAE api bundled in app.
     *
     * @param unit the deployment unit
     * @return true if GAE api is bundled, false otherwise
     */
    public static boolean isBundledAppEngineApi(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null && marker.bundledAppEngineApi;
    }

    /**
     * Keep info weather app was CDI originally.
     *
     * @param unit the deployment unit
     * @param flag the cdi app flag
     */
    public static void setCDIApp(DeploymentUnit unit, boolean flag) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.cdiApp = flag;
    }

    /**
     * Was this originally CDI app.
     *
     * @param unit the deployment unit
     * @return true if app was originally CDI app, false otherwise
     */
    public static boolean isCDIApp(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null && marker.cdiApp;
    }

    /**
     * Set runtime version info.
     *
     * @param unit  the deployment unit
     * @param version the version
     */
    public static void setVersion(DeploymentUnit unit, String version) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.version = version;
    }

    /**
     * Get runtime version.
     *
     * @param unit the deployment unit
     * @return app id
     */
    public static String getVersion(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null ? marker.version : null;
    }

    /**
     * Set app id info.
     *
     * @param unit  the deployment unit
     * @param appId the app id
     */
    public static void setAppId(DeploymentUnit unit, String appId) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.appId = appId;
    }

    /**
     * Get app id.
     *
     * @param unit the deployment unit
     * @return app id
     */
    public static String getAppId(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null ? marker.appId : null;
    }

    /**
     * Set app version info.
     *
     * @param unit  the deployment unit
     * @param appVersion the app version
     */
    public static void setAppVersion(DeploymentUnit unit, String appVersion) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.appVersion = appVersion;
    }

    /**
     * Get app version.
     *
     * @param unit the deployment unit
     * @return app version
     */
    public static String getAppVersion(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null ? marker.appVersion : null;
    }

    /**
     * Set module info.
     *
     * @param unit  the deployment unit
     * @param module the module
     */
    public static void setModule(DeploymentUnit unit, String module) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null && module != null)
            marker.module = module;
    }

    /**
     * Get module.
     *
     * @param unit the deployment unit
     * @return module
     */
    public static String getModule(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null ? marker.module : null;
    }

    /**
     * Set threadsafe info.
     *
     * @param unit  the deployment unit
     * @param threadsafe the threadsafe flag
     */
    public static void setThreadsafe(DeploymentUnit unit, boolean threadsafe) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null)
            marker.threadsafe = threadsafe;
    }

    /**
     * Is threadsafe.
     *
     * @param unit the deployment unit
     * @return threadsafe flag
     */
    public static boolean isThreadsafe(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return marker != null && marker.threadsafe;
    }

    /**
     * Add persistence provider.
     *
     * @param unit                the deployment unit
     * @param persistenceProvider the persistence provider
     */
    public static void addPersistenceProvider(DeploymentUnit unit, String persistenceProvider) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (marker) {
                if (marker.persistenceProviders == null)
                    marker.persistenceProviders = new HashSet<String>();
                marker.persistenceProviders.add(persistenceProvider);
            }
        }
    }

    /**
     * Get persistence providers.
     *
     * @param unit the deployment unit
     * @return the persistence providers or empty set if none
     */
    public static Set<String> getPersistenceProviders(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (marker) {
                if (marker.persistenceProviders != null) {
                    return Collections.unmodifiableSet(marker.persistenceProviders);
                }
            }
        }
        return Collections.emptySet();
    }

    /**
     * Set JPA entities.
     *
     * @param unit     the deployment unit
     * @param entities the JPA entities
     */
    public static void setEntities(DeploymentUnit unit, Set<String> entities) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        if (marker != null) {
            marker.entities = entities;
        }
    }

    /**
     * Get JPA entities.
     *
     * @param unit the deployment unit
     * @return JPA entities
     */
    public static Set<String> getEntities(DeploymentUnit unit) {
        final CapedwarfDeploymentMarker marker = getMarker(unit);
        return (marker != null) ? marker.entities : null;
    }
}
