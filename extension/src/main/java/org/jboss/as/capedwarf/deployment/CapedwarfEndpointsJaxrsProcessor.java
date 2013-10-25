package org.jboss.as.capedwarf.deployment;

import java.util.List;

import org.jboss.as.jaxrs.deployment.JaxrsAttachments;
import org.jboss.as.jaxrs.deployment.ResteasyDeploymentData;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.capedwarf.shared.endpoints.Converters;
import org.jboss.jandex.AnnotationInstance;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfEndpointsJaxrsProcessor extends CapedwarfEndpointsProcessor {
    protected void doDeploy(DeploymentUnit unit, List<AnnotationInstance> apis) {
        final ResteasyDeploymentData resteasyDeploymentData = unit.getAttachment(JaxrsAttachments.RESTEASY_DEPLOYMENT_DATA);
        final ClassLoader cl = unit.getAttachment(Attachments.MODULE).getClassLoader();
        final Converters converters = Converters.getInstance(cl);
        resteasyDeploymentData.getScannedResourceClasses().addAll(converters.getEndpoints());
    }
}
