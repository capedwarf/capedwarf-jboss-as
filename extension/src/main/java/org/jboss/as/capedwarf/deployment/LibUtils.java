package org.jboss.as.capedwarf.deployment;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
final class LibUtils {
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
        for (VirtualFile lib : getLibs(unit)) {
            if (lib.getName().contains(library))
                return true;
        }
        return false;
    }

    public static ModuleDependency createModuleDependency(ModuleLoader loader, ModuleIdentifier moduleIdentifier) {
        return new ModuleDependency(loader, moduleIdentifier, false, false, true, false);
    }
}
