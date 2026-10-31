# Settings and audio-control consolidation — installed

**Single Bluetooth sender installed, 2026-10-31:** SmartWay Panel 0.34.1/code80
on G10 and Audio Streamer 1.11.1/code40 on A133 retain the first music connection
until it disconnects, including while paused. The user explicitly chose this
behavior. Native sender-initiated A2DP events and manual/helper connect requests
share the admission rule, so laptops/tablets cannot bypass phone-only checks.
32 policy checks and 31 incoming-assist checks passed; decoder/sync payloads are
unchanged. Saved setup, +70/0 ms timing and persistent helper files are retained.
Physical phone-plus-laptop validation is pending with the user. The user confirmed
the earlier Bluetooth connection/grouping fixes worked repeatedly on both panels.

**G10 incoming connection assist installed, 2026-10-30:** SmartWay Panel
0.34.0/code79 adds one bounded, bonded-phone music-profile request after an
incoming ACL link on Android 8/9, using the same API as the working panel button.
Fresh pairing and Media audio / Internet profile changes had not resolved the
phone-only G10 failure; A133 worked directly with the same phone. 31 policy guards
and ABI/native-payload checks pass. App-only installation retained saved setup,
the running persistent Bluetooth helper PID and core/helper hashes. No playback
or connection test initiated by the agent. Phone-only physical validation pending.
Audio Streamer 1.11.0 is prepared locally from the same shared code; A133 remains
on its working 1.10.2 installation. Neither codec negotiation nor sync was changed.

**Persistent Bluetooth route, 2026-10-30:** genuine silent PCM remained after
removing the failed reconnect workaround. A permanent music-only Bluetooth input
route now feeds either local Java AudioTrack output or the existing group source.
Group attach/detach leaves that input route open. 65 host pipe-lifecycle checks
pass. The user must reconnect the phone once after provisioning this new route,
then validate start/stop grouping. No decoding/sync payload changes. Installed
APK labels stay 0.33.2/1.10.2; rebuild delivery APKs before future publication.

**Bluetooth helper hotfix, 2026-10-30:** repeated actual `Connect rejected`
failures led to inspection of the vendor APK, which requires privileged reconnect
permission. Automatic disconnect recovery is now OFF in both saved setups and
guarded by an explicit reconnect-capability check in the helper. Legacy
MediaSession-first transport and preservation of initially playing Bluetooth
were restored without changing the unified UI. APK labels remain 0.33.2/1.10.2;
rebuild their release copies to include this helper before future publication.
Physical start/stop and audio validation remains with the user.

**Deployment verified, 2026-10-30:** A133's running helper was found to contain
older executable DEX even though the installed JAR hash was current. Init could
restart it during replacement. The helper installer now stops/restarts its init
service and verifies loaded DEX, not just on-disk assets. A133 was corrected and
both live helper DEX signatures now match the 0.33.2/1.10.2 source build. No APK
or engine change. Physical start/stop validation remains with the user.

**Connection-state fix, 2026-10-30:** SmartWay Panel 0.33.2/code78 and Audio
Streamer 1.10.2/code38 correct the app-backed profile adapter's false
DISCONNECTED result during DISCONNECTING. Actual sink/controller state is now
queried per phone; unknown replies cannot advance reconnection. New transition
and IPC regression checks pass. Saved configuration and timing are preserved;
physical group/playback validation is left to the user.

**Maintenance installed 2026-10-30:** SmartWay Panel 0.33.1/code77 and Audio
Streamer 1.10.1/code37 correct the standalone helper's invalid Android profile
binding. Real APK processes now own profile bindings; a same-owner/system-UID
private IPC channel supplies profile state and fixed commands to the capture
helper. Read-only live checks confirm sink/controller readiness on both panels
and the phone connected to Panel 2. Settings and +70/0 ms timing are preserved.
User playback validation remains pending. See `shared/multiroom/BLUETOOTH_HANDOFF.md`.

**Installed, 2026-10-30:** user authorized “install them on both”. SmartWay Panel
0.33.0/code76 and Audio Streamer 1.10.0/code36 are installed on G10 .40 and A133
.33 respectively. Both updated Bluetooth helpers are running with UID1000 and
no effective capabilities. Saved setup and timing (+70 ms / 0 ms), privileged
panel worker and PTP helpers are preserved; served pages match the builds.
Playback is idle. Physical Bluetooth/group tests are left to the user.
Report: `controls-release-install-local.json`. This supersedes the pending note below.

