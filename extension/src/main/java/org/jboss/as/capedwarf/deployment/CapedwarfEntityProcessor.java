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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.MapKey;
import org.jboss.capedwarf.shared.components.SetKey;
import org.jboss.capedwarf.shared.components.Slot;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;

/**
 * Read entity classes for annotations; e.g. allocationSize.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfEntityProcessor extends CapedwarfWebDeploymentUnitProcessor {
    private static final DotName JPA_ENTITY = DotName.createSimple("javax.persistence.Entity");
    private static final DotName JPA_EMBEDDABLE = DotName.createSimple("javax.persistence.Embeddable");
    private static final DotName JPA_MAPPED_SUPERCLASS = DotName.createSimple("javax.persistence.MappedSuperclass");
    private static final DotName JPA_SEQUENCE_GENERATOR = DotName.createSimple("javax.persistence.SequenceGenerator");
    private static final DotName JDO_ENTITY = DotName.createSimple("javax.jdo.annotations.PersistenceCapable");
    private static final DotName JDO_SEQUENCE = DotName.createSimple("javax.jdo.annotations.Sequence");

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        final Map<String, Integer> allocationsMap = new HashMap<>();
        final Set<String> entityClasses = new HashSet<>();
        // handle JPA
        final List<AnnotationInstance> entities = index.getAnnotations(JPA_ENTITY);
        if (entities.isEmpty() == false) {
            // fill-in entity classes
            final Map<String, AnnotationInstance> entityMap = new HashMap<>();
            for (AnnotationInstance ai : entities) {
                final AnnotationTarget target = ai.target();
                final ClassInfo ci = (ClassInfo) target;
                final String className = ci.name().toString();
                entityMap.put(className, ai);
                entityClasses.add(className);
            }
            final List<AnnotationInstance> generators = index.getAnnotations(JPA_SEQUENCE_GENERATOR);
            if (generators.isEmpty() == false) {
                // map sequence generator to its entity
                for (AnnotationInstance ai : generators) {
                    final AnnotationValue allocationSize = ai.value("allocationSize");
                    if (allocationSize != null) {
                        final AnnotationValue seqName = ai.value("sequenceName");
                        if (seqName != null && seqName.asString().length() > 0) {
                            allocationsMap.put(seqName.asString(), (-1) * allocationSize.asInt());
                            continue;
                        }

                        AnnotationTarget target = ai.target();
                        String className = null;
                        if (target instanceof ClassInfo) {
                            final ClassInfo ci = (ClassInfo) target;
                            className = ci.name().toString();
                        } else if (target instanceof MethodInfo) {
                            final MethodInfo mi = (MethodInfo) target;
                            className = mi.declaringClass().name().toString();
                        } else if (target instanceof FieldInfo) {
                            final FieldInfo fi = (FieldInfo) target;
                            className = fi.declaringClass().name().toString();
                        }

                        if (className != null) {
                            int as = allocationSize.asInt();
                            final AnnotationInstance entityAnnotation = entityMap.get(className);
                            if (entityAnnotation != null) {
                                allocationsMap.put(toKind(className, entityAnnotation), as);
                            } else {
                                final Set<ClassInfo> allKnownSubclasses = index.getAllKnownSubclasses(DotName.createSimple(className));
                                for (ClassInfo ci : allKnownSubclasses) {
                                    final String ciCN = ci.name().toString();
                                    final AnnotationInstance ea = entityMap.get(ciCN);
                                    if (ea != null) {
                                        allocationsMap.put(toKind(className, ea), as);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        addTargetClasses(index, JPA_EMBEDDABLE, entityClasses);
        addTargetClasses(index, JPA_MAPPED_SUPERCLASS, entityClasses);

        // handle JDO
        for (AnnotationInstance ai : index.getAnnotations(JDO_SEQUENCE)) {
            String seqName = null;
            AnnotationValue seqNameAV = ai.value("datastoreSequence");
            if (seqNameAV != null && seqNameAV.asString().length() > 0) {
                seqName = seqNameAV.asString();
                allocationsMap.put(seqName, (-1));
            }
            final AnnotationValue extensions = ai.value("extensions");
            if (extensions != null) {
                final AnnotationInstance[] aies = extensions.asNestedArray();
                for (AnnotationInstance aie : aies) {
                    final AnnotationValue vendorName = aie.value("vendorName");
                    final AnnotationValue key = aie.value("key");
                    if (vendorName != null && key != null && "datanucleus".equals(vendorName.asString()) && "key-cache-size".equals(key.asString())) {
                        final AnnotationValue value = aie.value("value");
                        if (value != null) {
                            if (seqName != null) {
                                allocationsMap.put(seqName, (-1) * Integer.parseInt(value.asString()));
                            } else {
                                final String kind = toKind(((ClassInfo) ai.target()).name().toString());
                                allocationsMap.put(kind, Integer.parseInt(value.asString()));
                            }
                        }
                    }
                }
            }
        }
        addTargetClasses(index, JDO_ENTITY, entityClasses);

        String appId = CapedwarfDeploymentMarker.getAppId(unit);
        String moduleId = CapedwarfDeploymentMarker.getModule(unit);
        ComponentRegistry registry = ComponentRegistry.getInstance();
        // push allocationsMap to registry
        registry.setComponent(new MapKey<String, Integer>(appId, moduleId, Slot.ALLOCATIONS_MAP), Collections.unmodifiableMap(allocationsMap));
        // push entities to registry
        registry.setComponent(new SetKey<String>(appId, moduleId, Slot.METADATA_SCANNER), Collections.unmodifiableSet(entityClasses));

        // attach to unit
        CapedwarfDeploymentMarker.setEntities(unit, entityClasses);
    }

    private static void addTargetClasses(CompositeIndex index, DotName annotation, Set<String> set) {
        for (AnnotationInstance ai : index.getAnnotations(annotation)) {
            final AnnotationTarget target = ai.target();
            if (target instanceof ClassInfo) {
                ClassInfo ci = (ClassInfo) target;
                set.add(ci.name().toString());
            }
        }
    }

    private static String toKind(String className, AnnotationInstance entityAnnotation) {
        final AnnotationValue name = entityAnnotation.value("name");
        if (name != null && name.asString().length() > 0) {
            return name.asString();
        }

        return toKind(className);
    }

    private static String toKind(String className) {
        final int p = className.lastIndexOf(".");
        return (p > 0) ? className.substring(p + 1) : className;
    }
}
