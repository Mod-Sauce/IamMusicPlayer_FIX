package dev.felnull.imp.client.lava;

import com.sedmelluq.lava.common.natives.ResourceNativeLibraryBinaryProvider;
import com.sedmelluq.lava.common.natives.architecture.SystemType;

import java.io.InputStream;

public class IMPResourceNativeLibraryBinaryProvider extends ResourceNativeLibraryBinaryProvider {
    private static final String DEFAULT_RESOURCE_ROOT = "/natives/";

    public IMPResourceNativeLibraryBinaryProvider(Class<?> classLoaderSample) {
        super(classLoaderSample, DEFAULT_RESOURCE_ROOT);
    }

    @Override
    public InputStream getLibraryStream(SystemType systemType, String libraryName) {
        InputStream stream = super.getLibraryStream(systemType, libraryName);
        if (stream == null && systemType.formatSystemName().startsWith("android-")) {
            throw new UnsatisfiedLinkError("This IMP build does not include /natives/"
                + systemType.formatSystemName() + "/" + systemType.formatLibraryName(libraryName)
                + ". Install a build packaged with the Android native resources. "
                + "Desktop Linux and unmodified MoeMusic connector binaries are not compatible.");
        }
        return stream;
    }
}