**Pending local update, 2026-10-30:** SmartWay Panel 0.33.0/code76 and Audio
Streamer 1.10.0/code36 implement native Bluetooth source detection, capture-ready
PLAY and bounded handoff recovery. See `shared/multiroom/BLUETOOTH_HANDOFF.md`.
Host checks pass; hardware validation is pending. Neither update is installed.
The installed versions below remain current. Earlier “recovery pending” notes
describe those installed releases.

**Latest completed release, 2026-10-30:** user said “send them”; installed
**SmartWay Panel 0.32.3/code75** on G10 172.16.1.40 and
**Audio Streamer 1.9.2/code35** on A133 172.16.1.33.
APK application labels were verified using aapt before installation. Notifications,
setup and Home-app instructions use the new branding. Package IDs/signing identities
are unchanged. Saved configuration, timing (+70 ms / 0 ms), helpers and native
audio engines are preserved. Both settings pages served their expected contents.
Playback is idle for user testing; no playback/call/relay/reboot tests were performed.
Download copies: `release/smartway-panel-0.32.3.apk` and
`release/audio-streamer-1.9.2.apk`. Automatic Bluetooth recovery is still pending.
Future installs still require the user's explicit request.

**2026-10-30: authorized UI updates installed successfully.**
G10 now runs unified 0.32.2/code74; A133 runs dedicated Audio Receiver 1.9.1/code34.
Spacing and Bluetooth button placement are included. Both served HTML pages match
the built sources; saved configuration and timing (+70 ms / 0 ms) are preserved.
Setup was opened on G10. Playback is stopped for user checks. Existing media helpers,
the G10 privileged worker, PTP helpers and native audio/sync engines are unchanged.
Automatic Bluetooth stream recovery remains unimplemented. Future updates remain
subject to the user's instruction to wait until explicitly asked to install.

Previous hold (superseded for this batch): do not send or install another app
until the user explicitly says to. The 12 px gap between settings action groups
is edited locally for pending 0.32.2/code74. The combined build/install command
was cancelled during the build. G10 was checked afterward and still runs 0.32.1.
Do not automatically install when resuming or completing the pending build.
Pending local UI adjustment: “Pair a Bluetooth phone” and “Android Bluetooth
settings” now live inside the Bluetooth accordion, with inline action feedback.

## Confirmed Bluetooth grouping recovery issue (2026-10-30)

Live A133 leader / G10 receiver group was connected and unmuted but silent.
Capture kept receiving zero-filled PCM. A133 AudioFlinger showed Bluetooth UID
1002's AAudio track paused after it was moved to the remote-submix route.
Phone pause/resume produced these platform errors: `AAUDIO_STREAM_STATE_DISCONNECTED`,
`systemStart() stream is dead`, start returned `-895`. MediaSession metadata still
claimed playing, so the UI's playing status did not establish an audible source.

With the group left running, user disconnected/reconnected the phone to Wall Panel 2
and resumed playback: **both panels play**. No APK/helper/settings change was made.
This confirms the existing stream fails to recover across capture-route activation;
a fresh Bluetooth connection creates a working stream under the registered policy.
Pause/resume alone is not a recovery. This was not a group transport/Wi-Fi failure.

Pending engineering work: a safe Bluetooth-stream recreation/recovery strategy when
activating capture, with safeguards against unwanted reconnections and misleading
playing status on silent input. It is NOT implemented or claimed fixed. Do not change
AirPlay decoding/synchronization. Respect the delivery hold above; user must authorize
any new APK installation. Read-only evidence is under `test-classes/bt-group-a133-*`.

Completed on 2026-10-30: unified **0.32.1/code73** installed on G10
**172.16.1.40**, dedicated **1.9.0/code33** installed on A133 **172.16.1.33**.
Both see the other room online and authorized. Saved setup fingerprints match,
including SIP, dashboard, network assignments and audio routing. Timing remains
**+70 ms / 0 ms**. Music is stopped; no playback, call, relay or reboot test was run.
The fixed root worker, PTP helper and native audio/sync executables are unchanged.
The shared media-session helper was updated on both panels without starting capture.

