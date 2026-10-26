# Current development: independent network routing - 0.15.0

User authorized implementing independent HA and SIP network profiles after reproducing
Ethernet default breaking intercom while Wi-Fi remained connected. Also updated HA
Asterisk addon during work; new panel registration after install succeeded. User
reconnected laptop Ethernet/DHCP for verification: paneleth192.168.3.1, wifi172.16.1.26.
Both current profiles Wi-Fi, fallback OFF (real HA/SIP/camera all on Wi-Fi now).
Setup/Intercom > Network assignments, Auto/Ethernet/Wi-Fi plus per-role fallback.
No global OS route/IP/root changes. Other apps unaffected. No HA files to copy.

NetworkRouter binds main/WebView process to HA selection; MQTT/probe/screenshots
explicit network sockets+DNS. Avoids repeating unchanged process binds. SIP uses
JAIN NetworkLayer+AddressResolver, RTP Network.bindSocket, Contact/SDP chosenIPv4;
explicit interface overrides legacy localIP field (which applies only Auto now).
Unlock Network.openConnection. Native RTSP replaced with Media3 network SocketFactory,
forced TCP interleaving, audio disabled/muted and no focus. Aspect-fit remains NO CROP.
No SIP separate process, so hardware/audio/kiosk shared-state lifecycle retained.
Media3 resources packaged via AAPT2/newSDK35 platform; OS still8,target27,min23; 25
artifact hashes pinned; APK~4MB. Root protocol3/native/boot unchanged. SDK dependencies
downloaded once; source old standalone untouched. See NETWORK_ROUTING.md.

Local regressions pass,333 SIP/12 presentation/21 network tests. First installed
candidate freshly SIPregistered with updatedAsterisk; bothWiFi selected even though
Androiddefault Ethernet103. HA sixavailability online, hardware healthy. WebView
loaded-status true network100/wlanIP. Network-only probe RTSP OPTIONS200/noPLAY,
MQTTTCPtrue, HTTPHEAD405unsupported (actualdashboardGETloads). No calls/audio/video/
relay/reboot tests by agent. User physical checks next. FINAL build installed/verified; adds default-network callbacks and avoids unchanged-
network rebinds. Backup /data/local/wallpanel/before-network-routing.20261025T203832Z.
Network prefs private file, optional protected import consumed. No credentials in
diagnostics. Do not print SIP/XML or RTSP credentials. Leave bothprofilesWiFi now;
realbuildingEthSIPnetwork remainsfuture physicaltest. ExternalApp mode not started.

## Previous boost removal - 0.14.4

User found boost ineffective, attributed low loudness to small built-in speaker,
and explicitly requested removing it. Installed0.14.4/code22, SIPregistered/Hikvision.
Removed SipSpeakerBoost/LoudnessEnhancer from code/DEX, settings, model and diagnostics.
Old encrypted speaker_boost_db key stripped on store migration, account/settings
preserved. Larger112x72dp one-row buttons and ring/call volumes100 remain. Camera,
micmute/door handling unchanged. Source/test cleanup, fake RTP/ringer tests pass12
presentation checks. APK signature checked and absence of boost classes verified.
No physical sound/call/relay tests. See remove-boost-install-result.json.
Backup /data/local/wallpanel/before-remove-boost.20261025T190448Z.
IMPORTANT: do not re-enable/reintroduce software loudness boost without user request.

## Historical boost experiment - 0.14.3 (REMOVED)

Installed0.14.3/code21: buttons112x72dp/icons28dp/text14sp in one row. User found
previous buttons too small and max volume quiet. Read-only codec raw3/saved100 and
Android voice5/5/music20/20 already maximum within established range; firmware
provides loudness_enhancer. Per-session speaker boost for ring/received audio only,
current+6dB with ring/call100. Setting0-12, default0; fallback on unsupported effect.
No mic gain changes. RuntimeJSON speaker_boost_db/call_boost_active for future USER
call inspection (inactive is normal while IDLE). SIPregistered/Hikvision retained.
334 SIP/14 presentation checks + regressions pass incl output-session target600,
cleanup/fallback. No physical tests. USER checks volume/clarity/echo/new UI.
See SPEAKER_BOOST.md, speaker-boost-install-result.json. Encrypted prefs backup:
/data/local/wallpanel/before-speaker-boost.20261025T185351Z. No HA/root/native changes.

