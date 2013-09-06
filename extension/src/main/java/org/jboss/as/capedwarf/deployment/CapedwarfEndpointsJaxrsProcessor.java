package org.jboss.as.capedwarf.deployment;

import java.util.List;
import java.util.Random;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.LoaderClassPath;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import org.jboss.as.jaxrs.deployment.JaxrsAttachments;
import org.jboss.as.jaxrs.deployment.ResteasyDeploymentData;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CapedwarfEndpointsJaxrsProcessor extends CapedwarfEndpointsProcessor {
    private static final Random RANDOM = new Random();
    private static final String PROVIDER = "org.jboss.capedwarf.endpoints.EndpointsSerializerProvider";
    private static final String DEPENDENT = "javax.enterprise.context.Dependent";

    protected void doDeploy(DeploymentUnit unit, List<AnnotationInstance> apis) {
        final ResteasyDeploymentData resteasyDeploymentData = unit.getAttachment(JaxrsAttachments.RESTEASY_DEPLOYMENT_DATA);

        final Set<String> classes = resteasyDeploymentData.getScannedResourceClasses();
        for (AnnotationInstance api : apis) {
            ClassInfo ci = (ClassInfo) api.target();
            DotName className = ci.name();
            classes.add(className.toString());
        }

        final List<AnnotationInstance> serializers = getApiSerializers(unit);
        if (serializers != null && serializers.size() > 0) {
            final ClassLoader cl = unit.getAttachment(Attachments.MODULE).getClassLoader();
            final Set<String> providers = resteasyDeploymentData.getScannedProviderClasses();
            for (AnnotationInstance ai : serializers) {
                providers.add(generateProvider(cl, ai));
            }
        }
    }

    protected String generateProvider(ClassLoader cl, AnnotationInstance ai) {
        try {
            ClassInfo ci = (ClassInfo) ai.target();
            DotName className = ci.name();
            return generateSimpleSub(cl, className.toString(), PROVIDER, DEPENDENT);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String getSimpleName(String className) {
        int p = className.lastIndexOf(".");
        return (p > 0) ? className.substring(p + 1) : className;
    }

    protected static String toDesc(String className) {
        return "L" + className.replace('.', '/');
    }

    protected static String generateSimpleSub(final ClassLoader cl, String ctorParamClass, String superClass, String... classAnnotations) throws Exception {
        final ClassPool pool = new ClassPool() {
            @Override
            public ClassLoader getClassLoader() {
                return cl;
            }
        };
        pool.appendClassPath(new LoaderClassPath(cl));

        int p = superClass.lastIndexOf(".");
        String prefix = (p > 0) ? superClass.substring(0, p) : "";
        String suffix = (p > 0) ? superClass.substring(p + 1) : superClass;
        String classname = prefix + "." + getSimpleName(ctorParamClass) + Math.abs(RANDOM.nextInt()) + suffix;

        CtClass newClass = pool.makeClass(classname);
        newClass.setSuperclass(pool.get(superClass));
        SignatureAttribute.ClassSignature cs = SignatureAttribute.toClassSignature(toDesc(superClass) + "<" + toDesc(ctorParamClass) + ";>;");
        newClass.setGenericSignature(cs.encode());

        ClassFile classFile = newClass.getClassFile();
        ConstPool constPool = classFile.getConstPool();
        AnnotationsAttribute attribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        for (String ann : classAnnotations) {
            attribute.addAnnotation(new Annotation(ann, constPool));
        }

        CtConstructor ctor = new CtConstructor(new CtClass[0], newClass);
        ctor.setBody(String.format("{super(%s.class);}", ctorParamClass));
        newClass.addConstructor(ctor);

        newClass.toClass();

        return classname;
    }
}