Packaging correction: 0.32.0 omitted `settings.html` from the unified APK even
though the API and source-page tests passed. Version 0.32.1 includes it; the signed
APK build and cross-product artifact test now verify all three shared HTML pages.
The G10's actual `/settings` response was checked after installation: HTTP 200,
11,076 bytes matching the source exactly, and 13 settings sections available.
Saved configuration fingerprints still match. See `settings-fix-install-local.json`.

Host regression checks passed, including Linux provisioning fixtures, shared web
flows, volume persistence/call priority, reconnect policy and both APK payload checks.
Mock browser previews produced no script errors or horizontal document overflow.
See `SETTINGS_AND_AUDIO_0_32.md`, `controls-release-install-local.json` and
`controls-release-verification-local.json`. Physical listening/startup/IP-switch
validation belongs to the user. No GitHub push or HA file deployment was performed.

## Historical pre-install planning (superseded by the completion record above)

## Progress checkpoint (expanded implementation, NOT deployed)

Current target versions are unified **0.32.0/code72**, dedicated **1.9.0/code33**.
Installed devices still G10 0.31.2 / A133 1.8.1. No new APK installed this task.

Implemented since initial notes below:
- `SettingsHub.java` shared typed schema, validation, per-section PATCH-like saves,
  administrator PIN/native-session authorization, secret blank=keep/null=explicit clear,
  tools (logs/PTP/Bluetooth/permissions). Uses original preferences; no second config.
- `WallPanelSettings.java` product provider exposes general/dashboard/kiosk, MQTT,
  network assignments, hardware modules/startup, display timers/brightness, knob display,
  physical audio routing/external speakers, complete SIP/video/unlock configuration.
  HA-specific knob entity/page mappings stay in HA (UI explicitly says so).
- `settings.html` shared web/native renderer. Sends ONLY changed fields, preserves
  dirty forms and credentials, prevents accidental navigation loss. Browser tests pass.
- `groups.html` now the one Audio & rooms page, one main Play/Pause (`smart_play`),
  automatic source detection + explicit chooser, track selection without playback,
  room membership, independent volume/mute/timing, presets, bulk volume edits (not a
  separate gain stage). Previous/Next and local AirPlay artwork are being retained.
- Old native Main forms retired: unified MainActivity is PIN gate then shared web portal;
  old Receiver/Bluetooth/Network/SIP/GroupSettings activities are compatibility redirects.
  Dedicated MainActivity opens shared audio portal. GroupManagerActivity supports
  /settings, local token/session injection, Back with dirty-form guard, Hide keyboard,
  and startup HTTP retry. Sections can be passed via fragment; settings JS handles it.
- `RoomVolume.java` central authority in existing wallpanel_groups preferences. Migrates
  once from current actual Android volume (prevents stale group-level restore), shares
  mute/level across AudioEngine/GroupRuntime; source trim remains separate. Group gain
  now compensates within hardware step using `RoomLevel`, removes old double attenuation.
  Own MediaPlayer and AirPlay output gate use same compensation; Android firmware BT/
  third-party direct output remains limited by Android hardware steps. Explicit mute is
  distinct from volume zero; raising volume unmutes. Pending volume changes defer through
  SIP and apply afterward. NEED actual production RoomVolume host-state tests still.
- ReceiverService/BootReceiver/ReceiverRuntime keep lightweight HTTP/discovery alive
  independently of AirPlay enabled state; capture/audio still only opt-in during play.
  Shared Room name also controls BT naming only when audio ownership is enabled (new
  Product.ownsAudio method; currently declared Java default method, could make abstract
  if DX compatibility requires). Dedicated audio always owns BT naming.
- `MediaActivity.java` queries root media-session metadata via new MEDIA_STATUS command
  in BluetoothBridge. Default read-only; no capture triggered by UI/discovery. Explicit
  source changes pause other local BT/app media sessions; no auto pause of AirPlay sender.
  `SourceDecision` handles none/conflicts/paused sources. Receiver rooms are distinguished
  as `receiving` rather than pretending their old saved input is playing locally.
