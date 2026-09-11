package dev.felnull.imp.client.lava;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Dependency-free regression checks; runnable without Minecraft or an Android SDK. */
public final class AndroidNativeSupportTest {
    private static int checks;

    public static void main(String[] args) {
        expect(null, detect("Linux", "aarch64", "OpenJDK Runtime Environment", "OpenJDK", Map.of(), Set.of()));
        expect(null, detect("Windows 11", "amd64", "OpenJDK", "OpenJDK", Map.of("ANDROID_HOME", "/sdk"), Set.of()));
        expect(null, detect("Linux", "amd64", "OpenJDK", "OpenJDK", Map.of("ANDROID_HOME", "/sdk"), Set.of()));
        expect(null, detect("Mac OS X", "aarch64", "OpenJDK", "OpenJDK", Map.of(), Set.of()));
        expect("android-aarch64", detect("Linux", "aarch64", "OpenJDK", "OpenJDK",
            Map.of("ANDROID_ROOT", "/system", "ANDROID_DATA", "/data"), Set.of()));
        expect("android-armhf", detect("Linux", "armv7l", "OpenJDK", "OpenJDK", Map.of(), Set.of("/system/build.prop")));
        expect("android-x86-64", detect("Linux", "amd64", "OpenJDK", "OpenJDK", Map.of(),
            Set.of("/apex/com.android.runtime/lib64/bionic/libc.so")));
        expect("android-x86", detect("Linux", "i686", "OpenJDK", "OpenJDK", Map.of(),
            Set.of("/apex/com.android.runtime/lib/bionic/libc.so")));
        expect("android-aarch64", detect("Android", "arm64-v8a", "OpenJDK", "OpenJDK", Map.of(), Set.of()));
        expect("android-armhf", detect("Linux", "arm", "Android Runtime", "Dalvik", Map.of(), Set.of()));
        expect(null, detect("Linux", "aarch64", "OpenJDK", "OpenJDK",
            Map.of("ANDROID_ROOT", "", "ANDROID_DATA", "/data"), Set.of()));
        for (String arch : new String[]{"aarch64", "arm64", "arm64-v8a", "AARCH64"}) {
            expect("android-aarch64", AndroidNativeSupport.targetForArchitecture(arch));
        }
        for (String arch : new String[]{"arm", "arm32", "armhf", "armv7", "armv7l", "armeabi-v7a", "aarch32"}) {
            expect("android-armhf", AndroidNativeSupport.targetForArchitecture(arch));
        }
        for (String arch : new String[]{"x86", "i386", "i486", "i586", "i686"}) {
            expect("android-x86", AndroidNativeSupport.targetForArchitecture(arch));
        }
        for (String arch : new String[]{"amd64", "x86_64", "x86-64"}) {
            expect("android-x86-64", AndroidNativeSupport.targetForArchitecture(arch));
        }
        expectFailure(() -> AndroidNativeSupport.targetForArchitecture("riscv64"));
        for (String path : new String[]{"/data/data/net.kdt.pojavlaunch/cache", "/data/user/0/launcher/cache",
            "/data/user_de/10/launcher/files"}) {
            expect(true, AndroidNativeSupport.isAppPrivatePath(Path.of(path)));
        }
        for (String path : new String[]{"/sdcard", "/storage/emulated/0/Android/data/launcher", "/mnt/sdcard",
            "/tmp", "relative/cache", "/data/user-external/cache", "/data/user/../../storage", "/data/local/tmp"}) {
            expect(false, AndroidNativeSupport.isAppPrivatePath(Path.of(path)));
        }
        expect(null, AndroidNativeSupport.validateLibraryLocation(null));
        expectFailure(() -> AndroidNativeSupport.createExtractionDirectory("/path/that/does/not/exist/imp-native-test"));
        expectFailure(() -> AndroidNativeSupport.validateLibraryLocation("/path/that/does/not/exist/imp-native-test"));
        System.out.println("Android native support: " + checks + " checks passed");
    }

    private static String detect(String os, String arch, String runtime, String vm,
                                 Map<String, String> environment, Set<String> files) {
        Properties properties = new Properties();
        properties.setProperty("os.name", os);
        properties.setProperty("os.arch", arch);
        properties.setProperty("java.runtime.name", runtime);
        properties.setProperty("java.vm.name", vm);
        return AndroidNativeSupport.detectSystem(properties, environment, files::contains);
    }

    private static void expect(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
        checks++;
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected an actionable failure");
        } catch (IllegalStateException expected) {
            checks++;
        }
    }
}