## Previous compact controls - 0.14.2

User confirmed all functions good before this update. Requested smaller buttons,
native icons and ONE row, plus ringing/call volumes100%. Installed0.14.2/code20.
Buttons88x58dp, 23dp native Canvas vector phone/hangup/mic/mic-off/lock icons with
12sp labels; one centred horizontal row, only applicable/enabled controls visible.
Door1/2 labels distinguish locks. Green Answer/red End/amber mic-muted retained.
Camera source fitting/cropping, protocols and hardware unchanged. Current saved
ring_volume=100 and volume=100 applied through allowlisted private options import;
credentials/Hikvision selection preserved, temporary JSON consumed, SIPregistered.
No new physical call/sound/video/relay test by agent. User to check new UI and loudness.
Build/signature and existing payload integrity checks pass; cosmetic update, no new
UI tests or broad repeated functional suite. CallActionIcon uses paths, not font glyphs.
See compact-controls-install-result.json. Encrypted prior config backup:
/data/local/wallpanel/before-compact-controls.20261025T184323Z

# Previous explicit door station brand - 0.14.1

User requested save current station as Hikvision and prepare for Akuvox/2N/Dahua later.
Implemented installed0.14.1/code19: station_brand persisted in encrypted SIP config,
settings spinner Generic(no door API) / Hikvision(ISAPI), IntercomDoor brand router
and separate HikvisionDoor wire adapter. Other brands planned, not implemented or
selectable. No guessed protocols. Legacy saved doorServer migrates to Hikvision;
fresh/previously unconfigured generic. Credentials and video/ring/door switches retained.
Confirmed panel runtime station_brand=hikvision and SIPregistered. All local regression
tests pass; generic rejects door commands before network. No physical tests/unlock.
See INTERCOM_BRANDS.md, brand-install-result.json. Prior encrypted prefs backup:
/data/local/wallpanel/before-door-brand.20261025T183159Z. No HA files or root changes.

## Previous call controls and doors - 0.14.0

Installed0.14/code18, SIPregistered. User requested ringier/louder chime/full-window
video with overlay green Answer/red Hangup/micMute. IMPORTANT subsequent correction:
NO CROP/STRETCH, retain complete camera image/proportions and any aspect bars for now.
Math.min surface fit unchanged; video window enlarged under translucent overlay UI.
New telephone-style double ring, currentvolume85 via private settings import.
Per-call RTP silence mute/unmute implemented/tested, resets each incoming call.
Door station authenticated GET capabilities confirms DS-KD8003-IME1(B), channels1-2,
open/alwaysOpen/resume. Only momentary open implemented; no real PUT/unlock sent.
Door station relays used, not Tuya panel relays. Door1 enabled, Door2 disabled/hidden;
both toggles+separate encrypted API login configurable in Intercom settings. User
said nothing wired yet. Private options import consumed, original account preserved.
All local tests pass incl production door client fake HTTP/auth and RTP micmute checks.
No physical tests by agent. User to check chime/UI/micmute and door response. No HA files.
See MIGRATION_0_14.md, call-controls-install-result.json. Backup root encrypted prefs
/data/local/wallpanel/before-call-controls.20261025T173007Z. Do not expose credentials.

## Previous ringing and RTSP migration - 0.13.0

Installed 0.13.0/code17, SIP registered/IDLE after private options import. Built-in
looping chime enabled65%, native RTSP preview enabled for supplied door station .30.
Login authenticated DESCRIBE200/H264, no PLAY/SETUP or physical call tests performed.
Credentials live only in encrypted app SIP configuration; temporary import consumed.
User tests ringing/video before+after answer, audio, cancel/hangup/dashboard return.
Existing audio migration0.12 was USER accepted without noise. Old standalone app and
Linphone removed on explicit request. See MIGRATION_0_13.md and sip-video-install-result.json.
Local tests pass333 SIP +12 production chime/video fake Android checks and regressions.
No HA update, root/startup or hardware changes in0.13. Do not expose RTSP password in
diagnostics/logcat/commands. Backup encrypted config under /data/local/wallpanel/
before-sip-video.20261025T170947Z. Ring/video settings are editable in Intercom settings.