- `smart_play` backend preserves group on pause/resume/member changes and uses one
  source branch. New pending_source/title allow selecting next file without interrupting
  current audio; applied only by main Play. Legacy API commands remain compatible.
  AirPlay controls sender; cross-room AirPlay membership still selected on sender, NOT
  routed through independent grouping. Native HA/local player item controls stay local;
  use local library/BT/App audio for cross-room. UI explains these existing boundaries.
- `RoomIdentity.java`: HMAC nonce challenge verifies moved token-protected peer BEFORE
  sending its existing secret to a new mDNS endpoint; `/api/v1/identity` pre-auth only
  returns proof. Updates old host pin only on proof success. Tests passed. Changing token
  or older peers without proof still needs explicit pairing. Earlier pin limitation below
  is superseded.
- `CameraAddress.java` projects RTSP address WITHOUT credentials for visible editable
  URL, with separate username/password form projection into existing encrypted video_url.
  Editing IP retains login/escaped path/query. Its host test passes.
- HttpApi now serves shared audio page at / and /groups, /settings with typed backend;
  POST requires application/json. Old timing.html still packaged but unserved; can retire.

Tests already passed:
`shared/multiroom/test_network_recovery.py` (actual sockets, reconnect policy, source
arbitration, room-level compensation, HMAC proof mismatches),
`node shared/multiroom/test_groups_web.cjs`,
`node shared/multiroom/test_settings_web.cjs`,
`unified-app/test_camera_address.py`.

Most recent unified build (cell414 / session not collected yet) started before some late
source edits; must rebuild once code is frozen. Prior compile failed only because
GroupRuntime.intercom was package-private; now made public. Need collect cell414 result.
`build_bluetooth_bridge.py` succeeded before later Next/Previous root helper edits;
MUST rebuild helper asset again before final APK builds and deploy fixed helper to both.

Remaining important work:
1. Finish review/polish: source-only validation before stopping group for manual Local/
   AirPlay requests, add local-item capability/airplay control availability to room data
   (hasLocalItem added to AudioEngine and internal audio snapshot; snapshot filters it).
   Current smart_play can stop group before an invalid manual local/AirPlay request.
2. Camera field labels should say unlock URL needs http(s)://, not plain IP. Settings
   section help should explain camera URL editable and keeps saved login.
3. Add production RoomVolume fake-Android host test for migration, no group level jump,
   fine steps, zero vs explicit mute, external OS changes, SIP deferral and durable state.
4. `test_receiver_pcm.py` needs RoomVolume stub; retain all framing/clock/gating tests,
   add live gain refresh checks while nested call leases are held. Existing PcmPump code
   only volume sink line changed, no decoder or synchronization logic.
5. Compile both apps, correct compatibility errors. Run relevant existing store/network/
   setup/back/receiver/HA tests and payload contract checks. No blanket obsolete-test
   deletion; replace assertions for intentionally retired UI with new shared UI coverage.
6. Visual QA with mock API, NOT actual panel playback/UI. Edge executable exists at
   C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe. Headless CLI screenshots
   with separate workspace profile can render 1280x800 and mobile layouts. No playwright
   or jsdom installed; fake DOM helper `shared/multiroom/test_dom.cjs` already supports tests.
7. Confirm setup tools parity, help/defaults, lifecycle/back/PIN, backend validation/
   secret preservation. Avoid promising unsupported new capture paths.
8. Final build helper + both APKs, verify unchanged native AirPlay/group payload hashes.
9. ONLY AFTER EVERYTHING READY, deploy unified G10 .40, dedicated A133 .33, and updated
   media bridge fixed assets using install_app_audio_bridge_local.py (currently hardcoded
   old .100/.101; update to .40/.33). No force-stop/reboot/physical tests. User will test.
   Read SIP IDLE before install. Pause existing group for update (normal transport pause,
   NOT a playback test) so releasing old extra group attenuation does not cause a loud
   direct-Bluetooth jump. Leave playback paused; tell user start first test at low level.
   APK upgrade -r --no-streaming, sync, native Main/Home launch as appropriate. Verify
   versions, API status/settings, discovery, root helper query-only. Do not press Play.
10. No HA files changed by this consolidated task; don't deploy HA or push GitHub.
11. Final concise change summary + links and ask user to test the complete release.

