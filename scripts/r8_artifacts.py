"""Keep a release APK, its verified R8 mapping and build metadata together."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile


def archive_release(apk, mapping, metadata, module, commit, identity, output):
    pair = verify_pair(apk, mapping)
    meta = json.loads(Path(metadata).read_bytes())
    elements = meta.get('elements', [])
    if len(elements) != 1:
        raise ValueError('Expected one APK metadata element')
    element = elements[0]
    if element.get('outputFile') != Path(apk).name:
        raise ValueError('AGP outputFile does not identify the supplied APK')
    for key in ('applicationId', 'versionCode', 'versionName'):
        actual = meta.get(key) if key == 'applicationId' else element.get(key)
        if actual != identity.get(key):
            raise ValueError('APK metadata mismatch: ' + key)
    if identity.get('minSdk') != pair['min_api']:
        raise ValueError('APK and R8 minimum API mismatch')
    variant = meta.get('variantName', '')
    if not re.fullmatch(r'[A-Za-z0-9_-]+', module) or not re.fullmatch(r'[A-Za-z0-9_-]+', variant):
        raise ValueError('Invalid module or variant')
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Expected a full source commit')
    app_id = identity['applicationId']
    if not re.fullmatch(r'[A-Za-z0-9_.]+', app_id):
        raise ValueError('Invalid application ID')
    apk_name = Path(apk).name
    if not re.fullmatch(r'[A-Za-z0-9_.-]+\.apk', apk_name):
        raise ValueError('Invalid APK filename')
    files = {apk_name: Path(apk).read_bytes(), 'mapping.txt': Path(mapping).read_bytes(),
             'output-metadata.json': Path(metadata).read_bytes()}
    manifest = dict(pair, module=module, variant=variant, source_commit=commit, apk_file=apk_name, **identity,
                    files={name: hashlib.sha256(data).hexdigest() for name, data in files.items()})
    version = re.sub(r'[^A-Za-z0-9._-]', '_', identity['versionName'])
    name = f"{module}-{variant}-{app_id}-{identity['versionCode']}-{version}-{commit}.zip"
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    destination = output / name
    with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
        archive.writestr('manifest.json', json.dumps(manifest, indent=2, sort_keys=True) + '\n')
    verify_archive(destination)
    return destination


def verify_archive(path):
    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read('manifest.json'))
        apk_name = manifest.get('apk_file', '')
        if not re.fullmatch(r'[A-Za-z0-9_.-]+\.apk', apk_name):
            raise ValueError('Invalid archived APK filename')
        names = [apk_name, 'mapping.txt', 'output-metadata.json']
        if sorted(archive.namelist()) != sorted(names + ['manifest.json']):
            raise ValueError('Unexpected archive contents')
        data = {name: archive.read(name) for name in names}
    for name, content in data.items():
        if hashlib.sha256(content).hexdigest() != manifest['files'].get(name):
            raise ValueError('Archive file hash mismatch: ' + name)
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        for name in (apk_name, 'mapping.txt'):
            (root / name).write_bytes(data[name])
        pair = verify_pair(root / apk_name, root / 'mapping.txt')
    if any(manifest.get(key) != value for key, value in pair.items()):
        raise ValueError('Archive R8 identity mismatch')
    return manifest


def dex_markers(dex):
    if len(dex) < 112 or not dex.startswith(b'dex\n'):
        raise ValueError('Invalid DEX header')
    count, table = struct.unpack_from('<II', dex, 56)
    if table + count * 4 > len(dex):
        raise ValueError('Invalid DEX string table')
    for index in range(count):
        offset, = struct.unpack_from('<I', dex, table + index * 4)
        for _ in range(5):
            if offset >= len(dex):
                raise ValueError('Invalid DEX string length')
            value = dex[offset]
            offset += 1
            if value < 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = dex.find(b'\0', offset)
        if end < 0:
            raise ValueError('Unterminated DEX string')
        if dex[offset:offset + 5] == b'~~R8{':
            marker = json.loads(dex[offset + 4:end])
            if not isinstance(marker, dict):
                raise ValueError('Invalid R8 marker')
            yield marker


def verify_pair(apk, mapping):
    raw = Path(mapping).read_bytes()
    start = re.search(rb'(?m)^[^#\r\n]', raw)
    if not start:
        raise ValueError('Missing R8 mapping body')
    header = raw[:start.start()].decode('utf-8')

    def field(name):
        matches = re.findall(r'^# ' + re.escape(name) + r': (.+)$', header, re.MULTILINE)
        if len(matches) != 1:
            raise ValueError('Missing or duplicate mapping header: ' + name)
        return matches[0].strip()

    body_hash = hashlib.sha256(raw[start.start():]).hexdigest()
    if field('pg_map_hash') != 'SHA-256 ' + body_hash:
        raise ValueError('Mapping body hash does not match original bytes')
    map_id = field('pg_map_id')
    if not re.fullmatch(r'[0-9a-f]{7,64}', map_id) or not body_hash.startswith(map_id):
        raise ValueError('Mapping map ID does not match its body hash')
    compiler = field('compiler_version')
    minimum = int(field('min_api'))
    with zipfile.ZipFile(apk) as archive:
        markers = [marker for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)
                   for marker in dex_markers(archive.read(name))]
    if not markers:
        raise ValueError('APK has no R8 marker')
    if any(marker.get('pg-map-id') != map_id for marker in markers):
        raise ValueError('APK and mapping map ID differ')
    if any(marker.get('version') != compiler or marker.get('min-api') != minimum for marker in markers):
        raise ValueError('APK and mapping compiler/min API differ')
    return {'pg_map_id': map_id, 'mapping_body_sha256': body_hash,
            'mapping_sha256': hashlib.sha256(raw).hexdigest(),
            'apk_sha256': hashlib.sha256(Path(apk).read_bytes()).hexdigest(),
            'compiler_version': compiler, 'min_api': minimum}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    pair = commands.add_parser('pair', help='Verify APK DEX markers against mapping bytes')
    export = commands.add_parser('export', help='Export from a clean committed checkout')
    for command in (pair, export):
        command.add_argument('--apk', type=Path, required=True)
        command.add_argument('--mapping', type=Path, required=True)
    export.add_argument('--metadata', type=Path, required=True)
    export.add_argument('--module', required=True)
    export.add_argument('--aapt2', type=Path, required=True)
    export.add_argument('--output', type=Path, required=True)
    verify = commands.add_parser('verify', help='Verify all bytes in a retained archive')
    verify.add_argument('archive', type=Path)
    args = parser.parse_args()
    try:
        if args.command == 'pair':
            result = verify_pair(args.apk, args.mapping)
        elif args.command == 'verify':
            result = verify_archive(args.archive)
        else:
            root = Path(__file__).resolve().parent.parent
            if subprocess.check_output(['git', 'status', '--porcelain'], cwd=root):
                raise ValueError('Commit or isolate checkout changes before exporting release provenance')
            commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
            badging = subprocess.check_output([str(args.aapt2), 'dump', 'badging', str(args.apk)], encoding='utf-8')
            package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'", badging, re.M)
            minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'", badging, re.M)
            if not package or not minimum:
                raise ValueError('Unable to read APK identity using aapt2')
            identity = {'applicationId': package[1], 'versionCode': int(package[2]),
                        'versionName': package[3], 'minSdk': int(minimum[1])}
            result = str(archive_release(args.apk, args.mapping, args.metadata, args.module, commit, identity, args.output))
        print(json.dumps(result, indent=2, sort_keys=True))
    except (ValueError, OSError, KeyError, struct.error, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f'error: {error}\n')


if __name__ == '__main__':
    main()
