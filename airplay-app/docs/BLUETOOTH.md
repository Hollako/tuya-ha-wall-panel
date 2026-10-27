# Bluetooth receiver integration

2026-10-26. Phone sends music; panel receives with firmware A2DP Sink.

## Device evidence

- 172.16.1.33 Android11: effective profile_supported_a2dp_sink=true and profile_supported_avrcp_controller=true; persist.bluetooth.a2dp.sink=true. After enabling Bluetooth, dumpsys reports A2dpSinkService, AvrcpControllerService, A2DP Source State Disabled, A2DP Sink State Enabled. Name BGM-0704. Actual phone pairing/audio remains unverified.
- 172.16.1.26 Android8.1: Bluetooth.apk contains sink service, but profile_supported_a2dp_sink and profile_supported_avrcp_controller are false. Manifest enables sink service through that resource. No Bluetooth package resource overlays were listed. Runtime starts A2dpService (source), not sink. Name CCaispeech-0B00. Current firmware configuration blocks reception; an app update cannot enable this profile. No firmware patch was installed.
- Bluetooth was initially OFF on both; enabled through trusted ADB after user authorized stopping playback and updating. No paired devices were removed. Evidence: verification/bluetooth/.

## App boundary

BluetoothSinkModule owns firmware capability inspection and observation of adapter/sink connection/playback broadcasts. AudioSourceModule is the common observation interface; AirPlaySourceModule adapts existing AirPlay state. ReceiverService owns the Bluetooth observer independently of AirPlay start/stop. MainActivity has a separate UI observer with cleanup. No Bluetooth stack, privileged helper, PCM decoder or routing changes were introduced.

Status API adds sources.airplay, sources.bluetooth and bluetooth. sinkConfigured describes firmware configuration, not successful playback. connected/playing remain null until events establish a session; sessionStateKnown=false makes that limitation explicit after app launch with Bluetooth already enabled. Null is not disconnected. Firmware controls Bluetooth decoding/output and media volume. Existing audio/stream fields and app trim are AirPlay-specific.

App controls request Bluetooth enable, 120-second discoverability, and open system Bluetooth settings. Android confirmation/pairing dialogs remain responsible for consent. Pair control is disabled when the firmware sink resource is false or unknown. The app does not silently change Bluetooth name or pair/connect devices.

## Future audio manager contract

Keep receiver implementations separate. A future SourceCoordinator should consume source snapshots and own selection, focus, gain, interrupt/resume policy and SIP call priority. It needs real per-source suspend/resume and volume adapters before automatic arbitration can be claimed. A2DP playback is owned by firmware; pausing a Java AudioTrack does not silence the firmware sink. AVRCP phone pause is best effort, and call priority needs tested firmware audio-focus behavior or a separately reviewed narrow control integration. Do not add Bluetooth commands to the existing AirPlay PTP helper or Wall Panel privilege service.

Current build has no automatic source selection, focus arbitration, SIP support, Bluetooth transport buttons or unified volume. Pause AirPlay before a Bluetooth listening test to avoid simultaneous sources. Phone or Android media volume controls Bluetooth; existing trim controls AirPlay only.

## Manual acceptance

On panel .33 open Bluetooth receiver > Pair phone, accept discoverability if requested, pair from the phone to BGM-0704 and confirm any pairing code. Select it for media audio and play a track with AirPlay paused. Check sound, sink connection/playback broadcasts, API state, phone volume, pause/resume and reconnect. Existing phone bond was preserved. Repeat after receiver stop/start and an app restart; initial unknown session state is intentional. Original panel needs a separate firmware feasibility review and rollback plan before sink-enabling changes.

## Shared name (1.0.5)

The app Receiver name is now shared by AirPlay and Bluetooth. Apply & Restart saves it for both; Bluetooth renaming is asynchronous and uses ordinary BLUETOOTH_ADMIN permission on these Android8/11 devices. Service startup, preference changes and Bluetooth ON trigger synchronization. Turning Bluetooth on is still explicit; an OFF adapter defers the rename. API desiredName/nameMatchesReceiver/nameSyncError expose actual completion or rejection. Actual installed firmware names verified as Wall Panel (.26) and Wall Panel 2 (.33). An Android-settings name edit is overwritten at the next synchronization trigger. No independent Bluetooth-name field exists. This supersedes the earlier statement that the app does not change the Bluetooth name.

## Original-panel investigation outcome

Read-only analysis found native A2DP sink/SBC/AudioTrack support alongside disabled profile resources. A configuration-only overlay is a plausible next experiment, not verified working support. Local candidate and trial/rollback notes: ../firmware/bluetooth-sink-test/README.md. The candidate built and signed successfully but was not installed; original panel firmware is unchanged. User has confirmed actual Bluetooth playback on Wall Panel2.

## Controlled G10 sink trial - applied and clean-boot verified

User explicitly authorized the prepared test. Normal /data APK install succeeded but was not recognized by OverlayManager; it was uninstalled. A new standalone overlay APK was then added at /vendor/overlay/WallPanelBluetoothSinkTest/BluetoothSinkTest.apk (root:root0644, vendor_overlay_file context). /vendor was remounted only for this addition and returned read-only. Original Bluetooth APK/native binaries were not replaced. Package com.wallpanel.bluetooth.sinktest is mutable and enabled for user0; idmap inspection confirms all3 mappings (source=false,sink=true,controller=true). Only the3 corresponding sink/media-browser/controller components were explicitly enabled; baseline had no component overrides.

Initial overlay activation/toggle produced two Bluetooth HAL SIGSEGV crashes and stale source-profile resources. After waiting for Bluetooth OFF and restarting the Bluetooth process, both sink and controller initialized successfully. A subsequent clean reboot with the enabled overlay passed: Bluetooth ON, A2dpSinkService and AvrcpControllerService present, source A2dpService absent, zero Bluetooth crashes after over1minute. Actual phone SDP/audio output remains to be tested; do not claim working sound yet. Existing iPad bond retained.

Both original Wall Panel helper/core executable hashes and wallpanel-privilege.rc hash match pre-trial baselines; original Bluetooth APK/JNI/HAL hashes also match. Factory SELinux Permissive and global su restriction unchanged. Existing Wall Panel startup/routing files untouched. Original AirPlay helper and receiver now have verified reboot autostart; final API RUNNING_IDLE,error=null,PTP lease available. Panel2 was not modified and remains STREAMING.

Root-only rollback material is in /data/local/wpar-bt-sink-trial/ (0700), including pairing config backups and rollback.sh (0600). Local script firmware/bluetooth-sink-test/rollback-panel26.sh disables only the new overlay, restores the3 DEFAULT component states, and reloads Bluetooth. It leaves the new APK disabled; no pairing/data wipe or original firmware replacement. Rollback has not been executed. This test uses receive-only Bluetooth, so panel-to-headphones A2DP output is disabled. Evidence: verification/bluetooth/sink-trial/, especially clean-boot-check2.json, final-integrity.json and final-status.json.

Next: reconnect iPad to Wall Panel, check Bluetooth audio destination and listen. If phone caches old service list, try Bluetooth reconnect first; re-pair only if needed.
