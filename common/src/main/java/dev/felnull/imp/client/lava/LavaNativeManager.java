package dev.felnull.imp.client.lava;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures Lavaplayer native libraries are available from bundled resources.
 */
public class LavaNativeManager {

    private static final Logger LOGGER = LogManager.getLogger(
        LavaNativeManager.class
    );
    private static final LavaNativeManager INSTANCE = new LavaNativeManager();

    private LavaNativeManager() {}

    public static LavaNativeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Checks for an already-extracted native library. If it is not present, Lavaplayer's
     * resource loader will extract the bundled native from /natives in the mod jar.
     *
     * @param osAndArch OS and architecture identifier (e.g. "linux-x86-64")
     * @param name Name of the native library file to load
     * @return true when the library is already extracted, false when resource extraction should be used
     */
    public boolean load(String osAndArch, String name) {
        Path nativeLibPath = LavaPlayerLoader.getNaiveLibraryFolder()
            .resolve(osAndArch)
            .resolve(name);
        boolean exists = Files.exists(nativeLibPath);

        if (exists && isUnixLibrary(nativeLibPath)) {
            File nativeFile = nativeLibPath.toFile();
            if (!nativeFile.canExecute()) {
                nativeFile.setExecutable(true, false);
            }
        }

        LOGGER.info(
            "LavaPlayer native {} for {} {} in configured native directory; bundled resources will be used when absent.",
            name,
            osAndArch,
            exists ? "found" : "not found"
        );
        return exists;
    }

    private boolean isUnixLibrary(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".so") || fileName.endsWith(".dylib");
    }
}
