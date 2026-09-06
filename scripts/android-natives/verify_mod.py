#!/usr/bin/env python3
"""Verify the final remapped jars, not just an intermediate resource directory."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ANDROID_TARGETS = ("android-aarch64", "android-armhf", "android-x86", "android-x86-64")
DESKTOP_RESOURCES = (
    "natives/linux-x86-64/libconnector.so",
    "natives/linux-aarch64/libconnector.so",
    "natives/darwin/libconnector.dylib",
    "natives/win-x86-64/connector.dll",
)


def verify(path):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        legal = "META-INF/imp-licenses/"
        required = ["dev/felnull/imp/client/lava/AndroidNativeSupport.class", *DESKTOP_RESOURCES,
                    legal + "LICENSE", legal + "licenses/GPL-3.0.txt",
                    legal + "THIRD_PARTY_NOTICES.md", legal + "RELEASING.md",
                    legal + "android-tooling-APACHE-2.0.txt"]
        for component, filename in (("lavaplayer-fork", "LICENSE"), ("lavaplayer-2.2.4", "LICENSE"),
                                    ("opus", "COPYING"), ("mpg123", "COPYING"), ("ogg", "COPYING"),
                                    ("vorbis", "COPYING"), ("samplerate", "COPYING"),
                                    ("fdkaac", "NOTICE"), ("fdkaac", "LICENSE-header.txt"),
                                    ("ndk", "NOTICE"), ("ndk", "NOTICE.toolchain")):
            required.append(legal + f"licenses/android/{component}/{filename}")
        fdk_license = jar.read(legal + "licenses/android/fdkaac/LICENSE-header.txt").decode("utf-8")
        if not all(section in fdk_license for section in ("COPYRIGHT LICENSE", "NO PATENT LICENSE", "DISCLAIMER", "CONTACT INFORMATION")):
            raise ValueError(f"{path}: incomplete FDK AAC license")
        for target in ANDROID_TARGETS:
            binary = f"natives/{target}/libconnector.so"
            provenance = f"META-INF/android-natives/{target}/provenance.json"
            required.extend((binary, provenance))
            metadata = json.loads(jar.read(provenance))
            if metadata["lavaplayer_tag"] != "2.2.4" or metadata["resource_target"] != target:
                raise ValueError(f"{path}: incompatible provenance for {target}")
            if hashlib.sha256(jar.read(binary)).hexdigest() != metadata["sha256"]:
                raise ValueError(f"{path}: Android binary checksum mismatch for {target}")
        for resource in required:
            if names.count(resource) != 1:
                raise ValueError(f"{path}: expected exactly one {resource}")
    print(f"PASS {path}: Android loader, all four verified Android natives, desktop natives, license notices")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("Usage: verify_mod.py <final-mod.jar> ...")
    for argument in sys.argv[1:]:
        verify(Path(argument))
