# Building

## Home Assistant package

The canonical integration lives in `custom_components/tuya_wall_panel` for HACS.
Run `python ha-knob/build_package.py` to package the canonical translations and produce
`ha-knob/tuya-wall-panel.zip` plus the existing expanded manual-copy folder.
Generated packages and expanded copies are not tracked.

## Android APK

The current build uses Windows Python 3.13, Java 8 and Ubuntu under WSL. Scripts
still contain developer-specific paths. Adjust them for another workstation:

1. Fetch ECJ, dx and the Android platform stub with `python audio/fetch_build_tools.py`.
2. Install Zig 0.13.0 and set its executable path in `unified-app/build_native.py`.
   The current path is `panel-172.16.1.26/tooling/zig-windows-x86_64-0.13.0/zig.exe`.
3. Install Android SDK platform 35 and build-tools 34.0.0 in Ubuntu/WSL; update
   the `/home/bacnet/android-sdk` paths in `build.py` and `media_dependencies.py`.
4. Install Python `cryptography` for APK signing. The build currently loads it
   from `panel-172.16.1.26/python-deps`; install with `pip install --target
   panel-172.16.1.26/python-deps cryptography` or adapt that path.
5. Put your private signing key/certificate pair in the ignored
   `unified-app/app-signing-key.pem` and `app-signing-cert.pem`. With neither
   present, the build creates a new development identity. It never replaces an
   incomplete identity. Keep keys backed up outside this repository.
6. Prepare the receiver native payloads described below, then run `python unified-app/build.py`. It compiles the native workers and APK,
   verifies pinned SIP/Media3 artifacts, signs the APK, and generates the setup kit.

Building does not install anything. Shared native headers remain at their
historical paths so this source snapshot builds without changing hardware logic.
Generated hardware binaries and downloaded compilers are not tracked. Hardware
assets are generated before packaging. Receiver artifacts are an additional pinned
build input, supplied in the matching release source/relink bundle or rebuilt below.

## Shared receiver and standalone APK

Both product builds compile `shared/audio-receiver/src` directly. SIP core classes
are in `shared/sip/src`; keep each engine canonical rather than copying it into
product source trees. See [shared engine layout](shared/README.md).

The tested receiver toolchains are Musl GCC 9.4 (ARM64) and Bootlin Musl GCC 13.3
(ARM32), with configured WSL caches under `/root/wpar`. These native builds use
the actual pinned libraries/configuration from the release source/relink archive.
The release archive preserves dependency source archives, patched/configured
Shairport/nqptp trees, relinkable objects and ABI-specific static libraries. Extract
it in a separate work directory, read its README, and adapt absolute tool paths.
This is a developer-oriented incremental build; a clean-host build is not claimed.

Run under Ubuntu/WSL as the cache owner:

```sh
python3 airplay-app/scripts/build-isolated-native.py arm64-v8a
python3 airplay-app/scripts/build-isolated-native.py armeabi-v7a
sh airplay-app/scripts/build-apk.sh
```

The dedicated APK is `airplay-app/android/app/build/outputs/apk/debug/app-debug.apk`.
Preserve its existing signing identity for updates; it differs from the unified
app's identity. The unified build checks each receiver artifact against
`shared/audio-receiver/native-payloads.json` before packaging. A native engine
change requires deliberate review/update of that manifest and rebuilding both
APKs. Do not automatically repin unexplained differences.

For this release, the exact installed APKs are attached to GitHub Releases; no
Android CI build is claimed. GitHub Actions validates the HA integration only.

## Tests

HA tests: `python -m unittest discover -s ha-knob -p 'test_*.py'`.
On Windows, install `tzdata` if Python does not have IANA time-zone data.

Android policy and recovery tests: `python unified-app/test_runtime.py` and
`python unified-app/test_app_recovery.py`, using the configured Java/ECJ paths.
They use a local fake broker and fake Android services, not the live panel.

The Android build now packages ARM32 (`armeabi-v7a`) and ARM64 (`arm64-v8a`) native variants in one APK. Run `python unified-app/test_abis.py` for ELF/hash, selection and app-only installer checks. See [multi-ABI status](unified-app/MULTI_ABI.md) for A133 hardware limitations and the receiver merge boundary.
