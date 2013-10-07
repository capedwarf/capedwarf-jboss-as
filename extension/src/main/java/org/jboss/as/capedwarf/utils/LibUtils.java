package org.jboss.as.capedwarf.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.capedwarf.shared.compatibility.Compatibility;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class LibUtils {
    private static final AttachmentKey<List<VirtualFile>> LIBS_KEY = AttachmentKey.create(List.class);

    private LibUtils() {
    }

    private static final VirtualFileFilter JARS_VFS = new VirtualFileFilter() {
        public boolean accepts(VirtualFile file) {
            return file.getName().endsWith(".jar");
        }
    };

    private static List<VirtualFile> getChildren(VirtualFile lib) {
        try {
            return lib.getChildren(JARS_VFS);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<VirtualFile> getLibs(DeploymentUnit unit) {
        List<VirtualFile> libs = unit.getAttachment(LIBS_KEY);
        if (libs == null) {
            final ResourceRoot root = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
            final VirtualFile lib = root.getRoot().getChild("WEB-INF/lib");
            libs = lib.exists() ? getChildren(lib) : Collections.<VirtualFile>emptyList();
            unit.putAttachment(LIBS_KEY, libs);
        }
        return libs;
    }

    public static boolean hasLibrary(DeploymentUnit unit, String library) {
        VirtualFile lib = findLibrary(unit, library);
        return (lib != null && lib.exists());
    }

    public static VirtualFile findLibrary(DeploymentUnit unit, String library) {
        for (VirtualFile lib : getLibs(unit)) {
            if (lib.getName().contains(library))
                return lib;
        }
        return null;
    }

    public static ModuleDependency createModuleDependency(ModuleLoader loader, ModuleIdentifier moduleIdentifier) {
        return new ModuleDependency(loader, moduleIdentifier, false, false, true, false);
    }

    public static VirtualFile getCompatibilityFile(DeploymentType type, VirtualFile root) {
        String path = ((type == DeploymentType.EAR) ? "META-INF/" : "WEB-INF/classes/") + Compatibility.FILENAME;
        return root.getChild(path);
    }

    public static Compatibility getCompatibility(DeploymentType type, VirtualFile root) throws IOException {
        VirtualFile cf = getCompatibilityFile(type, root);
        return (cf.exists()) ? Compatibility.readCompatibility(cf.openStream()) : null;
    }
}
