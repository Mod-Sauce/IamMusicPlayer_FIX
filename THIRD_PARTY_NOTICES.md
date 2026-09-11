# Licensing and third-party notices

## Project license and scope

IamMusicPlayerRenewed retains its existing GNU Lesser General Public License,
version 3 (LGPLv3), in `LICENSE`. LGPLv3 incorporates GPLv3; the accompanying
GPLv3 text is in `licenses/GPL-3.0.txt`. Existing copyright, authorship and
license notices in the original project and its dependencies remain intact.
This document does not replace those notices or relicense third-party code.

## Android audio components

This inventory covers the Android native addition, not a complete audit of
all pre-existing Java dependencies, assets, desktop codecs, or required mods.
Copyright holders and contributors are identified in the verbatim component
license/AUTHORS files below, which must be retained with redistributions.

| Component | Version/revision | License/notice location |
| --- | --- | --- |
| IMP Android launcher integration | This repository | LGPLv3, `LICENSE` |
| Native build tooling | `scripts/android-natives/` | Apache-2.0, `scripts/android-natives/LICENSE` |
| LavaPlayer JNI baseline | 2.2.4; `2fc96d70b9f632ceb7b8387b69a874cbf86c05c0` | Apache-2.0, `licenses/android/lavaplayer-2.2.4/LICENSE` |
| lolicode LavaPlayer native fork | `468c8e9f1a65364c6dcf122ecaaa0bcce8a37c23` | Apache-2.0, `licenses/android/lavaplayer-fork/LICENSE` |
| Opus | 1.6.1 | BSD-style and additional terms in `licenses/android/opus/COPYING` |
| mpg123 | 1.33.7 | LGPL-2.1 and file-specific exceptions, `licenses/android/mpg123/COPYING` |
| Ogg | 1.3.6 | BSD-style, `licenses/android/ogg/COPYING` |
| Vorbis | 1.3.7 | BSD-style, `licenses/android/vorbis/COPYING` |
| libsamplerate | 0.2.2 | BSD-2-Clause, `licenses/android/samplerate/COPYING` |
| FDK AAC | 2.0.3 | Custom FDK license, `licenses/android/fdkaac/LICENSE-header.txt` and `NOTICE` |
| Android NDK runtime/toolchain notices | r27d / 27.3.13750724 | Multiple licenses, `licenses/android/ndk/NOTICE` and `NOTICE.toolchain` |

The NDK notice collection is retained conservatively from the build toolchain;
its inclusion does not mean every component listed there is linked into IMP.
The native library uses static C++ runtime linkage. The license of a wrapper
or build script does not replace the licenses of statically linked codecs.

mpg123's requested notice:

> Copyright (c) 1995-2020 by Michael Hipp and others,
> free software under the terms of the LGPL v2.1

FDK AAC's standalone upstream `NOTICE` contains only an introduction. The
complete license is preserved verbatim from the leading comment in the 2.0.3
`libAACdec/src/aacdecoder.cpp` source file as `LICENSE-header.txt`. It includes
source-availability requirements, restrictions on using the Fraunhofer name,
and an explicit absence of a patent license. Do not interpret these notices
as a patent license or a blanket statement of compatibility with every
copyleft combination; obtain appropriate licensing advice before release.

## Local modifications to the native bridge

Android integration modifications prepared on 2026-09-06:

- Select Android/Bionic resources and extract into launcher-private storage.
- Adapt the fork's native build to retain IMP's
  `com_sedmelluq_discord_lavaplayer` JNI namespace.
- Restore the two-argument C entry point for `SampleRateLibrary.create` to
  match the unchanged LavaPlayer 2.2.4 Java interface.
- Correct the Vorbis JNI `initialise` return type to `jboolean`.
- Compile with generated JNI headers and validate binary exports/linkage.

These are local adaptations, not an upstream MoeMusic or LavaPlayer release.
The modifications are implemented by `scripts/android-natives/compatibility.py`
and `build.py`; their generated source diffs are retained in the rebuild kits.
No FDK codec algorithm modifications were made by this integration; the build
uses the fork's CMake support. Preserve all original source-header notices.

## Notices in binary distributions

Both mod jars include `META-INF/imp-licenses/`, containing this document,
`LICENSE`, the incorporated GPLv3 text, and the checked-in component notices.
Android-capable jars additionally retain ABI-specific provenance, validation
reports and upstream notices under `META-INF/android-natives/<target>/`.