## Previous SIP audio migration - 0.12.0

Acceptance update: user confirmed incoming calls, answer/hangup/close and two-way
audio work perfectly without noise. User then explicitly requested removing the
standalone backup app: com.panel.intercom was fully uninstalled, including app data;
pm list packages -u confirms absence. Unified SIP remains registered/IDLE afterward.
The historical rollback instructions below no longer apply directly to the removed
package. Saved file archives/source were not deleted. Linphone org.linphone was also
fully uninstalled at the user's request and its package absence verified.

User authorized migrate unfinished standalone now. Installed 0.12.0/code16. Integrated
SIP service registered with Asterisk, IDLE; legacy com.panel.intercom disabled/retained.
327 protocol/media tests + fake Android RTP + existing regressions pass. New codec
fixes old missing PCMU complement/bias/clipping defects; noise improvement still USER
test. Optional SIP config encrypted in sip_configuration; no hardcoded credentials.
SIP works independently of MQTT/profile. Call activity shares Home task so the same
WebView returns; root guard retained. Audio routing/focus/amplifier/display coordination
implemented; no agent physical calls/tones/mic/wake/reboot tests. No HA component update.

Old preference XML had unquoted attributes; migrate_sip.py recovers only in memory,
does not modify original. Backups /data/local/wallpanel/before-sip-migration.20261025T163052Z
and local sip-migration-backups/20261025T163052Z/PanelIntercom-original.apk. Private
pendingJSON consumed/deleted and settings encrypted. Script --rollback disables new
SIP before restoring original legacy state/service. Live rollback not exercised.
Never print credential XML, password/digest/logcat auth material. Source WSL unchanged.

App build uses WSL SDK34 D8/Java17 because old dx silently skipped Java7 classes.
Pinned dependency JARs in vendor/sip; root helper compile unchanged. JAIN event scanner
forceStop queue bug fixed by stopping only this stack then interrupt/join its created
scanner; static lifecycle lock/factory reset protects restart. No System.exit test hack.
See MIGRATION_0_12.md, SIP_MIGRATION_PLAN.md and sip-migration-result.json.

## Previous dashboard System UI guard - 0.11.1

User reported still able to slide down. Read-only Window Manager proved visible
overlay com.sznaner.pulldownmenu/.service.MenuService (UID1000, own process1330),
while Android statusbar had correct 0x3ff0000/0x7 flags. Disabled exact package with
backed-up original state0 at /data/local/wallpanel/vendor-menu-original.json. No shared
UID app-ops changed. Overlay window removed. Added it to setup CHECK/APPLY/RESTORE;
15 fake provisioning/recovery cases pass incl exact state restore. 0.11.1 build/kit
includes correction; current support upgraded via --bootstrap-only. User re-tests
swipe; no agent gesture/reboot tests. See vendor-menu-retirement.json. Do not claim
v0.11.0 flags alone blocked this vendor app, or remove intercom/native hardware services.

User requested no pull-down Home/brightness/volume menu over dashboard; future
intercom remains allowed. Installed 0.11.0 with protocol-3 root support via
--bootstrap-only, preserving hardware configuration. New sixth payload component
wallpanel-system-ui.jar; current bootstrap_panel.py is required for staging it.
SystemUiGuard leases fixed statusbar/nav/notification restrictions to dashboard.
PIN handoff retains guard until MainActivity.showForm authenticates; admin cancel
reclaims lease. Root token/socket death restores controls. No device-owner changes,
SystemUI disabling or generic shell API. Does not filter arbitrary third-party windows.

Root flags verified 0x3ff0000 / 0x7 owner local.wallpanel.app.dashboard. Native and
six MQTT availability online. No agent swipes/control/reboot tests; user tests those.
No HA update. See MIGRATION_0_11.md. Preserve top-left admin hold/HA Open setup.

## Previous packaged panel setup - 0.10.0

