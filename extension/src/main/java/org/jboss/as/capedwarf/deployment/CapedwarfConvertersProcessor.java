package org.jboss.as.capedwarf.deployment;

import java.util.List;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.capedwarf.shared.endpoints.Converters;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfConvertersProcessor extends CapedwarfEndpointsProcessor {
    protected void doDeploy(DeploymentUnit unit, List<AnnotationInstance> apis) {
        final ClassLoader cl = unit.getAttachment(Attachments.MODULE).getClassLoader();

        final Converters converters = Converters.getInstance(cl);
        final CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);

        for (AnnotationInstance api : apis) {
            ClassInfo ci = (ClassInfo) api.target();
            DotName className = ci.name();

            converters.addEndpointClass(className.toString());

            Set<ClassInfo> subs = index.getAllKnownSubclasses(className);
            for (ClassInfo sub : subs) {
                converters.addEndpointClass(sub.name().toString());
            }

            AnnotationValue transformers = api.value("transformers");
            if (transformers != null) {
                for (Type type : transformers.asClassArray()) {
                    converters.add(type.name().toString());
                }
            }
        }

        // register all result types
        final List<AnnotationInstance> apiMethods = getApiMethods(unit);
        if (apiMethods != null && apiMethods.size() > 0) {
            for (AnnotationInstance ai : apiMethods) {
                MethodInfo target = (MethodInfo) ai.target();
                converters.addResultType(target.returnType().name().toString());
            }
        }

        // handle custom transformers last, as they override @Api::transformers
        final List<AnnotationInstance> apiTransformers = getApiTransformers(unit);
        if (apiTransformers != null && apiTransformers.size() > 0) {
            for (AnnotationInstance ai : apiTransformers) {
                converters.add(ai.value().asClass().name().toString());
            }
        }
    }
}
