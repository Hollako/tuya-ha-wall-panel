# Wall Panel Audio Receiver

Android AirPlay 2 receiver for the G10 (Android 8.1 ARM64) and A133 (Android 11 ARM32). Package: com.wallpanel.audioreceiver. Local build: 1.1.0 (code 7); installed devices remain on 1.0.5. Version 1.1.0 adds measured Android output feedback and a live per-panel timing slider. Two-device acoustic synchronization/endurance validation is pending.

Read [STATUS.md](STATUS.md) first. It distinguishes observed playback from unverified AP2/multiroom behavior.

## Privilege boundary

The app and Shairport are ordinary Android processes. An independent SO_PEERCRED-authenticated Unix socket at /data/local/wpar-airplay/ptp.sock leases the fixed PTP daemon. nqptp binds UDP 319/320 as root and immediately drops to the discovered AirPlay app UID. No app-side su or shared Wall Panel helper is used.

## Build

See [docs/BUILD.md](docs/BUILD.md). scripts/build-isolated-native.py rebuilds patched native binaries using existing WSL dependency caches. scripts/build-apk.sh builds android/app/build/outputs/apk/debug/app-debug.apk.

## Evidence

verification/ contains executable identities, service hashes, HTTP lifecycle results, mDNS captures and the initial iPad playback logs. Unmeasured synchronization and reboot behavior remain pending.

All work is local in this airplay-app working copy. The original source directory and existing Wall Panel application were not modified.

Current update: 1.0.4 installed on both panels, with modular Bluetooth receiver status and pairing controls. A2DP Sink is enabled by firmware on .33; disabled on .26. Actual phone playback test remains pending. See docs/BLUETOOTH.md and latest STATUS.md entry.

Update1.0.5 is installed on both panels. Receiver name now applies to both AirPlay and Bluetooth via Apply & Restart; if Bluetooth is off the name applies when it turns on. Actual names match on both panels.

Current synchronization work: see [docs/AIRPLAY_SYNC_PLAN.md](docs/AIRPLAY_SYNC_PLAN.md). Timing adjustments save automatically and feed the running native controller; no app/receiver restart is requested. Other settings retain their Apply other settings & Restart button. No 1.1.0 deployment has been performed.

Local build 1.1.1/code8 adds a mobile web timing page at http://PANEL_IP:8099/ with automatic live saving, +/-5 ms and Reset. API token authentication is preserved. Not yet deployed; both installed panels remain at their previously verified version.

Deployment update: 1.1.1/code8 is now installed and RUNNING_IDLE on both .26 and .33. Web timing pages and API verified. Playback and acoustic synchronization of this new backend still await user testing.

Deployment update: corrected1.1.2/code9 installed on both panels. Backend initialization and status-text contrast corrected; startup, actual native identities and web pages verified. iPad playback retest pending.

Deployment update: Panel2 (.33) now1.1.3/code10 with serialized PCM/flush transport and Java error logging; original G10 remains1.1.2 and was left streaming. Panel2 playback retest pending.

Both panels are now updated to1.1.3/code10. User confirmed Panel2 playback after the transport fix; Panel1 was updated subsequently and its startup/binary identity verified.

Panel1 update:1.1.4/code11 fixes receiver restart socket cleanup;3 on-device restart cycles and real AirPlay discovery replies verified, with iPad playback resumed. Panel2 remains1.1.3 and was not interrupted. BootReceiver autostart on Panel1 confirmed in actual reboot logs.

Both panels now1.1.4/code11 after applying socket cleanup correction to Panel2 as well. AirPlay discovery responses received from both; Panel1 streaming, Panel2 restarted and ready.

Panel2 now1.1.5/code12: audio listener survives session I/O disconnects. Actual same-UID Android probe fails on old1.1.4 connection2 and passes5 forced disconnect/reconnect cycles on1.1.5 without app/native restart. Panel1 left unchanged at last-known1.1.4 pending Panel2 playback confirmation.

Both panels now1.1.5/code12 with reconnect-safe listener. Panel2 real playback observed; Panel1 updated afterward and ready for reselect.

Both panels now1.1.6/code13, renamed Audio Receiver. Direct-boot support and duplicate-start protection added; Panel2 cold-boot startup remains blocked by firmware and is NOT verified fixed. A separate app-only privileged init entry is proposed but not installed; explicit approval required. See latest STATUS.md.

Panel2 now1.1.7/code14: user-approved dedicated one-shot init launch plus boot-service wait for local IPv4 connectivity. Actual reboot verified automatic startup and RUNNING_IDLE/error=null. App/native receiver stays UID10092 with no capabilities; timing binaries/settings unchanged. Panel1 stays on1.1.6 with user-confirmed successful boot. See STATUS.md and verification/panel2-approved-autostart/.
# Shared engine source layout (27 October 2026)

Receiver engine Java now lives in `../shared/audio-receiver/src`; this Gradle
project includes that source set directly. Only this product's launcher and boot
adapters remain in `android/app/src/main/java`. Do not recreate copied engine
classes there. Tuya HA Wall Panel consumes the same canonical source. Native
sources/builds remain here. See `../shared/README.md` and `STATUS.md` for the merge.
Audio Receiver 1.2.0 is installed on A133; G10 uses unified 0.23.0. See the latest STATUS.md deployment entry.

