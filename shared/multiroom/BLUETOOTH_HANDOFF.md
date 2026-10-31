# Bluetooth source detection and group handoff

## Persistent Bluetooth route implementation

Live state after disabling reconnect showed genuine zero-valued PCM and no
active native Bluetooth output, while metadata still claimed playing. Dynamic
capture registration was invalidating the vendor's existing AAudio stream.
Changing only labels or playback commands could not restore that stream.

`StableBluetoothPipe` now opens the UID1002/media-only AudioPolicy/AudioRecord
route once per helper lifetime. Bluetooth group clients subscribe to that pipe;
closing a group client never closes or replaces the Bluetooth route. Without a
group subscriber, a normal Java AudioTrack supplies local music output as UID1000,
which the Bluetooth and app capture rules explicitly exclude. With a subscriber,
local output pauses so the existing synchronized room player is the sole output.
No decoder, encoding, network clock, timing calibration or AirPlay engine changed.

The queue is bounded. A stalled group consumer detaches rather than dropping
samples silently or blocking the Bluetooth record loop. Local output skips idle
silence, pauses during communication mode, and recreates a failed local AudioTrack
without changing the Bluetooth input policy. Only Bluetooth music is recorded;
there is no microphone or call capture. The route starts with the helper and
persists even when no group is selected.

65 host pipe checks cover one input open, repeated join/leave, local fallback,
local/group output exclusivity and bounded stalled consumers. Real hardware
playback remains unvalidated until the user tests. Installing this architecture
while a phone is already connected can invalidate the old stream once; one manual
reconnect is required to create the phone's initial stream on the permanent route.
Subsequent group changes must retain `bluetoothRoute.opens == 1`.

This is a helper-only update; installed APK labels remain 0.33.2/1.10.2. Rebuild
the APK artifacts before any next publication so standalone installs include it.

## Helper-only correction after repeated disconnects

Exact live errors after verified deployment were `Connect rejected`. Read-only
inspection of A133's actual Bluetooth APK confirmed that its A2dpSinkService
connect method enforces BLUETOOTH_PRIVILEGED, while disconnect follows a different
permission path. Moving profile bindings to the ordinary APK permitted observation
and disconnect but did not confer privileged reconnect permission. Earlier state
and deployment fixes therefore did not make that reconnect workaround viable.

Automatic recovery was disabled through the existing settings API on both panels.
The helper also refuses to disconnect through an app-backed profile unless it
explicitly reports reconnect capability; an absent capability is treated as false.
This guard applies even if the recovery setting is later enabled.

Comparison with the former Play from Bluetooth action identified added initial
PLAY and transport-order changes. The helper now preserves the first already-
playing Bluetooth stream rather than sending an extra startup PLAY, and uses the
original MediaSession transport first, with AVRCP only as fallback. Paused sources
can still receive PLAY, and subsequent user transport commands remain functional.
The unified UI and source detection are retained. No decoder/sync changes.

80 host recovery/startup/capability checks and 13 IPC state checks pass. Both
helpers are updated with supervisor-safe replacement and loaded-code validation.
User playback testing remains pending; this is not a claim that the underlying
vendor AudioTrack rerouting issue is resolved. Installed APK versions remain
0.33.2/1.10.2. Their existing release copies predate this helper-only hotfix;
rebuild APKs before the next publication or standalone distribution.

## Verified deployment correction (same 0.33.2 / 1.10.2 builds)

After the state fix was installed, the A133 helper's mapped executable DEX still
had an earlier signature although `bridge.jar` on disk matched the new build.
G10's loaded code already matched. Android init supervised the helper and could
restart it between the installer's process kill and atomic file replacement.

The installer now stops `wallpanel_btgroup` through init before replacing files,
then restarts it through init. Read-only verification of the mapped DEX signature
is required; checking only the file hash or process existence is insufficient.
Rollback restores the prior files and restarts the supervisor.

A133 was updated with that sequence. Both running helpers now have the exact
expected DEX signature `0ad40e079a05dbfd4a1fefa6b836c98869e2e649`.
No new APK or native playback-engine change was needed. Comparison with the
former Bluetooth button confirmed that unified `smart_play` dispatches to the
same `group_input` / `_input` Bluetooth startup path. User start/stop validation
is pending now that the state correction is actually executing on A133.

## Connection-state correction: 0.33.2 / 1.10.2

The app-backed profile adapter initially inferred DISCONNECTED from absence in
`getConnectedDevices()`. That list excludes DISCONNECTING devices, so the helper
could issue CONNECT while the actual sink was still disconnecting. The phone then
remained disconnected and required the user's reconnect.