Latest user request: rename the app Tuya HA Wall Panel. 0.10.1/code13 built with
launcher label, UI/Home guidance, notification/media-session and setup-kit title
renamed. Package local.wallpanel.app and signing key unchanged. User then explicitly requested build/install: 0.10.1 installed and MainActivity opened.
Read-only native readiness and all six MQTT availability topics online. No reboot or
physical tests. Current panel is 0.10.1. User
plans to try a different Tuya model after full development; it is not supported or
auto-provisioned merely because it is Tuya. Keep exact model/hardware preflight.

User requested packaged (1) root service/corrected boot wrapper, (2) hardware
permissions/configuration, (3) disabled conflicting Tuya apps/retired startup services.
Implemented Panel installation Check/Prepare/Restore, protocol-2 restricted setup
operations, exact G10 preflight, private defaults, backups and interruption recovery.
Portable release setup ZIP uses explicit target + signed-APK asset hashes. Real
app-context su is still blocked on stock firmware, so one-time ADB is required there.
No claims that sideload alone grants root or unknown Tuya hardware is supported.

0.10 installed on current panel with --bootstrap-only; settings preserved. Actual
app-context CHECK root/profile/policy/boot all ready, conflicts false; six MQTT topics
online and native ownership verified. No HA update. Prepare/Restore/reboot are USER
tests; do not run physical tests. See MIGRATION_0_10.md and verification records.

Final user interaction: only Restore was tried; no snapshot existed. Saved runtime
was observed off, cause unproven. Isolated tests of production PanelSetup pass five
transition/refusal scenarios, including no-snapshot Restore never stopping hardware.
Added action/runtime before-after diagnostics, installed final APK and restored prior
enabled runtime through normal service start. Final actual-app CHECK reports unchanged
true/true runtime, root/profile/boot ready; native and all six availability online.
No agent Prepare/Restore/reboot/physical tests. Do not claim the unexplained transition
was reproduced or fixed; inspect panel-setup-result.json if it recurs during user tests.

## Previous independent startup / fallbacks retired - 0.9.0

USER ACCEPTANCE: confirmed successful reboot with HA online, Open Setup from HA,
and dashboard URL changes from HA all working. Boot/startup is accepted. Previously
pending dashboard URL delivery is now confirmed working and closed; do not treat it
as unresolved. These were USER tests, not agent-operated reboot/control tests.

Post-user-reboot fix: init was restarting wallpanel_privilege because direct execution
of the /data binary returned Permission denied (SELinux remained Permissive; /data
is nosuid). Dashboard/MQTT were alive but hardware readiness and HA availability
correctly remained offline. Boot now invokes /system/bin/sh with the root-owned
start-privilege.sh wrapper, matching the proven system-entry pattern. Generator
prepare_privilege.py uses privilege_startup.definitions so future provisioning keeps
the fix. Two fake-helper boot-wrapper/UID tests pass. Live helper was started without
rebooting; the app recovered automatically and all six availability topics read back
online. No HA/APK updates or manual I/O tests. Backup:
/data/local/wallpanel/before-shell-boot.20261025-083633. Actual next boot still USER
test. See shell-boot-fix-result.json and availability-readback.json. HA canonical source
was moved by user; use ha-knob/tuya-wall-panel/custom_components/tuya_wall_panel when
needed, preserving HA1.12.0. MCP exposes no matching Wall Panel entities.

User accepted 0.8, then authorized final startup and removal of fallbacks. Installed
0.9.0 and confirmed real app native ownership + MQTT connected. Canonical LWT is now
/availability. Protected boot/update receiver respects saved runtime_enabled; offline
boots keep local hardware; worker retries 5-60s and transport deadline is independent.
HA 1.12 ZIP is ready for USER COPYING ONLY. No user reboot/I/O tests by agent.

Critical discovery: actual Android-app su fails setgid; previous nested-UID shell
probe was not proof of app privilege. Old I/O reports could be satisfied by fallback.
One app-specific root bootstrap is now required: /data/local/wallpanel binaries,
root init wallpanel-privilege.rc, filesystem socket mode600 UID10065, SO_PEERCRED,
only WORKER/CAPTURE own PID, no shell/network/credentials API. Its native executable
is packaged in the APK and staged by prepare_privilege.py. No old hardware controller
is retained as fallback. Native updates need matching privileged assets staged.

