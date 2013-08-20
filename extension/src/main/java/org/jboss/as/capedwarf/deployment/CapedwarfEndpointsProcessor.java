package org.jboss.as.capedwarf.deployment;

import java.util.List;

import org.jboss.as.jaxrs.deployment.JaxrsAttachments;
import org.jboss.as.jaxrs.deployment.ResteasyDeploymentData;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class CapedwarfEndpointsProcessor implements DeploymentUnitProcessor {

    private static final DotName ENDPOINT_API = DotName.createSimple("com.google.api.server.spi.config.Api");

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);

        ResteasyDeploymentData resteasyDeploymentData = unit.getAttachment(JaxrsAttachments.RESTEASY_DEPLOYMENT_DATA);

        final List<AnnotationInstance> apis = index.getAnnotations(ENDPOINT_API);
        for (AnnotationInstance api : apis) {
            ClassInfo ci = (ClassInfo) api.target();
            DotName className = ci.name();
            resteasyDeploymentData.getScannedResourceClasses().add(className.toString());
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }
}
