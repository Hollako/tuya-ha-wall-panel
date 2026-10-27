"""Package the portable, standard-library one-time installer beside the signed APK."""
from pathlib import Path
import json,zipfile
from bootstrap_panel import payload
r=Path(__file__).resolve().parent
build=json.loads((r/'build-result.json').read_text());apk=r/build['apk'];payload(apk)
out=r/'release';out.mkdir(exist_ok=True)
readme='''Tuya HA Wall Panel - panel setup kit

One APK supports ARM32 (armeabi-v7a) and ARM64 (arm64-v8a).
Dashboard and SIP are available on the A133/Android 11 profile. A133 direct hardware
controls remain disabled pending verification; its four relays and fb1 knob are
not mapped using the G10 wiring. Do not use the G10 setup procedure on the A133.

To install ONLY the app on an authorized ARM panel, without root or vendor changes:
   python bootstrap_panel.py --device PANEL_IP:5555 --app-only

The full root/hardware setup below supports only verified G10/PX30 wiring.
The installer chooses ARM32/ARM64 payloads from the panel's supported ABI list.
The APK includes Panel installation: check, prepare/repair and restore previous setup.
If app-level root is available, Prepare requests root and installs support itself.
On firmware that blocks app su (including our current panel), one-time ADB setup is
required. The APK cannot grant itself root or enable USB debugging.

1. Enable ADB/USB debugging and authorize your computer on the intended panel.
2. Install Python 3 and Android platform-tools (adb).
3. Extract this entire kit into one folder. Run:
   python bootstrap_panel.py --device PANEL_IP:5555
   For USB use the exact serial from adb devices. Use --adb PATH if needed.

This checks wiring before APK installation, preserves existing app settings, installs
the restricted root service and corrected system-shell startup, prepares private
configuration, selects our Home app and disables only known conflicting G10 packages
and old project services. Changes are backed up. It does not replace WebView, disable
system verification or SELinux, open device nodes to all apps, reboot or test outputs.
Unknown hardware or insufficient root/system access stops setup.

Configure profile/modules, dashboard and HA/MQTT in the app, then Start/apply hardware.
Use Panel installation for future checks/repair and Restore previous setup. Restore
means the snapshot before your latest preparation, not a factory reset. Restoring
keeps app hardware stopped so it does not compete with re-enabled vendor components.
Do not uninstall required vendor kernel/audio services. Keep backups.

For already prepared panels needing only new root support:
   python bootstrap_panel.py --device PANEL_IP:5555 --bootstrap-only

No HA configuration files are included or automatically deployed by this kit.

The dashboard System UI guard uses the packaged root-owned Java helper. It blocks
pull-down/quick settings and system navigation; administrator setup releases it.
With PIN locking enabled, the guard remains active until authentication succeeds.
Intercom/other app windows are not filtered. Test swipe and admin recovery yourself.

SIP audio intercom is optional. Configure its server/account in Intercom settings.
It registers independently of MQTT; video and HA SIP controls are later work.
To migrate an existing Panel Intercom installation after APK setup, use:
   python migrate_sip.py --device PANEL_IP:5555
This needs ADB root, backs up the old APK/settings, disables its package, imports
the account privately into encrypted app storage and verifies registration only.
An active old RTP/call state stops the handoff. No test calls are placed.
To return to the retained standalone installation:
   python migrate_sip.py --device PANEL_IP:5555 --rollback
Keep the protected device backups and the local sip-migration-backups folder.
'''
package=out/('tuya-wall-panel-'+build['version']+'-setup.zip')
readme+='''
AirPlay receiver merge (disabled until prepared):
The unified and dedicated receiver builds use shared/audio-receiver sources.
On an existing trusted-root panel with the working standalone Audio Receiver,
install this APK first, then inspect the read-only plan:
   python receiver_cutover.py --device PANEL_IP:5555 --adb PATH_TO_ADB
To perform the backed-up handoff:
   python receiver_cutover.py --device PANEL_IP:5555 --adb PATH_TO_ADB --apply
It disables but retains the standalone app/data, preserves receiver name, timing
offset and trim, changes only the existing PTP helper's authorized app UID and
opens unified Home. No reboot or audio tests. The printed device backup path is
needed for --rollback BACKUP_PATH --apply. It refuses active SIP calls, unknown
helpers/UIDs or missing existing PTP init service. Fresh receiver provisioning is
separate; G10 hardware setup alone does not provision AirPlay timing support.
Audio receiver settings are available from setup. Blank receiver interface follows
the HA network assignment. The unified optional receiver web server stays OFF.
Please test playback, mute/volume, call interruption/resume and both reboot types.
'''
with zipfile.ZipFile(package,'w',zipfile.ZIP_DEFLATED) as archive:
    archive.write(apk,apk.name);archive.write(r/'bootstrap_panel.py','bootstrap_panel.py');archive.write(r/'migrate_sip.py','migrate_sip.py');archive.writestr('README.txt',readme)
    archive.write(r/'receiver_cutover.py','receiver_cutover.py');archive.write(r/'build-result.json','build-result.json')
(out/'README.txt').write_text(readme,encoding='utf-8')
print('Built setup kit:',package)
