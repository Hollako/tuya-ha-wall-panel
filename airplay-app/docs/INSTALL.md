# Trusted-root deployment

See STATUS.md for the latest installed version and evidence. Existing-device APK updates preserve the separate root-owned PTP service; steps that provision helpers are for first installation or an explicitly required helper update.

1. Connect trusted ADB to 172.16.1.26:5555 and verify shell id -u is 0. Do not change global su restrictions.
2. Back up the current APK and only the AirPlay helper directory; check every transfer exit status and hash. Do not use a partial APK backup.
3. Stop the AirPlay package only. Resolve the separate helper PID and verify /proc/PID/exe is /data/local/wpar-airplay/wpar-ptp-helper before terminating it.
4. Stage rebuilt helper and nqptp using trusted ADB in a root-owned staging directory; run scripts/provision-airplay.sh. It derives UID from /data/user/0/com.wallpanel.audioreceiver and installs root-owned fixed executables and an app-owned private shm directory.
5. Install the APK with adb install -r. Updating this APK does NOT update the root-owned helper/nqptp copy.
6. New devices may add scripts/airplay-ptp.rc using scripts/install-airplay-boot.sh. This adds a separate init service; it must not overwrite existing startup definitions. The script refuses to overwrite an existing AirPlay entry. Do not run an unrelated Wall Panel bootstrap.
7. Start com.wallpanel.audioreceiver/.MainActivity and ReceiverService with action com.wallpanel.audioreceiver.START.
8. Verify all running /proc/PID/exe paths and hashes, native UIDs and ports. Both nqptp and Shairport must run as the AirPlay UID with CapEff=0.

No reboot was performed. Separate AirPlay init loading must be verified on the next planned device reboot. Existing panel routing, hardware worker and Wall Panel startup service are untouched.

## A133 / Android11 installation notes

Verified panel: 172.16.1.33, UID10092, receiver Wall Panel 2. Use ARM32 helpers from build/native/armeabi-v7a. Start the app with am start-foreground-service (plain am startservice is rejected when backgrounded). The panel supports an adb-remount vendor overlay; install the independent startup entry with install-airplay-boot.sh vendor. A real reboot test passed. App broadcast delivery may be delayed several minutes by this firmware.

Its incomplete Google verifier blocked ADB APK installation. verification/panel33/install-once.sh shows a one-install ADB-verification override with automatic restoration; do not leave the override disabled and do not disable global package verification. The original null setting was verified after installation and after reboot.

## Audio timing controls (1.1.1)

Open the receiver app and find Audio timing beneath Volume. Range -500..+500ms; positive means later. The +/-5ms buttons allow small adjustments. Keep one panel at0 while adjusting the other. Slider, +/-5ms and Reset save and apply live without app restart. The same controls are available at http://PANEL_IP:8099/ from a phone/iPad. Other settings retain Apply other settings & Restart. Large timing jumps may briefly invoke native resynchronization.

##1.1.6 startup caveat

App label is Audio Receiver; applicationId remains com.wallpanel.audioreceiver. Only enable/autostart flags are mirrored to device-protected storage. ReceiverService ENSURE_STARTED is idempotent. Panel2 firmware still fails to cold-start the boot receiver despite valid switches;1.1.6 alone has not solved this. scripts/audio-receiver-boot-proposed.rc is an uninstalled proposal requiring explicit user approval after an automatic review rejection. Never deploy it as part of a routine APK-only update. Existing root-owned PTP service and all Wall Panel startup files must be preserved.
