# R8 mapping retention

Keep each release APK with its own mapping and AGP output metadata. Source control
retains the historical mappings below; new generated `app/release/` files are
ignored. No Git history is rewritten. The former `app/release/` rule inside
`app/.gitignore` matched `app/app/release/`; `/release/` fixes that relative path.

## Recover historical bytes

Run from a clone containing this repository's history. This writes Git blob bytes
directly, avoiding PowerShell text encoding and checkout newline conversion:

```powershell
New-Item -ItemType Directory -Force release-archives/recovered | Out-Null
@'
from pathlib import Path
import subprocess
commit = '3e5641774dd54add3381d5e358ab92cb7d405a92'
for name in ('mapping.txt', 'output-metadata.json'):
    Path('release-archives/recovered', name).write_bytes(
        subprocess.check_output(['git', 'show', commit + ':app/release/' + name]))
'@ | py -
Get-FileHash release-archives/recovered/* -Algorithm SHA256
```

For v1.1 the mapping is **26,671,747 bytes**, SHA-256
`bae149fefcfab0431918b19b60d309c2dd03fc35197b67723a0d46926abdcd75`,
Git blob `04a059e26aa807607dec57d4e88ac1ee278d5e05`.
The metadata SHA-256 is
`297cd85583659fcae1224f7222af89ba0671aee4ac75ce4f98c1754f95e44616`.
Recovery and the fork's Git tree/blob identities were verified before removing
these two generated files from the current index.

## Verified official APK pairs

These two APKs were downloaded from the upstream releases and checked against the
actual R8 markers in their DEX string tables, mapping body hashes, compiler version
9.4.24 and minimum API. Both have application ID `com.linzin.linzindeskclock` and
versionCode 1. A retrieval commit identifies the stored mapping, not a proven
clean build of the APK from that commit.

| Release | Mapping retrieval commit | min API | Mapping body SHA-256 / R8 map ID |
| --- | --- | --- | --- |
| [v1.1](https://github.com/linzin86/LinzinDeskClock/releases/tag/v1.1) | `3e5641774dd54add3381d5e358ab92cb7d405a92` | 21 | `ef2b9ad3d12344f2eecbd0186401b6b74d615f0878494473d18c873dacb3caea` |
| [v1.0.3](https://github.com/linzin86/LinzinDeskClock/releases/tag/v1.0.3) | `3627ddd6fbb5a6138eba18d32f629b5999b1ea1c` | 31 | `15260e5d9ffdd6b0082787328d240c896d89b990b2812d76a29de113c5a3a473` |

v1.1 APK SHA-256:
`1389aaac7c49bb6eb724f5b9ac503ee71562aec0ab238dedf8baf37739f6a864`.
Its embedded VCS revision is `3627ddd6fbb5a6138eba18d32f629b5999b1ea1c`.

v1.0.3 APK SHA-256:
`f99c1ebb5f8149098fb0e8c3ff2fef3bb088423aa11b7dc6b83090d4ba700b3d`.
Its embedded VCS revision is `224d48729e2341bcf93463f3a650f845d1340c71`.
Its mapping is **28,852,603 bytes**, full-file SHA-256
`1f29fb7755e328edb5acdc9ead718823736c3b9d7f60a9feb5fa6d3c1bec3558`,
Git blob `437098ebfbafdcb5315c2a8a4d9c3d42093c603b`.

Mappings at `224d4872`, `0ca0cac2`, `cf85a180`, `0b85d520` and `0fde21cb`
remain retrievable in history. They have **not** been matched to official APKs.
In particular, a commit title or version name alone is insufficient pairing proof.

To recheck an original APK against recovered mapping bytes:

```powershell
gh release download v1.1 --repo linzin86/LinzinDeskClock --pattern '*.apk' --dir release-archives
py scripts/r8_artifacts.py pair --apk release-archives/LinzinDeskClock_v1.1.apk --mapping release-archives/recovered/mapping.txt
```

## Export future builds

Use a clean, committed checkout, the module's freshly built R8 APK, its mapping,
and its own AGP `output-metadata.json`. Export before another build overwrites any
of those inputs. The exporter refuses a dirty checkout and never overwrites an
existing archive. It validates the APK's actual package/version/minSdk using
`aapt2`, the AGP identity and exact `outputFile` filename, and the DEX R8 marker
against mapping bytes.

```powershell
.\gradlew.bat :app:assembleRelease
py scripts/r8_artifacts.py export --module app --apk app/build/outputs/apk/release/app-release-unsigned.apk --mapping app/build/outputs/mapping/release/mapping.txt --metadata app/build/outputs/apk/release/output-metadata.json --aapt2 "$env:ANDROID_HOME/build-tools/37.0.0/aapt2.exe" --output release-archives
py scripts/r8_artifacts.py verify release-archives/ARCHIVE_NAME.zip
```

The ZIP name separates module, variant, application ID, versionCode/versionName
and full source commit. It contains the APK under its original filename, exact
mapping and metadata bytes, and `manifest.json` with SHA-256 digests, source commit,
compiler/minimum API and R8 map ID. Signed APKs may also be exported using their
matching metadata; use a separate destination for a second artifact of the same
module/version/commit. The archive records the exact supplied APK digest and
does not sign or publish anything. Keep the APK filename named by its matching
AGP metadata when signing; renamed APKs are rejected rather than guessing a match.

CI exports its freshly built **unsigned** modern release and uploads
`r8-release-<checkout commit>` with **7-day retention**. This is temporary CI
retention, not a permanent release archive. Before publishing a signed release,
export that exact signed APK and keep its ZIP in owner-controlled backed-up
storage. Download CI archives before expiry when they must be retained:

```powershell
gh run download RUN_ID --repo Evanlau1798/LinzinDeskClock --name r8-release-COMMIT --dir release-archives/downloaded
py scripts/r8_artifacts.py verify release-archives/downloaded/ARCHIVE_NAME.zip
```

The verifier checks every retained file's full SHA-256 and repeats APK/mapping
pair validation. A changed mapping body or wrong APK fails before export; a
changed header or other archived byte fails archive verification. Hashes detect
corruption and mismatches; retain the archive in trusted storage. They do not
authenticate an attacker-modified archive and manifest together.

Run the focused retention tests with
`py -m unittest discover -s scripts -p test_r8_artifacts.py -v`.
This maintenance workflow does not change R8 rules, Android behavior or signing.
