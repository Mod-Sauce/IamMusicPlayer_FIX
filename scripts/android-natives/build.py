#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Linux x86-64 host; all build outputs stay under ignored build/android-natives."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile

from compatibility import PREFIX, audit, require

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "build/android-natives"
NDK_VERSION = "27.3.13750724"
FORK = "468c8e9f1a65364c6dcf122ecaaa0bcce8a37c23"
UPSTREAM = "2fc96d70b9f632ceb7b8387b69a874cbf86c05c0"  # lavalink-devs tag 2.2.4
SOURCES = {
    "fork": (f"https://codeload.github.com/lolicode-org/lavaplayer/tar.gz/{FORK}",
             "274e7fd2e092d7572783574fad8967d5755dcc5b9d90f034e614c879fc970716"),
    "upstream": (f"https://codeload.github.com/lavalink-devs/lavaplayer/tar.gz/{UPSTREAM}",
                 "aa0028a776de02cb9ae34ea10244fe20b2689a852e391f1d0019262160cf6439"),
}
# Resource identifiers follow the fork, not Android's ABI spelling.
ABIS = {
    "arm64-v8a": ("android-aarch64", "aarch64-linux-android", "aarch64-linux-android", "AArch64", "ELF64"),
    "armeabi-v7a": ("android-armhf", "arm-linux-androideabi", "armv7a-linux-androideabi", "ARM", "ELF32"),
    "x86": ("android-x86", "i686-linux-android", "i686-linux-android", "Intel 80386", "ELF32"),
    "x86_64": ("android-x86-64", "x86_64-linux-android", "x86_64-linux-android", "Advanced Micro Devices X86-64", "ELF64"),
}


def run(*args, cwd=None, env=None):
    subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)