Private `SINK_STATE` and `CTRL_STATE` queries now return the respective real
profile's `getConnectionState(device)`. Missing/failed replies return unknown
(-1), never DISCONNECTED. Recovery waits for the actual disconnect, then connect,
then controller readiness. No additional disconnect attempts or radio toggles
were added. Error logs now distinguish rejected connect/disconnect and deadlines.

Regression coverage: 13 production IPC state/credential/controller checks and
74 recovery-flow checks, including a delayed asynchronous disconnect with an
empty connected list that would reject an early CONNECT. Physical validation
remains the user's task. This correction supersedes the first adapter's state
inference described below.

## Maintenance correction: installed 0.33.1 / 1.10.1

Live testing of the first release exposed a real Android-process integration
failure that host mocks did not cover. G10 rejected receiver registration by the
standalone system-UID process and restarted it repeatedly. Both firmwares also
failed to bind Bluetooth profile proxies from that process. Without a connected
peer, the guarded recovery could never run.

Profile bindings and protected AVRCP observations now belong to the APK's real
application process (`BluetoothProfiles`). The capture helper uses a fixed-purpose
local socket adapter (`AppBluetoothProfile`) rather than attempting those bindings
itself. The APK accepts only UID1000; the helper verifies the endpoint's app UID.
The channel exposes status and a whitelist of same-bonded-phone profile/transport
operations; it is neither a network endpoint nor a general privileged executor.
Addresses remain inside private IPC, not public status. Status reads are cached
briefly, and audio capture remains in the existing least-privilege helper.

Both maintenance APKs and helpers are installed. Actual read-only IPC queries
confirmed `known=true` and `controllerReady=true` on both models, plus the one
connected phone on A133. Saved configuration and +70/0 ms timing were retained.
User playback/group validation is pending. No audio/call/relay/reboot tests were
started by the agent. Decoder and sync payload integrity checks passed unchanged.
Future installer verification now requires live profile/controller readiness.

The following sections describe the original handoff design and its first release.

Installed with the user's authorization on 2026-10-30: SmartWay Panel
0.33.0/code76 on G10 172.16.1.40 and Audio Streamer 1.10.0/code36 on A133
172.16.1.33, including both privileged helpers. Saved configuration and timing
(+70 ms / 0 ms) were verified unchanged. Playback is idle for the user's test;
physical playback validation remains pending.

## Evidence

G10 Bluetooth was audibly playing alone while its MediaSession was inactive and
paused. AudioFlinger showed its native Bluetooth music track active. Java playback
configuration and session metadata alone therefore cannot identify this source.

A133's existing native AAudio stream became disconnected when remote-submix
capture was enabled. Pause/resume did not recreate it. Reconnecting the same phone
with capture left active restored playback on both rooms. This is a capture-route
handoff failure, separate from decoder or synchronization behavior.

## Implementation

- A bounded, cached read-only native-track probe supplements sessions and broadcasts.
  Only Bluetooth-process music output tracks count; recording threads and logs do not.
- PLAY follows confirmed capture-policy readiness. Busy old captures get a bounded
  startup retry. Pending controller readiness gets a bounded deferred PLAY.
- A stalled handoff can reconnect only the same single connected phone once, then
  resume it. It requires observed playback plus known inactive native output during
  a short handoff window; silence or mute alone cannot trigger it.
- User pause, remote pause, intercom hold, disabled Bluetooth, another phone and
  canceled captures suppress recovery. Cancellation after our disconnect restores
  the phone quietly without resuming music. Attempts have a per-direction cooldown.
- Shared Bluetooth settings include an automatic-recovery opt-out, applied live.
- Both app variants use the same helper and source code. Installations must update
  the packaged privileged Bluetooth helper as well as the APK.

The receiver engines, decoding, synchronization, calibration and pairing records
are unchanged. No microphone capture was added. Profile calls follow Android's
[AVRCP controller interface](https://android.googlesource.com/platform/frameworks/base/+/ba12af5/core/java/android/bluetooth/BluetoothAvrcpController.java).

## Validation and limits

Host tests exercise native-track formats, source choice, policy-ready PLAY, busy
capture cleanup, one-shot recovery, deferred transport, pause/call cancellation,
phone changes, disabled Bluetooth and rejected reconnects. Small sanitized track
fixtures preserve the two observed firmware layouts without private dumps.

Real hardware validation remains pending: detect Bluetooth playing alone on each
panel, add/remove rooms during playback, and verify intentional pause, mute and
calls are respected. A short phone reconnect may be audible on affected firmware.
This does not repair radio coexistence, network loss or arbitrary Bluetooth failures.
