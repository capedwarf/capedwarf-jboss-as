package org.jboss.as.capedwarf.deployment;

import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class CapedwarfEndpointsProcessor extends CapedwarfWebDeploymentUnitProcessor {
    private static final DotName ENDPOINT_API = DotName.createSimple("com.google.api.server.spi.config.Api");
    private static final DotName ENDPOINT_API_METHOD = DotName.createSimple("com.google.api.server.spi.config.ApiMethod");
    private static final DotName ENDPOINT_API_TRANSFORMER = DotName.createSimple("com.google.api.server.spi.config.ApiTransformer");

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        final List<AnnotationInstance> apis = getApis(unit);
        if (apis != null && apis.size() > 0) {
            doDeploy(unit, apis);
        }
    }

    static List<AnnotationInstance> getApis(DeploymentUnit unit) {
        CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        return index.getAnnotations(ENDPOINT_API);
    }

    static List<AnnotationInstance> getApiMethods(DeploymentUnit unit) {
        CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        return index.getAnnotations(ENDPOINT_API_METHOD);
    }

    static List<AnnotationInstance> getApiTransformers(DeploymentUnit unit) {
        CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        return index.getAnnotations(ENDPOINT_API_TRANSFORMER);
    }

    static boolean hasApis(DeploymentUnit unit) {
        List<AnnotationInstance> apis = getApis(unit);
        return (apis != null && apis.size() > 0);
    }

    protected abstract void doDeploy(DeploymentUnit unit, List<AnnotationInstance> apis);
}