def digest(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def fetch(name, url, sha, dest):
    cache = WORK / "downloads"
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / (sha + ".tar")
    if not archive.exists():
        partial = archive.with_suffix(".partial")
        run("curl", "--fail", "--location", "--retry", "3", "--connect-timeout", "30",
            "--max-time", "600", url, "--output", partial)
        require(digest(partial) == sha, f"SHA-256 mismatch: {name}")
        partial.rename(archive)
    require(digest(archive) == sha, f"Cached SHA-256 mismatch: {name}")
    dest.mkdir(parents=True)
    with tarfile.open(archive) as tar:
        tar.extractall(dest, filter="data")
    children = list(dest.iterdir())
    require(len(children) == 1 and children[0].is_dir(), f"Unexpected archive layout: {name}")
    return children[0]


def licenses(source, dest):
    found = []
    for path in sorted(source.rglob("*")):
        if path.is_file() and re.match(r"(?i)^(copying|license|licence|notice|authors|patents)([.\-_].*)?$", path.name):
            target = dest / path.relative_to(source)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            found.append(str(path.relative_to(source)))
    require(found, f"No license files found: {source}")


def dependencies(fork, stage, legal):
    props = dict(line.split("=", 1) for line in (fork / "natives/versions.properties").read_text().splitlines()
                 if line and not line.startswith("#"))
    templates = {
        "opus": ("https://downloads.xiph.org/releases/opus/opus-{v}.tar.gz", "opus/opus"),
        "mpg123": ("https://www.mpg123.de/download/mpg123-{v}.tar.bz2", "mp3/mpg123"),
        "ogg": ("https://downloads.xiph.org/releases/ogg/libogg-{v}.tar.xz", "vorbis/libogg"),
        "vorbis": ("https://downloads.xiph.org/releases/vorbis/libvorbis-{v}.tar.xz", "vorbis/libvorbis"),
        "samplerate": ("https://github.com/libsndfile/libsamplerate/releases/download/{v}/libsamplerate-{v}.tar.xz", "samplerate"),
        "fdkaac": ("https://downloads.sourceforge.net/opencore-amr/fdk-aac-{v}.tar.gz", "fdk-aac"),
    }
    manifest = {}
    for name, (template, relative) in templates.items():
        url, sha = template.format(v=props[name]), props[name + "Sha256"]
        source = fetch(name, url, sha, stage / "deps" / name)
        licenses(source, legal / name)
        if name == "fdkaac":
            # Upstream NOTICE contains only the introduction, not the redistribution terms.
            header = (source / "libAACdec/src/aacdecoder.cpp").read_text(encoding="utf-8").split("*/", 1)[0] + "*/\n"
            require(all(section in header for section in ("COPYRIGHT LICENSE", "NO PATENT LICENSE", "DISCLAIMER", "CONTACT INFORMATION")),
                    "FDK AAC complete license header not found")
            (legal / name / "LICENSE-header.txt").write_text(header, encoding="utf-8")
        dest = fork / "natives" / relative
        if name == "samplerate":
            for directory in ("src", "include"):
                shutil.copytree(source / directory, dest / directory)
        elif name == "fdkaac":
            shutil.copytree(source, dest, dirs_exist_ok=True,
                            ignore=lambda directory, names: ["CMakeLists.txt"] if Path(directory) == source else [])
        else:
            shutil.copytree(source, dest)
        manifest[name] = {"version": props[name], "url": url, "sha256": sha}
    return manifest


def validate(binary, tools, abi, expected, reports):
    def read(*args):
        return subprocess.check_output([str(tools / "llvm-readelf"), *args, str(binary)], text=True)
    header, dynamic, symbols, versions = read("-h"), read("-d"), read("--dyn-syms", "--wide"), read("-V")
    for name, text in [("elf-header", header), ("dynamic", dynamic), ("symbols", symbols), ("versions", versions)]:
        (reports / (name + ".txt")).write_text(text)
    _, _, _, machine, elf_class = ABIS[abi]
    require(re.search(r"Machine:\s*" + re.escape(machine) + r"\s*$", header, re.M), "Wrong ELF machine")
    require(re.search(r"Class:\s*" + elf_class, header), "Wrong ELF class")
    require(re.search(r"Type:\s*DYN", header), "Not an ELF shared object")
    needed = set(re.findall(r"Shared library: \[([^]]+)\]", dynamic))
    require("libc.so" in needed, "Missing Bionic libc.so linkage")
    require(needed <= {"libc.so", "libm.so", "libdl.so", "liblog.so"}, f"Unexpected runtime dependencies: {needed}")
    require(not re.search(r"GLIBC_|GLIBCXX_|libc\.so\.6|ld-linux|musl", dynamic + versions + symbols), "Non-Android linkage")
    require(not re.search(r"TEXTREL|RPATH|RUNPATH", dynamic), "Text relocations or runtime search path")
    exported = set()
    for line in symbols.splitlines():
        fields = line.split()
        if len(fields) >= 8 and fields[3] == "FUNC" and fields[4] == "GLOBAL" and fields[5] == "DEFAULT" and fields[6] != "UND":
            if fields[7].startswith("Java_"):
                exported.add(fields[7])
    require(exported == set(expected), f"JNI exports differ; missing={set(expected) - exported}, extra={exported - set(expected)}")
    require(all(name.startswith(PREFIX) for name in exported), "Relocated JNI namespace")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--abi", choices=ABIS, default="arm64-v8a")
    parser.add_argument("--audit-only", action="store_true", help="Fetch pinned sources and audit JNI; no NDK required")
    args = parser.parse_args()
    stage = WORK / ("audit" if args.audit_only else args.abi)
    require(not stage.exists(), f"Remove the previous staging directory before rebuilding: {stage}")
    stage.mkdir(parents=True)
    fork = fetch("fork", *SOURCES["fork"], stage / "fork")
    upstream = fetch("upstream", *SOURCES["upstream"], stage / "upstream")
    reports = stage / "reports"
    expected = audit(fork, upstream, reports)
    if args.audit_only:
        return
    ndk = Path(os.environ["ANDROID_NDK_HOME"]).resolve()
    require(re.search(r"Pkg.Revision\s*=\s*" + re.escape(NDK_VERSION) + r"\s*$",
                      (ndk / "source.properties").read_text(), re.M), "Wrong NDK version")
    target, host, compiler, _, _ = ABIS[args.abi]
    tools = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    toolchain = ndk / "build/cmake/android.toolchain.cmake"
    legal = stage / "licenses"
    # Preserve the NDK distribution notices, including the statically linked C++/compiler runtimes.
    (legal / "ndk").mkdir(parents=True)
    for notice in ("NOTICE", "NOTICE.toolchain"):
        shutil.copy2(ndk / notice, legal / "ndk" / notice)
    licenses(fork, legal / "lavaplayer-fork")
    licenses(upstream, legal / "lavaplayer-2.2.4")
    deps = dependencies(fork, stage, legal)
    env = os.environ.copy()
    # Do not inherit host compilation flags or pkg-config paths into cross builds.
    for key in ("CFLAGS", "CXXFLAGS", "CPPFLAGS", "LDFLAGS", "PKG_CONFIG_PATH", "CCACHE",
                "CMAKE_C_COMPILER_LAUNCHER", "CMAKE_CXX_COMPILER_LAUNCHER"):
        env.pop(key, None)
    env.update(CC=str(tools / (compiler + "21-clang")), CXX=str(tools / (compiler + "21-clang++")),
               AR=str(tools / "llvm-ar"), RANLIB=str(tools / "llvm-ranlib"), STRIP=str(tools / "llvm-strip"),
               NATIVES_DIR=str(fork / "natives"), SOURCE_DATE_EPOCH="1720000000")
    cmake_args = [f"-DCMAKE_TOOLCHAIN_FILE={toolchain}", f"-DANDROID_ABI={args.abi}",
                  "-DANDROID_PLATFORM=android-21", "-DANDROID_STL=c++_static",
                  "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"]
    env["CMAKE_EXTRA_ARGS"] = "\n".join(cmake_args)
    run("bash", fork / "natives/scripts/build-linux-deps.sh", host, cwd=fork / "natives", env=env)
    dist = stage / "dist"
    dist.mkdir()
    env["DIST_DIR"] = str(dist)
    run("cmake", "-S", fork / "natives", "-B", stage / "cmake", "-DCMAKE_BUILD_TYPE=Release",
        "-DLAVA_JNI_PREFIX=com_sedmelluq_discord_lavaplayer", "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--no-undefined",
        *cmake_args, env=env)
    run("cmake", "--build", stage / "cmake", "--parallel", os.cpu_count() or 2, env=env)
    binary = dist / "libconnector.so"
    validate(binary, tools, args.abi, expected, reports)
    # Publish only after every check passes. Never copy into source resources automatically.
    package = stage / "package"
    resource = package / "resources/natives" / target
    resource.mkdir(parents=True)
    shutil.copy2(binary, resource / binary.name)
    metadata = package / "resources/META-INF/android-natives" / target
    shutil.copytree(legal, metadata / "licenses")
    shutil.copytree(reports, metadata / "validation")
    shutil.copytree(Path(__file__).parent, metadata / "pipeline", ignore=shutil.ignore_patterns("__pycache__"))
    provenance = {"fork_commit": FORK, "lavaplayer_tag": "2.2.4", "lavaplayer_commit": UPSTREAM,
                  "sources": SOURCES, "dependencies": deps, "ndk": NDK_VERSION, "api": 21,
                  "abi": args.abi, "resource_target": target, "jni_prefix": PREFIX,
                  "sha256": digest(binary), "cmake": subprocess.check_output(["cmake", "--version"], text=True),
                  "compiler": subprocess.check_output([env["CC"], "--version"], text=True),
                  "runtime_tested": False}
    (metadata / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    with (package / "SHA256SUMS").open("w") as sums:
        for file in sorted((package / "resources").rglob("*")):
            if file.is_file():
                sums.write(f"{digest(file)}  {file.relative_to(package)}\n")
    print(f"Validated resource package: {package}")


if __name__ == "__main__":
    main()