Final committed backup /data/local/panalume/before-app-only.20261025-081040.
Old four services stopped; old two init files removed; old three executable paths
archived; separate audio APK disabled. Existing vendor drivers/state kept. New init
will take over bootstrap startup after USER reboot; this boot uses direct startup.
Live read-only verification is app-only-verification.json. 81 HA tests, 15 native
fixtures, four privilege socket cases and six retirement/recovery cases pass, plus
existing Java and APK hash/signature checks. See MIGRATION_0_9.md. Earlier failed
attempt restored services correctly; Windows LF issue and su restriction corrected.
User next copies HA1.12, restarts HA, reboots panel, checks automatic dashboard and
hardware startup. URL delivery is still separate/pending. Do not run physical tests.

## Previous screenshots/reboot migration - 0.8.0

User accepted 0.7 display/brightness/dim/off. Installed app 0.8.0 and management
handover gate, preserving settings and leaving new runtime stopped. HA 1.11 ZIP
and expanded component are prepared for the user to copy, not deployed by agent.
App now schedules 300s screenshots, isolated binary upload, and explicit graceful
reboot. HA tracks management owner separately and suppresses duplicate scheduling.
Root helper is credential-free, memory-only, bounded and cancelable; reboot is never
replayed. Old MQTT/backlight and standalone audio remain automatic fallbacks; the
old MQTT service still supplies global availability, so do not retire it wholesale.
URL delivery remains pending; no screenshot/reboot/other physical tests by agent.
80 HA tests, 14 native fixtures, six fake installer recovery cases, existing Java
checks, isolated binary-upload tests, packaged asset hashes and APK signature pass.
Backup `/data/local/panalume/before-app-management-gate.20261025-002128`;
prefs `/data/local/tmp/wallpanel-app-before-0.8.0/wall_panel.xml`.
User next copies HA 1.11, restarts HA, taps Start/apply hardware, tests screenshots
and reboot. See `MIGRATION_0_8.md`. Mandatory approvals still apply; no conversational
permission questions needed for authorized coding/install work.

## Previous main display migration - 0.7.0

Installed app 0.7.0, replacement shared backlight fallback and display-gated bridge.
User accepted audio 0.6; URL remains pending. New runtime is stopped for user tests.
Defaults dim 60s at ~10% and off 300s TOTAL from last screen interaction. Both timers
and dim brightness are HA configuration numbers; 0 disables a timer. Existing Main
display light controls normal brightness/power. Normal brightness does not follow
the lower dim PWM; supplemental state protects it from legacy management updates.
Shared native runtime uses exact input-name allowlist, first-dark-touch consumption,
request/resource ownership and inherited activity across app/fallback handover.
Board brightness read format `===>N` was verified read-only and supported.
Backup `/data/local/panalume/before-app-display-gate.20261024-235104`; preferences
`/data/local/tmp/wallpanel-app-before-0.7.0/wall_panel.xml`.
HA 1.10 ZIP/extracted folder prepared for user copying only; not deployed to HA.
79 HA tests, 11 native suites, seven dual-service recovery tests, existing Java and
audio checks plus display routing/package/signature checks pass. No agent physical
timing, brightness, touch, audio, relay or reboot tests. See `MIGRATION_0_7.md`.

## Previous audio stage record

Installed 0.6.0 and audio ownership gate. Worker remains stopped for user tests.
User confirms knob is good; URL delivery still fails and is explicitly deferred.
No audio playback, source/output, volume, relay or reboot tests by agent.
HA 1.9 ZIP and extracted component folder are prepared for user copying only.
AudioEngine is inside BridgeService, not a separate APK/service. Root worker owns
trim/power/preference operations. Legacy audio is stopped through a request/resource
gate before takeover and restarted after fallback. No new PCM loopback or recording.
Audio gate backup: `/data/local/panalume/before-app-audio-gate.20261024-225031`.
Prefs backup: `/data/local/tmp/wallpanel-app-before-0.6.0/wall_panel.xml`.
Ten native suites, six gate recovery cases, production volume/audio policy tests,
74 HA tests and existing Java core/store/MQTT checks pass. See `MIGRATION_0_6.md`.
User says full project access: proceed without conversational permission checks;
mandatory environment approvals still apply. User performs physical tests.

