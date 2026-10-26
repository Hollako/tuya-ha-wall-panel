# App screenshots and graceful reboot - 0.8.0

User accepted the 0.7 display/brightness/idle policy. App 0.8.0 (code 10) is installed
on 172.16.1.26 with settings preserved. HA 1.11.0 is packaged for the user to copy;
the agent has not deployed it to HA. Runtime remains stopped for user activation.
Dashboard URL delivery is still pending and was not changed in this stage.

## Behavior

The foreground app schedules screenshots every 300 seconds from ownership/manual
refresh. It does not capture immediately at activation, retry continuously, wake
the display, write an image to flash, or enqueue captures while offline. Manual HA
Refresh screenshot remains available with a five-second throttle. HA 1.11 disables
its own automatic scheduler while app management owns the device, avoiding a second
capture schedule; it retains the old scheduler during legacy fallback.

A separate app thread and MQTT connection upload the binary PNG using QoS1 and a
validated PUBACK, without subscribing or publishing an availability will. Ordinary
command transport retains its 16 KiB inbound limit. Images are bounded to 8 MiB,
validated before publication, non-retained, and held in memory only. The capture
helper has no credentials or network code; it runs the fixed Android screencap
command, checks its native owner and management locks, and stops on timeout, owner
loss or control-pipe closure. Capture has a 10-second native limit; the app cancels
capture/upload after 25 seconds. Screenshot failure does not stop the hardware.

Reboot accepts only an explicit, non-retained REBOOT on the exact management topic.
The native worker requires management ownership, validates canonical request IDs,
deduplicates pending requests, and executes the existing graceful Android command
after 1.5 seconds. Pending reboot is canceled if the worker stops before execution.
It is never restored after reconnect, process restart, or fallback.

## Handover and remaining external services

Separate request/resource locks make the legacy management process stop publishing
device state and ignore screenshot/reboot commands while the app owns management.
It cancels pending actions and finishes/kills existing children before relinquishing
the resource. It resumes if the app exits. Display fallback remains independent.
HA tracks management ownership separately from display ownership, so stale merged
display fields cannot select the wrong screenshot scheduler.

All migrated hardware/control functions now have implementations packaged in the
app, pending user screenshot/reboot acceptance. The old root MQTT/backlight services
and standalone audio APK are still retained for automatic fallback. The old MQTT
process still supplies global availability. Retiring those services and making app
availability independent is a subsequent stage; do not stop them wholesale yet.
The app still uses Android/Linux drivers and existing protected state files.

## Deployment, recovery, validation

Management gate backup:
`/data/local/panalume/before-app-management-gate.20261025-002128`.
Preferences backup: `/data/local/tmp/wallpanel-app-before-0.8.0/wall_panel.xml`.
The gate installer updated only the legacy MQTT binary, preserving the running
backlight process. A detached watchdog and immediate failure recovery protect the
swap. The previous 0.7 APK remains available locally for rollback. Gate recovery is
in the backup's recover.sh; stop the app runtime before any manual gate rollback.

Passed: 80 isolated HA tests; 14 native fixture suites (including synthetic capture,
fake reboot and management handover); six fake installer/recovery/watchdog cases;
existing Java configuration/store/MQTT/audio/display tests; binary PNG fake-broker
upload/PUBACK tests; packaged worker/helper SHA256 checks; APK signature validation.
No agent screenshot capture, reboot, audio, relay, dim/off or touch-wake tests.

User next: copy HA 1.11's tuya_wall_panel folder into /config/custom_components/,
restart HA, tap Start/apply hardware in the app, then try Refresh screenshot and
Reboot yourself. Check the image's automatic update after five minutes. Existing
entity IDs are unchanged. Use previous hardware service remains the fallback.
