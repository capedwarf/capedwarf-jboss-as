package org.jboss.as.capedwarf.deployment;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

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
        final ClassLoader cl = unit.getAttachment(Attachments.MODULE).getClassLoader();
        final Map<String, Callback> targets = new LinkedHashMap<>();

        final Set<String> classes = resteasyDeploymentData.getScannedResourceClasses();
        for (AnnotationInstance api : apis) {
            ClassInfo ci = (ClassInfo) api.target();
            DotName className = ci.name();
            classes.add(className.toString());

            AnnotationValue serializers = api.value("serializers");
            if (serializers != null) {
                readSerializers(cl, targets, serializers.asClassArray());
            }
        }

        // handle custom serializers last, as they override @Api::serializers
        final List<AnnotationInstance> customSerializers = getApiSerializers(unit);
        if (customSerializers != null && customSerializers.size() > 0) {
            for (AnnotationInstance ai : customSerializers) {
                ClassInfo ci = (ClassInfo) ai.target();
                final DotName className = ci.name();
                targets.put(className.toString(), new Callback() {
                    public void applyCtor(CtConstructor ctor) throws Exception {
                        ctor.setBody(String.format("{super(%s.class);}", className.toString()));
                    }
                });
            }
        }

        if (targets.size() > 0) {
            final Set<String> providers = resteasyDeploymentData.getScannedProviderClasses();

            for (Map.Entry<String, Callback> entry : targets.entrySet()) {
                providers.add(generateProvider(cl, entry.getKey(), entry.getValue()));
            }
        }
    }

    protected void readSerializers(ClassLoader cl, Map<String, Callback> targets, Type[] types) {
        try {
            for (Type type : types) {
                final Class<?> clazz = cl.loadClass(type.name().toString());
                Method dm = findDeserialize(clazz.getName(), clazz);
                final String className = dm.getReturnType().getName();
                targets.put(className, new Callback() {
                    public void applyCtor(CtConstructor ctor) throws Exception {
                        ctor.setBody(String.format("{super(%s.class, %s.class);}", className, clazz.getName()));
                    }
                });
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Method findDeserialize(String info, Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Cannot find deserialize method: " + info);
        }
        for (Method m : clazz.getMethods()) {
            if ("deserialize".equals(m.getName()) && m.getParameterTypes().length == 1 && Modifier.isPublic(m.getModifiers()) && m.isBridge() == false) {
                return m;
            }
        }
        return findDeserialize(info, clazz.getSuperclass());
    }

    protected String generateProvider(ClassLoader cl, String className, Callback callback) {
        try {
            return generateSimpleSub(cl, className, PROVIDER, callback, DEPENDENT);
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

    protected static String generateSimpleSub(final ClassLoader cl, String ctorParamClass, String superClass, Callback callback, String... classAnnotations) throws Exception {
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
        callback.applyCtor(ctor);
        newClass.addConstructor(ctor);

        newClass.toClass();

        return classname;
    }

    private static interface Callback {
        void applyCtor(CtConstructor ctor) throws Exception;
    }
}
