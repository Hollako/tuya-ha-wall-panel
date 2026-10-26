# Independent startup and retirement of old services - 0.9.0

USER ACCEPTED: after the shell-wrapper boot correction, the user confirmed successful
reboot/HA online, Open Setup from HA, and dashboard URL updates from HA. Startup and
the previously pending URL delivery issue are now confirmed working. Historical
pending statements below describe earlier checkpoints; no agent reboot was performed.

Installed on 172.16.1.26. HA 1.12.0 is packaged for user copying, not deployed by
the agent. The app is active and has confirmed real native hardware ownership and
MQTT connectivity. No agent reboot or manual input/relay/audio/screenshot tests.

## Startup and recovery

Post-reboot correction: Android init could not directly execute the bootstrap under
/data, logging Permission denied while the dashboard and MQTT connection remained
alive. The corrected init entry executes /system/bin/sh with the fixed root-owned
/data/local/wallpanel/start-privilege.sh wrapper, which execs the same restricted
bootstrap. Existing SELinux mode and filesystem mount protections were unchanged.
The provisioner now generates this form too. Backup:
/data/local/wallpanel/before-shell-boot.20261025-083633. Two isolated wrapper/UID tests
pass. The helper was started without rebooting, the app recovered automatically, and
passive readback confirmed all six availability topics online. No APK or HA changes
were needed. The next actual boot remains for the user to verify.

Android's protected BOOT_COMPLETED and MY_PACKAGE_REPLACED broadcasts start the
foreground hardware service only when its saved runtime setting is enabled. The
selected Home activity owns dashboard startup and waits for connectivity as before;
the receiver does not start a second dashboard activity. Stop hardware is an explicit
administrator opt-out and persists across reboot. Start/apply enables it again.

The app holds a partial CPU wake lock; Home retains its existing keep-screen-on
policy. The existing persistent plugged-in awake setting remains 7. Main display
off/dim is still PWM-only. MQTT reconnects independently, so an offline first boot
does not turn off local hardware. A failed worker is closed and retried after 5,
10, 20, 40, then at most 60 seconds; failed generations cannot publish as the new
worker. A separate transport timer bounds blocked MQTT writes even if UI work stalls.
The app now supplies the canonical /availability LWT, publishing online only after
selected hardware and audio are ready. HA 1.12 bounds private app status by that
connection's availability, so stale retained metadata cannot keep setup online.

## Required privilege bootstrap

An actual app-process launch exposed `su: setgid failed: Operation not permitted`.
The earlier nested-shell root check did not establish that an Android app could
escalate. Do not use user I/O acceptance of older stages as proof that their app
worker owned hardware: the fallback services could satisfy those controls.

The app now uses one dedicated privilege bootstrap, wallpanel_privilege. This is
required OS provisioning, not an independent hardware controller or fallback. Its
source and executable are packaged with the app. A root-owned copy is installed in
/data/local/wallpanel and started by /system/etc/init/wallpanel-privilege.rc. In this
current boot it was started directly; init will start it after the user's reboot.

The filesystem socket is mode 0600 and owned by this installation's app UID 10065;
SO_PEERCRED also checks that UID. Only WORKER and CAPTURE <own-worker-pid> are allowed.
No shell, network listener, MQTT credentials, arbitrary executable path or file path
is accepted. The root-owned worker/capture executables are staged from packaged
assets with hash verification. Worker configuration is copied only from the fixed
app-private path, checking owner, mode, type, symlinks and size. The native worker
then performs its existing strict configuration validation. A socket closure ends
the worker; worker death cancels associated capture. The bootstrap itself opens no
UART, framebuffer, GPIO or audio device. Screenshots now use this same restricted
channel, not stock su. Native I/O policy remains in the app-packaged G10 worker.

Installing an APK alone does not provision this root service on another device.
Future native updates must stage their matching packaged executables through the
authorized provisioner; plain Java/UI updates retain the existing bootstrap.

## Retired installation

Stopped panalume_mqtt, panalume_panel, panalume_network and panalume_compat. Removed
the old panalume-panel.rc and panalume-mqtt.rc boot definitions. Their old executable
copies panel-mqtt, panel-backlight and panel-screenshot are archived, not left in
their active paths. Old script paths are non-controlling stop stubs for this boot's
already-loaded init definitions. The separate local.wallpanel.audio package is
disabled and force-stopped. Tuya packages remain disabled as before; vendor kernel
drivers and required Android audio services are preserved. No old fallback remains
running. Existing protected configuration paths are retained to preserve settings.

Backup: /data/local/panalume/before-app-only.20261025-081040. It contains the old
init definitions, scripts, executable copies, prior package state and saved setup.
recover.sh restores them, removes the new bootstrap's init definition, stops its
process and leaves the updated app's hardware explicitly disabled. Preferences
retain their existing owner/mode and encryption. The rollback also restores the
old setup, so any later edits should be exported before manual rollback.

The detached 120-second recovery watchdog and immediate failure path were tested
against a fake filesystem. An earlier live attempt did not reach app health and
successfully restored the original services; final retirement committed only after
three stable startup-health observations. The Windows staging-state line ending
was corrected to LF before changes. Read-only final checks confirm app/MQTT ready,
root worker alive, socket UID/mode, old services stopped, old init/executables absent,
new init present, separate audio disabled, and our app still selected as Home.

## Validation and user next step

Passed: 81 mocked HA tests, existing Java configuration/store/MQTT/audio tests,
receiver manifest and offline/retry policy checks, 15 native regression fixtures,
four real local-socket bootstrap cases using fake executables, six retirement/
recovery cases, packaged worker/capture hashes and APK signature validation.
See app-only-install-result.json and app-only-verification.json for live evidence.

Copy the HA 1.12 tuya_wall_panel folder into /config/custom_components/ and restart
HA. Reboot the panel yourself, then confirm dashboard and hardware reconnect without
tapping Start/apply. Recheck physical controls/audio/screenshots after that boot;
the agent performed startup health checks only. Dashboard URL delivery remains a
separate unresolved issue. No TV at 172.16.1.11 was changed.
