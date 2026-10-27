"""Prepare verified public binaries; run with Windows Python. No network/panel access."""
from pathlib import Path
import hashlib,json,shutil,zipfile
root=Path(__file__).resolve().parent.parent
out=root/'.publish/v1.18.0';out.mkdir(parents=True,exist_ok=True)
record=json.loads((root/'unified-app/receiver-merge-result.json').read_text())
files={
 'tuya-wall-panel-0.23.0.apk':root/'unified-app/tuya-wall-panel-0.23.0.apk',
 'audio-receiver-1.2.0.apk':root/'airplay-app/android/app/build/outputs/apk/debug/app-debug.apk',
 'tuya-wall-panel-0.23.0-setup.zip':root/'unified-app/release/tuya-wall-panel-0.23.0-setup.zip',
 'tuya-wall-panel.zip':root/'ha-knob/tuya-wall-panel.zip',
}
expected={'tuya-wall-panel-0.23.0.apk':record['unified']['sha256'],'audio-receiver-1.2.0.apk':record['dedicated_receiver']['sha256']}
for name,source in files.items():
    digest=hashlib.sha256(source.read_bytes()).hexdigest()
    if name in expected and digest!=expected[name]:raise RuntimeError('Installed build changed: '+name)
    shutil.copyfile(source,out/name)
notes='''HA integration **1.18.0**, unified Android app **0.23.0**, and dedicated Audio Receiver **1.2.0**.

## Downloads (expand Assets below)

| File | Use |
| --- | --- |
| `tuya-wall-panel-0.23.0.apk` | Unified wall panel: dashboard/app mode, hardware, SIP and optional AirPlay |
| `audio-receiver-1.2.0.apk` | Dedicated AirPlay receiver, without the unified dashboard/hardware UI |
| `tuya-wall-panel-0.23.0-setup.zip` | Unified APK plus supported-panel setup/migration tools |
| `tuya-wall-panel.zip` | Manual HA integration install; HACS users update through HACS |
| `receiver-native-source-and-relink-1.2.0.tar.gz` | Matching native sources, configuration, notices and relink files |
| `SHA256SUMS.txt` | Download checksums |

Both APKs include **ARM32 and ARM64**. Install one receiver product on each panel;
the unified app already includes the receiver. Neither APK grants itself root.
G10 direct hardware and A133 standalone receiver are the tested deployments;
A133 full hardware support and other Tuya models are not implied by ABI support.

## Changes

- One shared receiver engine and SIP protocol core; fixes carry across product builds.
- Knob weather/date/time screensaver, optional edge ring and mute indicator.
- Direct local media/knob control while keeping HA feedback.
- Optional installed-app mode with prepared kiosk support and SIP takeover.
- AirPlay source/metadata in the HA media entity, receiver enable and live timing/trim controls.
- Local SIP call interruption of receiver output; source-side AirPlay play/pause is **not implemented**.
- Receiver network/startup/recovery fixes and protected migration/rollback tools.

## Updating

Update the HA integration to **1.18.0**, then restart Home Assistant. Keep the same
integration entry and MQTT base topic. Install the matching APK over its existing
package; these APKs retain the signing identities used by the tested installations.
Read `RECEIVER_MERGE.md` and the setup kit README before transferring an existing
standalone receiver to the unified product. Initial receiver timing-helper
provisioning remains separate from G10 hardware preparation.

The G10 unified and A133 dedicated deployments were installed and user-confirmed
working. Automated HA, SIP/network/video, audio-gate/PCM and migration checks pass.
Bluetooth G10 glitches, firmware Bluetooth/SIP arbitration, and clean-host Android
build automation remain unfinished. HACS installs only the HA integration.

See the repository's `BUILDING.md`, `THIRD_PARTY.md` and native source bundle for
the tested incremental build/toolchain and original dependency notices.
'''
(out/'release-notes.md').write_text(notes,encoding='utf-8')
print('Prepared',len(files),'verified binary assets and release notes')
