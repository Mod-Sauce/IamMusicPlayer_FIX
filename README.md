# IamMusicPlayer (IMP)

The Ikisugi Music Player MOD (Minecraft Forge/Fabric MOD)



## License and redistribution

The project retains its existing [LGPLv3 license](LICENSE), with the incorporated
[GPLv3 text](licenses/GPL-3.0.txt) supplied alongside it.

See [third-party notices](THIRD_PARTY_NOTICES.md) for component licenses. The Android codecs retain their own terms, including mpg123's LGPL
and FDK AAC's custom source-availability and patent provisions. Notices are
included in both mod jars under `META-INF/imp-licenses/`.

## Android Java launchers (Pojav and similar)

Android-capable builds include Bionic audio-decoder libraries for `arm64-v8a`,
`armeabi-v7a`, `x86`, and `x86_64`. Use a launcher that can run Minecraft Java
1.21.1 with Java 21 and the appropriate Fabric/NeoForge loader. This is not a
Bedrock mod.

### Installation and troubleshooting

Install the matching Fabric or NeoForge jar from the **`android-capable-mods`**
artifact of the **Android Lavaplayer natives** GitHub workflow, along with IMP's
normal required mods. A desktop-only build does not contain the Android codecs.
Android is normally detected automatically, even when the launcher reports Linux.
The target follows the **JVM architecture**, not just the phone's hardware.

If detection fails, add the matching launcher JVM argument:

| JVM ABI | Argument |
| --- | --- |
| 64-bit ARM | `-Dlava.native.system=android-aarch64` |
| 32-bit ARM | `-Dlava.native.system=android-armhf` |
| 32-bit x86 | `-Dlava.native.system=android-x86` |
| 64-bit x86 | `-Dlava.native.system=android-x86-64` |

Native libraries are extracted into a private temporary directory under the
launcher's internal app storage, using `java.io.tmpdir`, `TMPDIR`, or `user.home`.
If none is usable, set `-Dlava.native.extractPath=` to an **existing writable
internal directory belonging to your launcher**. The startup error explains this
requirement. Do not use `/sdcard`, `/storage`, or the shared-storage Minecraft
folder: those paths cannot be used for Android native loading. Do not substitute
desktop Linux ARM libraries or unmodified MoeMusic connector libraries.

For troubleshooting, retain `latest.log` and note the launcher version, runtime,
mod loader, Android version, and JVM architecture. Before relying on a build,
test MP3, AAC/M4A, Ogg/Vorbis and Opus playback, seeking, pause/resume, volume,
track changes, and world disconnect/reconnect. Check local/HTTP audio separately
from YouTube so service/network errors are not mistaken for decoder failures.

### Building with Android support

A fresh clone does not contain the Android native libraries. Build them first,
or use a matching prebuilt native artifact, before building the mod.

#### 1. Clone the repository

```sh
git clone https://codeberg.org/Mod-Sauce/IamMusicPlayerRenewed.git
cd IamMusicPlayerRenewed
```

Use a branch/revision that includes Android support. All commands below run
from the repository root.

#### 2. Install the build tools

Building the Android libraries locally requires a **Linux x86-64** machine with:

- **JDK 21**, with `JAVA_HOME` set and `java`/`javac` on `PATH`.
- **Python 3.12+**, CMake 3.22+, make, autoconf, automake, libtool, pkg-config,
  curl, and `sha256sum`.
- **Android SDK command-line tools**, with `ANDROID_HOME` pointing to the SDK
  directory and `sdkmanager` on `PATH`.

Install the pinned Android NDK and accept the SDK licenses when prompted:

```sh
sdkmanager --licenses
sdkmanager --install 'ndk;27.3.13750724'
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.3.13750724"
```

The first build downloads the NDK, codec sources, and Gradle dependencies;
allow several gigabytes of free space. Gradle itself is supplied by the wrapper.

#### 3. Build and stage all four Android targets

Run this block in a shell; `set -e` stops it if a build or checksum check fails:

```sh
set -e
python3 -B scripts/android-natives/test_pipeline.py
mkdir -p native-resources/android/resources

for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    python3 -B scripts/android-natives/build.py --abi "$abi"
    (cd "build/android-natives/$abi/package" && sha256sum -c SHA256SUMS)
    cp -R "build/android-natives/$abi/package/resources/." native-resources/android/resources/
done
```

This stages the libraries and their notices/provenance outside Gradle's
`build/` directory, so the staged resources survive `gradlew clean`. Do not
also copy them into `common/src/main/resources`, which would create duplicates.
For a retry, remove only the failed ABI's staging directory under
`build/android-natives/` before rerunning its build; successful ABI builds do
not need to be repeated. Keep the source/relink files if distributing binaries.

**Alternative: use prebuilt natives.** Download `android-natives-resources`
from a successful **Android Lavaplayer natives** GitHub workflow run matching
your checkout. Extract it into `native-resources/android/`, so that
`SHA256SUMS` and `resources/` are directly inside that directory, then verify:

```sh
(cd native-resources/android && sha256sum -c SHA256SUMS)
```

With this alternative, you can skip the local NDK/toolchain setup and native
compilation; you still need JDK 21 to build the mod.

#### 4. Build the mod

```sh
sh gradlew build -PwithAndroidNatives=true
```

The final jars are in `fabric/build/libs/` and `neoforge/build/libs/`. Use the
jar without a `-sources`, `-dev`, or `-dev-shadow` suffix. The same jars retain
desktop support.

`-PwithAndroidNatives=true` makes the build fail if any Android target or its
provenance is missing. Without a staged native overlay, ordinary builds remain
desktop-only. The Android workflow performs the native build, staging, and mod
packaging automatically. See [the native build guide](scripts/android-natives/README.md)
for detailed validation and source/relink instructions.

# Contributor

``
fabric/src/main/generated
``  
``
forge/src/generated
``  
Do not edit these generated directories directly.  
If you want to change the contents, please change the IamMusicPlayerDataGenerator or change the resources folder.

## Transration

If a kind person translates it, please open an Pull requests!

[Templates used for translation](https://github.com/TeamFelnull/IamMusicPlayer/tree/master/common/src/main/resources/assets/iammusicplayer/lang)  
[Template used for translating patchouli](https://github.com/TeamFelnull/IamMusicPlayer/tree/master/resources/data/iammusicplayer/patchouli_books/manual)

# Using libraries

The following libraries are used for this mod,  
but they are included in the mod's jar file and do not need to be installed separately.

[LavaPlayer](https://github.com/sedmelluq/lavaplayer)  
[LavaPlayer (fork)](https://github.com/walkyst/lavaplayer-fork)   
[LavaPLayerNatives (macOS M1)](https://github.com/aikaterna/lavaplayer-natives)  
[Felnull Java library](https://github.com/TeamFelnull/FelNullJavaLibrary)  
[Java Youtube Downloader](https://github.com/sealedtx/java-youtube-downloader)  
[Mp3agic](https://github.com/mpatric/mp3agic)  
etc..

# Download

[Github](https://github.com/Mod-Sauce/IamMusicPlayer_FIX/releases/)                                                 
[Modrinth](https://modrinth.com/mod/iam-music-player-renewed) (Recommended)   
[Curseforge](https://www.curseforge.com/minecraft/mc-mods/iammusicplayer-renewed)