"""Package the portable, standard-library one-time installer beside the signed APK."""
from pathlib import Path
import json,zipfile
from bootstrap_panel import payload
r=Path(__file__).resolve().parent
build=json.loads((r/'build-result.json').read_text());apk=r/build['apk'];payload(apk)
out=r/'release';out.mkdir(exist_ok=True)
readme='''Tuya HA Wall Panel - panel setup kit

Supports only the verified G10/PX30 wiring currently implemented by the app.
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
with zipfile.ZipFile(package,'w',zipfile.ZIP_DEFLATED) as archive:
    archive.write(apk,apk.name);archive.write(r/'bootstrap_panel.py','bootstrap_panel.py');archive.write(r/'migrate_sip.py','migrate_sip.py');archive.writestr('README.txt',readme)
(out/'README.txt').write_text(readme,encoding='utf-8')
print('Built setup kit:',package)
