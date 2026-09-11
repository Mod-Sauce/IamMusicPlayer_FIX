package dev.felnull.imp.client.lava;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

/** Android launchers run OpenJDK on Bionic, often reporting their OS as Linux. */
final class AndroidNativeSupport {
    private AndroidNativeSupport() {
    }

    static String detectSystem() {
        return detectSystem(System.getProperties(), System.getenv(), path -> Files.exists(Path.of(path)));
    }

    static String detectSystem(Properties properties, Map<String, String> environment, Predicate<String> exists) {
        String os = properties.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String runtime = properties.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        String vm = properties.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        boolean android = os.contains("android") || runtime.contains("android") || vm.contains("dalvik");
        if (os.contains("linux")) {
            android |= nonBlank(environment.get("ANDROID_ROOT")) && nonBlank(environment.get("ANDROID_DATA"));
            android |= exists.test("/system/build.prop") || exists.test("/apex/com.android.runtime/lib64/bionic/libc.so")
                || exists.test("/apex/com.android.runtime/lib/bionic/libc.so");
        }
        if (!android) return null;
        return targetForArchitecture(properties.getProperty("os.arch", ""));
    }

    static String targetForArchitecture(String architecture) {
        return switch (architecture.toLowerCase(Locale.ROOT)) {
            case "aarch64", "arm64", "arm64-v8a" -> "android-aarch64";
            case "arm", "arm32", "armhf", "armv7", "armv7l", "armeabi-v7a", "aarch32" -> "android-armhf";
            case "x86", "i386", "i486", "i586", "i686" -> "android-x86";
            case "amd64", "x86_64", "x86-64" -> "android-x86-64";
            default -> throw new IllegalStateException("Unsupported Android JVM architecture: " + architecture
                + ". Use a launcher runtime for arm64-v8a, armeabi-v7a, x86 or x86_64.");
        };
    }

    static Path createExtractionDirectory(String explicitPath) {
        if (nonBlank(explicitPath)) {
            try {
                return createPrivateDirectory(Path.of(explicitPath));
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Android LavaPlayer extraction path is not usable: " + explicitPath
                    + ". Choose a writable directory in the launcher's internal app storage, not /sdcard or /storage.", e);
            }
        }

        String[] candidates = {
            System.getProperty("java.io.tmpdir"),
            System.getenv("TMPDIR"),
            System.getProperty("user.home")
        };
        for (String candidate : candidates) {
            if (!nonBlank(candidate)) continue;
            try {
                return createPrivateDirectory(Path.of(candidate));
            } catch (IOException | IllegalArgumentException ignored) {
                // Launchers differ in which of these locations is private and writable.
            }
        }
        throw new IllegalStateException("No writable internal storage directory for Android LavaPlayer natives. "
            + "Set -Dlava.native.extractPath to a directory inside the launcher's private app storage. "
            + "The Minecraft directory on /sdcard or /storage cannot be used for native loading.");
    }

    private static Path createPrivateDirectory(Path base) throws IOException {
        Path realBase = base.toRealPath();
        if (!isAppPrivatePath(realBase) || !Files.isDirectory(realBase) || !Files.isWritable(realBase)) {
            throw new IOException("Not a writable Android app-private directory: " + realBase);
        }
        // A private, random parent isolates the old LavaPlayer loader's timestamp-based extraction.
        return Files.createTempDirectory(realBase, "imp-lava-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    static boolean isAppPrivatePath(Path path) {
        Path normalized = path.normalize();
        return normalized.isAbsolute() && (normalized.startsWith("/data/data")
            || normalized.startsWith("/data/user") || normalized.startsWith("/data/user_de"));
    }

    static String validateLibraryLocation(String location) {
        if (location == null) return null;
        try {
            Path realPath = Path.of(location).toRealPath();
            if (!isAppPrivatePath(realPath)) {
                throw new IOException("Native libraries must be in Android app-private storage");
            }
            return realPath.toString();
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load Android native library from " + location
                + ". Use an existing path in the launcher's internal app storage.", e);
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