One-off polish scripts `unified-app/{polish_settings_local,finish_audio_ui_local}.py`
were already run; don't rerun (they intentionally assert old markers). They modify real
assets once. All edits under workspace, no git commits. User requested no partial delivery.

User expanded the active task on 30 October 2026: finish settings/web UI, unified playback,
volume and network recovery together. Do not install or deliver another partial update.

Required completion:
- One understandable settings hierarchy, app and browser use the same stored values.
- Shared audio network field appears once, explicitly AirPlay + room discovery/grouping.
- Full configuration through web; native-only Android system operations remain clearly
  identified, rather than pretending the browser can grant Android permissions.
- One primary Play/Pause, automatically identify active source; no speculative switching
  when idle or when multiple sources conflict. Selected source room and member rooms.
- One room volume/mute authority for standalone/group controls, no hidden saved-level
  restoration on group start. Keep source trim separate in advanced settings. Preserve
  low-level control as far as Android's hardware steps allow.
- Lightweight discovery/control service available from boot independently of AirPlay
  enabled state. Audio/capture only runs when needed. Stop remains stopped.
- Automatic IP discovery refresh, verified leader address recovery, receiver-only retry,
  web listener rebind on network changes. Never alter decoder/synchronization engines.
- Build unified and dedicated receiver from shared modules, run meaningful tests, then
  install final bundle on G10 172.16.1.40 and dedicated on A133 now 172.16.1.33. No HA copy
  or GitHub push requested. User performs physical/audio/network-switch tests.

Work already present (not deployed):
- GroupReconnect.java bounded receiver reconnect/address policy.
- GroupService follows verified permanent leader IDs and retries dead clients; source
  capture, leader and per-room data retained. Old process logs cannot change new status.
- GroupHub polls selected network identity and refreshes NSD for network/stale peers;
  verified status gates automatic address recovery. Same-leader join updates endpoint.
- HttpApi uses RebindableServer, closes/reopens listener on network changes only.
- test_network_recovery.py passes real socket rebind/shutdown tests and pure policy tests.
- Versions bumped unified 0.31.3/code72, dedicated 1.8.2/code33. Neither built successfully
  yet: initial build failed using static getBoundNetworkForProcess; now fixed to instance.
- Existing credential host pinning retained: token protected peers still require explicit
  pairing after IP changes; do not silently send credentials to unverified mDNS endpoints.

Current installed versions: G10 0.31.2, dedicated A133 1.8.1. Current services streaming
Bluetooth group. Do not force-stop, reboot, or change playback for a test yourself.

Architecture under consideration (not implemented yet): shared settings schema/provider,
browser renderer reused on native app, unified audio page and source arbitration, shared
room volume manager. Native MainActivity currently monolithic (general/MQTT/modules),
ReceiverSettingsActivity/BluetoothSettingsActivity/GroupSettingsActivity duplicate routes.
HttpApi assets are airplay-app/android/app/src/main/assets/{groups.html,timing.html} and
copied into both products. GroupManagerActivity embeds the local web UI and injects token.
Unified WallPanelApplication can register product settings provider for encrypted config.

Volume diagnosis: GroupRuntime uses saved volume_milli and applies Android ceil step plus
native software factor equal to volume (double attenuation); standalone AudioEngine uses
VolumePolicy hardware, or separate audio_player saved float. G10 STREAM_MUSIC max20.
Centralize saved volume/mute; fine software compensation should be within hardware step,
not an additional complete volume factor. Bluetooth firmware direct and third-party apps
have only Android hardware resolution; do not claim fractional output control there.

Relevant files: shared/audio-receiver/src/com/wallpanel/audioreceiver/{HttpApi,PrefStore,
ReceiverService,PcmPump,BluetoothSinkModule,ReceiverNetwork}.java; shared/multiroom/src/
com/wallpanel/multiroom/{GroupRuntime,GroupHub,GroupService,GroupSettingsActivity,
GroupManagerActivity}.java; unified-app/src/local/wallpanel/app/{MainActivity,ConfigStore,
SipStore,ReceiverRuntime,ReceiverSettingsActivity,AudioEngine,WallPanelApplication}.java.