## Previous knob stage record

0.5.0 APK and knob gate are installed. User accepted all 0.4 buttons/relay tests.
New knob worker is opt-in; no agent physical tests. User next copies HA 1.8 ZIP,
restarts HA and taps Start/apply hardware. New gate backup:
`/data/local/panalume/before-app-knob-gate.20261024-214940`.
App preferences backup: `/data/local/tmp/wallpanel-app-before-0.5.0/wall_panel.xml`.
See `MIGRATION_0_5.md` for current boundaries, validation and deployment.
Knob uses shared input, half-step decoder, renderer and request/resource locks.
HA desired URL is retained and acknowledged; existing nonempty HA saved URL replays.

## Previous stage record (0.4)

User instructed "start then" after agreeing to move bridges into the APK in stages.
Current installed app 0.4.0; dashboard/Home and saved setup work. Browser-only startup
handover is installed and user confirmed reboot fixed. Hardware still on existing
root bridge. User performs physical tests; do not operate relays/reboot/audio to test.

Implemented: foreground Android runtime service, persistent MQTT (same HA topics
during migration), app-packaged root hardware worker, and cooperative UART lease.
Old process cannot be stopped wholesale because it also handles knob/display/audio.
New flock gate lets it release only UART/button/relay paths and automatically resume
them if the app worker dies. Existing knob/audio/display remain independent.

Read-only device facts: ARM64; `/system/xbin/su` setuid root exists. Nested UID10065
root check using only `id` succeeded; SELinux is Permissive. App privilege check must
still be explicit and non-actuating. App root worker launches through fixed argv and
packaged script; no arbitrary shell commands from HA or dashboard.

Code deployed with app worker still inactive: `wallpanel-hardware/mcu-lease.h`, lease gate in
legacy `panel-direct-mcu.h`, ignore old relay/binding publications/commands while
leased, automatic fallback reloads saved bindings. New core `--run-leased` mode holds
the OS lock, imports shared offline bindings, and accepts strict non-actuating `bind`
commands saved atomically to the same shared file for fallback. Tests extended for
lease handover, knob preservation, reclaim, duplicate bind rejection and persistence.

Completed native, Java core/store/runtime, activation-policy, installer/recovery,
APK encoding/signature and HA tests. Per-panel MQTT base, protected credentials,
foreground lifecycle, explicit activation/return-to-previous controls are implemented.
Gate installed successfully; backup:
`/data/local/panalume/before-app-mcu-gate.20261024-204953`.
Latest read-only lease check: `held:false`. No live worker takeover or physical
tests were performed by the agent. User taps Start/apply for first acceptance check.
HA 1.7 ZIP prepared for user to copy; not deployed to HA by agent.
See `MIGRATION_0_4.md` for actual boundaries and rollback instructions.


2026-10-26: video regression diagnosis and 0.15.1 correction. See NETWORK_ROUTING.md
and video-compat-install-result.json. Network assignments retained. Native decoder
uses real in-band SPS/PPS; no audio changes. Physical video acceptance pending user.

2026-10-26: 0.15.2 fixes duplicate WWW-Authenticate selection (Digest followed by
Basic). Production DESCRIBE-only real camera login accepted. No video played.

2026-10-26: User confirmed 0.15.2 video working with Ethernet attached on a separate
subnet. Next 0.15.3 enables native WebView alpha colors through target API 28,
preserving minimum API 23, cleartext HTTP policy and foreground permission.
See DASHBOARD_RENDERING.md and dashboard-rendering-install-result.json.

2026-10-26: 0.15.5 packaged HA select/combo theme token mapping. Native alpha was
already correct; new HA form palette remained light despite legacy dark colors.
Temporary 0.15.4 inspector disabled and diagnostic APK/ZIP removed. User explicitly
requests they perform visual checks: ask them to open the affected HA dropdown page;
no agent screenshots or UI testing after the final update. Ten isolated tests passed.

2026-10-26: User confirms 0.15.5 closed fields good; requested screenshot reveals
selected option still light. 0.15.6 extends scoped primary state palette mappings;
17 isolated tests pass. User performs menu visual check after installation.
