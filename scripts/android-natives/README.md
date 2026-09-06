# Android connector resource pipeline

This directory adds build/package tooling only. It does **not** change
`dev.arbjerg:lavaplayer:2.2.4`, integrate the fork's Java code, replace desktop
natives, or automatically install binaries. A successful workflow produces
resources; no binaries are bundled merely by adding these scripts.

## Compatibility findings (checked against actual sources)

Baseline: [lavalink-devs/lavaplayer tag 2.2.4](https://github.com/lavalink-devs/lavaplayer/tree/2.2.4),
resolved commit `2fc96d70b9f632ceb7b8387b69a874cbf86c05c0`.
Basis: [lolicode-org/lavaplayer](https://github.com/lolicode-org/lavaplayer/tree/468c8e9f1a65364c6dcf122ecaaa0bcce8a37c23),
commit `468c8e9f1a65364c6dcf122ecaaa0bcce8a37c23`, specifically its
`build-android` workflow, `natives/scripts/build-linux-deps.sh`, and CMake files.
Both source archives are SHA-256 pinned in `build.py`; no branch tips are used.

| Finding | Staged-build action |
| --- | --- |
| Fork CMake defaults to `org_lolicode_lavaplayer`; `connector.h` introduces configurable name-expansion macros | Explicitly set `LAVA_JNI_PREFIX=com_sedmelluq_discord_lavaplayer` |
| Only native Java method difference: `SampleRateLibrary.create(II)J` becomes `(II[I)J`; fork C accesses the additional array | Remove that C argument and array write, restoring 2.2.4 ABI while retaining `uintptr_t` pointer conversions |
| Both trees implement `VorbisDecoderLibrary.initialise` with C `jint`, but Java requires `boolean` / JNI `jboolean` | Correct staged C return type to `jboolean` |
| Other C changes include pointer-width conversions, JNI naming macros, explicit `jint` progress values, and Android build support | Retain fork changes; verify all adapted signatures against 2.2.4 |
| Fork Java wrappers also change AAC error handling and resampler error reporting | Do not integrate any fork Java classes |

**Unadapted fork binaries are not compatible.** `compatibility.py` generates JNI
headers with `javac -h` from the unchanged native declaration classes in **both**
repositories (only the non-native loader is stubbed). It checks all **26 methods
in 7 classes**, permits exactly the differences above, records Java/C/header
diffs, and checks the adapted C signatures. The actual Android C compilation
also includes the generated 2.2.4 headers, catching signature conflicts.

This establishes source JNI compatibility after adaptation, not behavioral
identity: codec versions differ from the old native distribution. Device tests
are still required for AAC, MP3, Opus encode/decode, Vorbis, resampling, native
resource destruction, and CPU statistics (`/proc` access can be restricted).

## Build and artifacts

Use **Actions → Android Lavaplayer natives → Run workflow**. It also runs for
changes to these scripts or its own workflow. Four isolated Ubuntu jobs use NDK
`27.3.13750724`, Android API **21**, static C++ runtime, and flexible-page-size
support. Actions are commit-pinned. Host Ubuntu/CMake packages are not fully
hermetic; CMake/compiler versions are recorded, and bit-identical builds are not
promised. NDK installation uses Google's SDK manager package validation.

| Android ABI | Resource path beneath `resources/` |
| --- | --- |
| `arm64-v8a` | `natives/android-aarch64/libconnector.so` |
| `armeabi-v7a` | `natives/android-armhf/libconnector.so` |
| `x86` | `natives/android-x86/libconnector.so` |
| `x86_64` | `natives/android-x86-64/libconnector.so` |

The final **`android-natives-resources`** artifact contains all four paths,
`resources/META-INF/android-natives/<target>/` with provenance, licenses,
validation reports and pipeline sources, plus `SHA256SUMS`. It is emitted only
if every ABI succeeds. Separate **`android-rebuild-<abi>`** artifacts retain
source archives, modified native sources, dependencies, static libraries,
connector object files, CMake link commands and pipeline scripts for rebuilding
and relinking. Retain these alongside any published binaries; CI artifacts
expire after 30 days. This workflow neither publishes releases nor modifies a
working tree's source resources. A subsequent job stages the combined overlay,
runs the launcher checks and Gradle build, verifies the final remapped jars,
and uploads **`android-capable-mods`**. The workflow itself still needs a run on
GitHub; the local native and mod builds have been validated separately.

Checks per binary: correct ELF machine/class and shared-object type, exact set
of defined public JNI exports, Bionic `libc.so` dependency, no glibc/musl symbol
versions, no unexpected runtime dependencies (including `libc++_shared.so`), no
text relocations/RPATH/RUNPATH, and link-time `--no-undefined`. ELF checks do not
replace loading and codec smoke tests on Android, including 16 KB-page devices.

### Local build (Linux x86-64)

Install Python **3.12+**, JDK 17, CMake 3.22+, make, autoconf, automake, libtool,
pkg-config, curl, and Android SDK command-line tools. Install the pinned NDK:

```sh
sdkmanager --install 'ndk;27.3.13750724'
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.3.13750724"
python3 -B scripts/android-natives/test_pipeline.py
python3 -B scripts/android-natives/build.py --audit-only
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  python3 -B scripts/android-natives/build.py --abi "$abi"
done
```

Downloads go to ignored `build/android-natives/downloads/`, are verified even
on cache hits, and cover the pinned fork/baseline archives plus Opus 1.6.1,
mpg123 1.33.7, Ogg 1.3.6, Vorbis 1.3.7, libsamplerate 0.2.2, FDK AAC 2.0.3.
Dependency SHA-256 values come from the pinned fork's `natives/versions.properties`
and are copied into provenance. Fetches contact GitHub, Xiph, mpg123, SourceForge
and their download redirects. No credentials or application data are uploaded
by local builds. CI uploads the generated artifacts to GitHub.

Local output: `build/android-natives/<abi>/package/`. Builds deliberately refuse
to reuse an existing ABI staging directory (avoids mixed architectures and
stale patched files). To retry, remove **only** that generated ABI directory;
for another audit remove `build/android-natives/audit/`. Keep the download cache.
Do not run multiple builds of the same ABI concurrently.

### Use the artifact in a local build/release

Download and extract `android-natives-resources` into
`native-resources/android/` (outside Gradle's cleaned `build/` directory). Verify before building:

```sh
(cd native-resources/android && sha256sum -c SHA256SUMS)
./gradlew build -PwithAndroidNatives=true
```

Gradle includes this directory as an additional resource input for both loaders.
Do **not** also copy it into `common/src/main/resources`: that creates duplicate
resources. `-PwithAndroidNatives=true` fails the build if any ABI or provenance
file is missing. An explicit `-PandroidNativesDir=/absolute/path/to/resources`
also requires the complete resource set. Keep that input outside `build/` if
running `clean`. Native resources are ignored by Git and must be staged again
on a fresh checkout. Without an overlay, ordinary builds remain desktop-only
and print a notice; the Android workflow builds and verifies Android-capable
mod jars. The existing Forgejo/Crow workflows remain desktop-only unless an
overlay is staged and the required-build flag is added.

For local per-ABI builds, verify each `package/SHA256SUMS`, then collect the
contents of all four `package/resources/` directories in
`native-resources/android/resources/`. Preserve the matching rebuild kits.

The IMP loader detects Android launchers, selects the `android-*` resource
matching the JVM architecture, and extracts into launcher-private storage.
See the main README for runtime overrides and device testing. Do not rename
Android binaries to `linux-*` or use unmodified MoeMusic binaries.

## Licenses and redistribution

See the repository's `THIRD_PARTY_NOTICES.md` and `RELEASING.md` for the license
scope and release checklist. No MoeMusic AGPL core code is incorporated. Both
mod jars include the checked-in notices under `META-INF/imp-licenses/`.

This tooling is Apache-2.0; see `LICENSE`. It adapts the Apache-2.0 fork pipeline
identified above. The two C compatibility edits and generated-header inclusion
are local modifications, not upstream releases. Full upstream license/notice
files are copied into each resource package. The build also extracts the full
FDK license from its source header (upstream `NOTICE` is only an introduction)
and retains the NDK notices for statically linked runtime code. Update the
checked-in `licenses/android/` inventory whenever pinned components change.
The entire connector is **not**
solely Apache-2.0: statically linked codecs have their own terms, including
mpg123's LGPL and FDK AAC's custom license/patent provisions. Review those files
before distribution; notices alone do not discharge source/relink obligations,
and this pipeline does not grant codec patent licenses.

Publish the matching rebuild kits with binary releases, not just transient CI
links. They contain full dependency archives and relinkable objects. For a
modified mpg123 relink, build a replacement `libmpg123.a` with the same NDK,
ABI/API and PIC flags, then use the kit's
`cmake/connector/CMakeFiles/connector.dir/link.txt` with that archive and the
included connector objects/static dependencies. Rewrite absolute staging/NDK
paths for your machine and execute from the kit's CMake build directory.
Alternatively rebuild from the included sources with `build.py` (update the
source URL/hash deliberately for modified dependencies). Re-run all ELF/JNI
checks and Android tests after any relink. Preserve copyright notices, supply
the applicable license texts and corresponding sources, and review all other
redistribution requirements for your release.
