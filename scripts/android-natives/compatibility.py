# SPDX-License-Identifier: Apache-2.0
"""Fail-closed JNI audit and narrowly scoped 2.2.4 compatibility adaptations."""
import difflib
import json
import re
import shutil
import subprocess
from pathlib import Path

PREFIX = "Java_com_sedmelluq_discord_lavaplayer"
CREATE = PREFIX + "_natives_samplerate_SampleRateLibrary_create"
INITIALISE = PREFIX + "_natives_vorbis_VorbisDecoderLibrary_initialise"
JAVA_ROOT = "main/src/main/java/com/sedmelluq/discord/lavaplayer/natives"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def signatures(text, definitions=False):
    text = re.sub(r"LAVA_JNI_NAME\((_[A-Za-z0-9_]+)\)", lambda m: PREFIX + m[1], text)
    pattern = r"(?:CONNECTOR_EXPORT|JNIEXPORT)\s+(\w+)\s+JNICALL\s+(Java_\w+)\s*\(([^)]*)\)"
    result = {}
    for ret, name, args in re.findall(pattern, text):
        types = []
        for arg in args.split(","):
            arg = arg.strip()
            if definitions:
                arg = re.sub(r"\b\w+$", "", arg).strip()
            types.append(re.sub(r"\s+", "", arg))
        require(name not in result, f"Duplicate JNI entry: {name}")
        result[name] = [ret, types]
    return result


def headers(repo, out):
    out.mkdir(parents=True)
    java = sorted((repo / JAVA_ROOT).rglob("*.java"))
    native_files = [p for p in java if re.search(r"\bnative\s+\w+\s+\w+\s*\(", p.read_text())]
    require(len(native_files) == 7, "Unexpected native Java class set")
    # Only loader linkage is stubbed; native declarations are compiled unchanged.
    stub = out / "ConnectorNativeLibLoader.java"
    stub.write_text("package com.sedmelluq.discord.lavaplayer.natives;\n"
                    "public class ConnectorNativeLibLoader { public static void loadConnectorLibrary() {} }\n")
    subprocess.run(["javac", "-h", str(out), "-d", str(out / "classes"), str(stub),
                    *map(str, native_files)], check=True)
    shutil.rmtree(out / "classes")
    stub.unlink()
    return signatures("\n".join(p.read_text() for p in sorted(out.glob("*.h"))))


def descriptors(directory):
    # JNI C types alone cannot distinguish ByteBuffer from ShortBuffer (both jobject).
    text = "\n".join(p.read_text() for p in sorted(directory.glob("*.h")))
    pairs = re.findall(r"Signature:\s*(\S+).*?JNIEXPORT\s+\w+\s+JNICALL\s+(Java_\w+)", text, re.S)
    return {name: descriptor for descriptor, name in pairs}


def c_signatures(repo):
    root = repo / "natives/connector"
    files = sorted(root.glob("*.c")) + [root / "linux/statistics.c"]
    return signatures("\n".join(p.read_text() for p in files), definitions=True)


def audit(fork, upstream, out):
    out.mkdir(parents=True, exist_ok=True)
    expected = headers(upstream, out / "headers-2.2.4")
    fork_java = headers(fork, out / "headers-fork")
    require(len(expected) == 26, "Unexpected 2.2.4 JNI method count")
    baseline_descriptors = descriptors(out / "headers-2.2.4")
    fork_descriptors = descriptors(out / "headers-fork")
    require(baseline_descriptors.keys() == expected.keys() == fork_descriptors.keys(), "Descriptor entry set differs")
    require({k for k in baseline_descriptors if baseline_descriptors[k] != fork_descriptors[k]} == {CREATE},
            "Unexpected Java method descriptor differences")
    require(baseline_descriptors[CREATE] == "(II)J" and fork_descriptors[CREATE] == "(II[I)J",
            "Unexpected create descriptors")
    (out / "java-descriptors.json").write_text(json.dumps({"2.2.4": baseline_descriptors, "fork": fork_descriptors}, indent=2) + "\n")
    changed = {k for k in expected.keys() | fork_java.keys() if expected.get(k) != fork_java.get(k)}
    require(changed == {CREATE}, f"Unexpected Java ABI differences: {changed}")
    require(expected[CREATE] == ["jlong", ["JNIEnv*", "jobject", "jint", "jint"]], "Unexpected baseline create")
    require(fork_java[CREATE] == ["jlong", ["JNIEnv*", "jobject", "jint", "jint", "jintArray"]], "Unexpected fork create")
    for repo, java in [(upstream, expected), (fork, fork_java)]:
        c = c_signatures(repo)
        require(c.keys() == java.keys(), "Java/C JNI entry set differs")
        mismatches = {k for k in java if c[k] != java[k]}
        require(mismatches == {INITIALISE}, f"Unexpected Java/C mismatch: {mismatches}")
        require(c[INITIALISE][0] == "jint" and java[INITIALISE][0] == "jboolean"
                and c[INITIALISE][1] == java[INITIALISE][1], "Unexpected Vorbis mismatch")
    diff = []
    for relative in ["natives/connector/connector.h", *[
            str(p.relative_to(upstream)) for p in sorted((upstream / JAVA_ROOT).rglob("*Library.java"))],
            *[str(p.relative_to(upstream)) for p in sorted((upstream / "natives/connector").rglob("*.c"))]]:
        a, b = upstream / relative, fork / relative
        if a.exists() and b.exists():
            diff.extend(difflib.unified_diff(a.read_text().splitlines(True), b.read_text().splitlines(True),
                                             "2.2.4/" + relative, "fork/" + relative))
    (out / "source-interface.diff").write_text("".join(diff))
    (out / "expected-jni.json").write_text(json.dumps(expected, indent=2) + "\n")
    # Exact replacements deliberately fail if the pinned implementation changes.
    path = fork / "natives/connector/samplerate.c"
    text = path.read_text()
    for old, new in [(", jint channels, jintArray error_array)", ", jint channels)"),
                     ("\tjint error_out = (jint)error;\n\t(*jni)->SetIntArrayRegion(jni, error_array, 0, 1, &error_out);\n", "")]:
        require(text.count(old) == 1, "Samplerate adaptation context changed")
        text = text.replace(old, new)
    path.write_text(text)
    path = fork / "natives/connector/vorbis.c"
    old = "CONNECTOR_EXPORT jint JNICALL LAVA_JNI_NAME(_natives_vorbis_VorbisDecoderLibrary_initialise)"
    text = path.read_text()
    require(text.count(old) == 1, "Vorbis adaptation context changed")
    path.write_text(text.replace(old, old.replace(" jint ", " jboolean ")))
    require(c_signatures(fork) == expected, "Adapted C ABI does not match 2.2.4")
    # Force actual NDK compilation to check every declaration, not just parsed text.
    connector_header = fork / "natives/connector/connector.h"
    with connector_header.open("a") as handle:
        for header in sorted((out / "headers-2.2.4").glob("*.h")):
            handle.write(f'\n#include "{header.resolve()}"\n')
    report = ("26 JNI methods in 7 classes audited. Only Java ABI difference: SampleRateLibrary.create "
              "(II)J -> (II[I)J. Adapted staged C to (II)J. Both original C trees return jint for "
              "Vorbis initialise, while Java requires jboolean; staged C corrected. All adapted C "
              "signatures match javac-generated 2.2.4 headers. JNI prefix override required. "
              "This is a source ABI check, not an Android runtime test.\n")
    (out / "compatibility.txt").write_text(report)
    print(report, flush=True)
    return expected
