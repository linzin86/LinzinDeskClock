import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

from r8_artifacts import archive_release, main, verify_archive, verify_pair


class MappingPairTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        body = b'com.example.Clock -> a:\n'
        self.map_id = hashlib.sha256(body).hexdigest()
        self.mapping = self.root / 'mapping.txt'
        self.mapping.write_bytes((
            '# compiler: R8\n# compiler_version: 9.4.24\n# min_api: 21\n'
            f'# pg_map_id: {self.map_id}\n# pg_map_hash: SHA-256 {self.map_id}\n'
        ).encode() + body)
        self.apk = self.make_apk(self.map_id)

    def make_apk(self, map_id):
        marker = '~~R8' + json.dumps({'pg-map-id': map_id, 'version': '9.4.24', 'min-api': 21})
        encoded = marker.encode()
        length = len(marker)
        prefix = bytearray()
        while length >= 128:
            prefix.append((length & 127) | 128)
            length >>= 7
        prefix.append(length)
        dex = bytearray(116)
        dex[:8] = b'dex\n035\0'
        struct.pack_into('<II', dex, 56, 1, 112)
        struct.pack_into('<I', dex, 112, 116)
        dex.extend(prefix + encoded + b'\0')
        apk = self.root / 'clock.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', dex)
        return apk

    def test_records_full_file_hash_and_matching_dex_id(self):
        result = verify_pair(self.apk, self.mapping)
        self.assertEqual(self.map_id, result.get('pg_map_id'))
        self.assertEqual(hashlib.sha256(self.mapping.read_bytes()).hexdigest(), result.get('mapping_sha256'))

    def test_rejects_mapping_body_with_changed_bytes(self):
        self.mapping.write_bytes(self.mapping.read_bytes() + b'# changed\n')
        with self.assertRaisesRegex(ValueError, 'hash'):
            verify_pair(self.apk, self.mapping)

    def test_rejects_a_different_apks_mapping(self):
        self.make_apk('0' * 64)
        with self.assertRaisesRegex(ValueError, 'map ID'):
            verify_pair(self.apk, self.mapping)

    def test_pair_command_reports_the_verified_map_id(self):
        command = [sys.executable, str(Path(__file__).with_name('r8_artifacts.py')),
                   'pair', '--apk', str(self.apk), '--mapping', str(self.mapping)]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(self.map_id, result.stdout)

    def test_pair_command_fails_for_a_mismatched_mapping(self):
        self.make_apk('0' * 64)
        command = [sys.executable, str(Path(__file__).with_name('r8_artifacts.py')),
                   'pair', '--apk', str(self.apk), '--mapping', str(self.mapping)]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)

    def test_rejects_apk_without_r8_marker(self):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('classes.dex', b'dex\n035\0' + bytes(104))
        with self.assertRaisesRegex(ValueError, 'marker'):
            verify_pair(self.apk, self.mapping)

    def archive(self):
        metadata = self.root / 'output-metadata.json'
        metadata.write_text(json.dumps({'applicationId': 'com.example.clock', 'variantName': 'release',
            'elements': [{'versionCode': 2, 'versionName': '1.1', 'outputFile': 'clock.apk'}]}))
        return archive_release(self.apk, self.mapping, metadata, 'app', '1' * 40,
            {'applicationId': 'com.example.clock', 'versionCode': 2, 'versionName': '1.1', 'minSdk': 21}, self.root / 'exports')

    def test_archive_preserves_exact_bytes_and_artifact_identity(self):
        archive = self.archive()
        self.assertIsNotNone(archive, 'Export must create a recoverable archive')
        result = verify_archive(archive)
        self.assertEqual('app', result.get('module'))
        self.assertEqual('1' * 40, result.get('source_commit'))
        self.assertEqual('com.example.clock', result.get('applicationId'))
        with zipfile.ZipFile(archive) as saved:
            self.assertEqual(self.mapping.read_bytes(), saved.read('mapping.txt'))
            self.assertEqual(self.apk.read_bytes(), saved.read('clock.apk'))

    def test_rejects_metadata_for_a_different_apk_before_export(self):
        metadata = self.root / 'wrong-output-metadata.json'
        metadata.write_text(json.dumps({'applicationId': 'com.example.clock', 'variantName': 'debug',
            'elements': [{'versionCode': 2, 'versionName': '1.1', 'outputFile': 'some-other-debug.apk'}]}))
        output = self.root / 'wrong-exports'
        with self.assertRaisesRegex(ValueError, 'outputFile'):
            archive_release(self.apk, self.mapping, metadata, 'app', '1' * 40,
                {'applicationId': 'com.example.clock', 'versionCode': 2, 'versionName': '1.1', 'minSdk': 21}, output)
        self.assertFalse(output.exists(), 'Mismatched metadata must not create retained evidence')

    def test_export_reads_utf8_aapt_labels_under_a_cp950_host_locale(self):
        metadata = self.root / 'metadata.json'
        metadata.write_text(json.dumps({'applicationId': 'com.example.clock', 'variantName': 'release',
            'elements': [{'versionCode': 2, 'versionName': '1.1', 'outputFile': 'clock.apk'}]}))
        badging = ("package: name='com.example.clock' versionCode='2' versionName='1.1'\n"
                   "minSdkVersion:'21'\napplication-label-ja:'時計'\n").encode('utf-8')
        real_command = subprocess.check_output

        def command(arguments, **kwargs):
            if arguments[:3] == ['git', 'status', '--porcelain']:
                return b''
            if arguments[:3] == ['git', 'rev-parse', 'HEAD']:
                return '1' * 40
            return real_command([sys.executable, '-c',
                f"import sys; sys.stdout.buffer.write(bytes.fromhex('{badging.hex()}'))"], **kwargs)

        output = self.root / 'utf8-exports'
        arguments = ['r8_artifacts.py', 'export', '--apk', str(self.apk), '--mapping', str(self.mapping),
            '--metadata', str(metadata), '--module', 'app', '--aapt2', 'aapt2', '--output', str(output)]
        completed = True
        with mock.patch.object(sys, 'argv', arguments), mock.patch('subprocess._text_encoding', return_value='cp950'), \
             mock.patch('r8_artifacts.subprocess.check_output', side_effect=command), mock.patch('sys.stdout', new_callable=io.StringIO):
            try:
                main()
            except SystemExit:
                completed = False
        self.assertTrue(completed, 'Valid UTF-8 aapt2 output must export on a CP950 Windows host')
        self.assertEqual(1, len(list(output.glob('*.zip'))))

    def test_archive_does_not_overwrite_existing_evidence(self):
        self.assertIsNotNone(self.archive(), 'First export must exist')
        with self.assertRaises(FileExistsError):
            self.archive()

    def test_archive_verification_rejects_changed_mapping_header(self):
        archive = self.archive()
        self.assertIsNotNone(archive, 'First export must exist')
        changed = self.root / 'changed.zip'
        with zipfile.ZipFile(archive) as original, zipfile.ZipFile(changed, 'w') as edited:
            for name in original.namelist():
                value = original.read(name)
                edited.writestr(name, b'# extra header\n' + value if name == 'mapping.txt' else value)
        with self.assertRaisesRegex(ValueError, 'hash'):
            verify_archive(changed)


if __name__ == '__main__':
    unittest.main()
