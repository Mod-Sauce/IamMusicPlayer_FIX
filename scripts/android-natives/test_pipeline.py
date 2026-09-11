# SPDX-License-Identifier: Apache-2.0
"""Offline guard tests. Real source/header comparison: build.py --audit-only."""
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from build import ABIS, validate
from compatibility import PREFIX, signatures
from verify_mod import ANDROID_TARGETS, DESKTOP_RESOURCES, verify


class PipelineTests(unittest.TestCase):
    def test_signatures_include_types_not_parameter_names(self):
        name = PREFIX + "_natives_samplerate_SampleRateLibrary_create"
        java = signatures(f"JNIEXPORT jlong JNICALL {name}(JNIEnv *, jobject, jint, jint);")
        c = signatures("CONNECTOR_EXPORT jlong JNICALL LAVA_JNI_NAME("
                       "_natives_samplerate_SampleRateLibrary_create)"
                       "(JNIEnv *jni, jobject me, jint type, jint channels) {", True)
        self.assertEqual(java, c)
        incompatible = signatures("CONNECTOR_EXPORT jlong JNICALL LAVA_JNI_NAME("
                                  "_natives_samplerate_SampleRateLibrary_create)"
                                  "(JNIEnv *jni, jobject me, jint type, jint channels, jintArray error) {", True)
        self.assertNotEqual(java, incompatible)

    def check_binary(self, abi="arm64-v8a", dynamic=None, symbols=None, versions="", machine=None):
        name = PREFIX + "_natives_opus_OpusDecoderLibrary_create"
        output = {
            "-h": f"Class: {ABIS[abi][4]}\nType: DYN (Shared object file)\nMachine: {machine or ABIS[abi][3]}\n",
            "-d": dynamic if dynamic is not None else "Shared library: [libc.so]\nShared library: [liblog.so]",
            "--dyn-syms": symbols if symbols is not None else f"1: 00001000 10 FUNC GLOBAL DEFAULT 12 {name}",
            "-V": versions,
        }
        with tempfile.TemporaryDirectory() as directory:
            with patch("subprocess.check_output", side_effect=lambda command, **kw: output[command[1]]):
                validate(Path("unused.so"), Path("ndk/bin"), abi, {name: []}, Path(directory))

    def test_all_abi_headers(self):
        for abi in ABIS:
            with self.subTest(abi=abi):
                self.check_binary(abi)

    def test_glibc_rejected(self):
        for dynamic in ["Shared library: [libc.so.6]", "Shared library: [libc.so]\nShared library: [libstdc++.so.6]",
                        "Shared library: [libc.so]\nShared library: [libc++_shared.so]",
                        "Shared library: [libc.so]\nTEXTREL", "Shared library: [libc.so]\nRUNPATH"]:
            with self.subTest(dynamic=dynamic), self.assertRaises(RuntimeError):
                self.check_binary(dynamic=dynamic)
        with self.assertRaises(RuntimeError):
            self.check_binary(versions="GLIBC_2.17")

    def test_missing_wrong_namespace_and_undefined_exports_rejected(self):
        name = PREFIX + "_natives_opus_OpusDecoderLibrary_create"
        for symbols in ["", f"1: 0 0 FUNC GLOBAL DEFAULT UND {name}",
                        f"1: 0 0 FUNC GLOBAL DEFAULT 12 {name.replace('com_sedmelluq_discord', 'org_lolicode')}"]:
            with self.subTest(symbols=symbols), self.assertRaises(RuntimeError):
                self.check_binary(symbols=symbols)

    def test_wrong_machine_rejected(self):
        with self.assertRaises(RuntimeError):
            self.check_binary(machine="Intel 80386")

    def notice_fixture(self, missing=None, incomplete_fdk=False):
        root = Path(__file__).resolve().parents[2]
        prefix = 'META-INF/imp-licenses/'
        resources = {prefix + name: (root / name).read_bytes()
                     for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'RELEASING.md')}
        resources[prefix + 'android-tooling-APACHE-2.0.txt'] = (root / 'scripts/android-natives/LICENSE').read_bytes()
        resources.update({prefix + str(p.relative_to(root)): p.read_bytes()
                          for p in (root / 'licenses').rglob('*') if p.is_file()})
        resources['dev/felnull/imp/client/lava/AndroidNativeSupport.class'] = b'test fixture'
        resources.update({name: b'test fixture' for name in DESKTOP_RESOURCES})
        for target in ANDROID_TARGETS:
            binary = b'test fixture'
            resources[f'natives/{target}/libconnector.so'] = binary
            resources[f'META-INF/android-natives/{target}/provenance.json'] = json.dumps({
                'lavaplayer_tag': '2.2.4', 'resource_target': target,
                'sha256': hashlib.sha256(binary).hexdigest()}).encode()
        if missing:
            del resources[prefix + missing]
        if incomplete_fdk:
            resources[prefix + 'licenses/android/fdkaac/LICENSE-header.txt'] = b'INTRODUCTION only'
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / 'fixture.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                for name, data in resources.items():
                    archive.writestr(name, data)
            verify(jar)

    def test_packaged_notices_present(self):
        self.notice_fixture()

    def test_missing_project_license_rejected(self):
        with self.assertRaises(ValueError):
            self.notice_fixture(missing='LICENSE')

    def test_incomplete_fdk_license_rejected(self):
        with self.assertRaises(ValueError):
            self.notice_fixture(incomplete_fdk=True)


if __name__ == "__main__":
    unittest.main()
