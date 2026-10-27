# One APK for ARM32 and ARM64 — 0.21.0

The APK includes both `armeabi-v7a` (ARM32) and `arm64-v8a` (ARM64) Linux native
payloads. Java, SIP and Media3 code remain shared. Native executables live in
assets, so Android may report no primary APK ABI; the actual executable selection
comes from `Build.SUPPORTED_ABIS`, not the CPU's advertised 64-bit capability.
The known A133 has 32-bit Android despite its ARMv8 CPU and selects ARM32.

`NativeAssets` is the shared selection interface for future optional receiver
engines. It prefers ARM64 when the OS advertises it, otherwise accepts ARMv7. It
rejects unsupported ABIs and unknown profile/path names. It does not assume that
ARM32 and ARM64 have identical hardware wiring.

## Packaged profiles

| Profile | ARM32 | ARM64 | Runtime hardware status |
| --- | --- | --- | --- |
| G10 / PX30 | Compiled | Compiled | Existing exact-layout adapter and guards |
| A133 / Android 11 | Compiled | Compiled for future matching OSes | Candidate inventory only; direct controls disabled |

The A133 app profile permits Dashboard and SIP configuration. This is **not a
completed A133 hardware port**: knob input/rendering, four relays, amplifier/audio
routing, display policy and privilege/startup provisioning still need model
verification. Android audio/video behavior also requires physical testing there.
The existing G10 driver must not be enabled against A133 GPIO or framebuffer maps.

The A133 candidate probe has only `--describe` and read-only `--probe` operations;
runtime arguments are rejected. It reports `layout_matched:false` and
`runtime_ready:false`, so the existing G10 bootstrap cannot treat it as verified.
The inspected candidate has fb1 for its 240×240 knob, four relay nodes, no physical
buttons and no knob LED. No relay command values, GPIO pins, mirror orientation or
backlight paths are inferred from those observations.

## Assets and integrity

`assets/hardware/catalog.json` indexes profiles and ABI variants. Each
`hardware/<profile>-<arm32|arm64>/manifest.json` records architecture, hashes and
runtime readiness. The build checks ELF class and machine type for the worker,
capture helper and privilege service. ARM32 uses a static ARMv7 hard-float Musl
target; ARM64 uses static AArch64 Musl. The Java System UI helper is architecture
independent and is included with each variant.

Worker extraction validates profile, ABI, readiness and checksums. G10 root setup
stages the ABI-specific broker/worker/capture trio and verifies the same six
provisioning files as before. Existing G10 private runtime paths remain stable.
The scripts still require the exact G10 hardware probe before disabling packages
or changing root startup policy. Selecting an A133 profile does not grant support
for G10 hardware modules.

## Installation and testing

The standard APK can be installed normally on either ARM architecture. To install
only the app over authorized ADB without root, disabling Tuya, selecting a launcher
or provisioning hardware:

```text
python bootstrap_panel.py --device PANEL_IP:5555 --apk tuya-wall-panel-0.21.0.apk --app-only
```

Full provisioning remains G10-only. `--app-only` and `--bootstrap-only` are mutually
exclusive. Unknown ABIs are refused before installation. HA integration does not
need an update for architecture packaging.

Local checks verify every packaged native ELF/hash, Python and production Java ABI
selection, A133 module guards, a simulated app-only install with no root/vendor
operations, original setup-package tamper checks and the existing app regressions.
No APK installation, device mutation or physical tests were performed for 0.21.0.
ARM32 execution and A133-specific hardware behavior still require device testing.

## AirPlay merge boundary

The receiver can reuse `NativeAssets.abi()` and add matching ARM32/ARM64 assets with
their own hashes. Keep receiver lifecycle optional. Existing AudioEngine and SIP
audio priority should remain the common audio owner; combining APK files alone
would not resolve focus, routes, mute/volume synchronization or call preemption.
