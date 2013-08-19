package org.jboss.as.capedwarf.deployment;

import java.util.List;
import java.util.Set;

import org.jboss.as.jaxrs.deployment.JaxrsAttachments;
import org.jboss.as.jaxrs.deployment.ResteasyDeploymentData;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfEndpointsJaxrsProcessor extends CapedwarfEndpointsProcessor {
    protected void doDeploy(DeploymentUnit unit, List<AnnotationInstance> apis) {
        ResteasyDeploymentData resteasyDeploymentData = unit.getAttachment(JaxrsAttachments.RESTEASY_DEPLOYMENT_DATA);
        final Set<String> classes = resteasyDeploymentData.getScannedResourceClasses();
        for (AnnotationInstance api : apis) {
            ClassInfo ci = (ClassInfo) api.target();
            DotName className = ci.name();
            classes.add(className.toString());
        }
    }
}
