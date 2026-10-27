# Tuya HA Wall Panel — unified app

Current local build **0.22.0** adds [installed-app mode](EXTERNAL_APP_MODE.md) with app selection, administrator hotspot, root-backed task recovery and intercom return. ARM32 and ARM64 remain in one APK. [Architecture and A133 status](MULTI_ABI.md): Dashboard/SIP profile available; A133 direct hardware remains disabled pending verification. Installed on the G10 with its web dashboard still selected; external-app physical checks are pending. The A133 remains unchanged.

Build **0.19.0** / HA **1.16.0** adds [direct local media knob control](LOCAL_MEDIA_KNOB.md): rotate volume, press mute, hold play/pause, double press next page. The panel's own Audio entity uses its local audio engine and knob feedback; other media entities use HA.

Builds **0.18.0–0.18.2** add the [knob screensaver](KNOB_SCREENSAVER.md): local time/date, weather icons and temperature, with a 15-second return from controls and an optional edge circle.

Build **0.17.1**: prevent the dashboard's native gray status-bar
background from returning after UI visibility changes. No HA or receiver-app
changes. See `DASHBOARD_FULLSCREEN.md`. Not published to GitHub.

Previous local build **0.17.0**: knob-only wake/white LED on physical use, then
small display off/red LED after 15 seconds of inactivity. Local policy, optional
HA Knob activity mode switch. See `KNOB_ACTIVITY.md`. Not published to GitHub.

Previous build **0.16.0**: HA Reload dashboard and Restart app commands, tested
successfully by the user. See the repository root README for current installation
and HACS instructions. Historical notes below document development milestones.

Previous build **0.15.0**: independent network assignments for HA/dashboard
and intercom. Setup > Network assignments; current BOTH Wi-Fi, fallback OFF. Verified
HA dashboard/MQTT and fresh SIP registration with updated Asterisk while Ethernet is
Android default; RTSP OPTIONS over Wi-Fi successful (no media playback). User tests
call/video/audio next. No HA files to copy. See `NETWORK_ROUTING.md`.

Previous build **0.14.4**: speaker boost removed at user request. Larger
one-row icon buttons retained; ringing/call volumes100%, normal audio playback.

Historical build **0.14.3** (boost removed in 0.14.4): larger one-row icon controls, adjustable intercom
speaker boost (current +6dB), ringing/call volumes100%. User checks loudness/clarity.
See `SPEAKER_BOOST.md`. Camera framing unchanged.

Previous build **0.14.1** explicitly saves the door station brand as
Hikvision (ISAPI), with a separate unlock adapter. Generic SIP/RTSP has no vendor
door commands. Akuvox/2N/Dahua adapters are future work. See `INTERCOM_BRANDS.md`.

Previous build **0.14.0**: telephone-style ring at85%, full-window uncropped
camera view with overlay Answer/Hangup/Mic mute, optional door-station unlock1/2.
Door1 enabled and Door2 disabled; editable in Intercom settings. SIP registered;
physical checks left to user. See `MIGRATION_0_14.md`. No HA files to copy.

Previous build: **0.13.0**, built-in incoming chime and optional RTSP video
preview in the call window. SIP account preserved/registered; camera configured.
Intercom settings controls ringing/volume and video URL, encrypted with SIP settings.
User tests physical ringing/video; no agent call/audio/camera playback tests performed.
See `MIGRATION_0_13.md`. Previous0.12 audio baseline accepted by user, noise cleared.

Previous build: **0.12.0**, optional SIP audio intercom integrated.
Settings > Intercom settings holds the encrypted account, auto-answer and audio
options. Standalone Panel Intercom is backed up/disabled; integrated registration
with Asterisk and hardware/HA online verified. User tests incoming calls, audio,
dashboard return, display wake and reboot. Video/door control/HA SIP entities are
later work. No HA files to copy for this milestone. See `MIGRATION_0_12.md`.

Previous build:

Current installed build: **0.11.1**, dashboard pull-down/System UI protection.
The vendor com.sznaner.pulldownmenu overlay was separately identified and disabled
with its original state backed up; the current installer includes it in its exact
known-package policy. Android status-bar flags alone do not control that overlay.
Android notification/quick-settings expansion and system navigation are disabled
through a root Binder lease. Top-left admin hold and HA Open setup remain available;
PIN-protected setup does not release controls before authentication. No HA update.
Read-only flags and native/HA online verified; physical swipe/reboot tests are USER
tests. See `MIGRATION_0_11.md` and the 0.11.1 setup kit for matching root support.

Previous build:

0.10.1 is built with the new user-requested app name throughout the launcher,
setup/dashboard headings, Home guidance, media session and foreground notification.
Package/signing identity and settings are unchanged. This name-only update is installed and opened on the panel. Read-only checks confirm
native readiness and all six HA availability topics online. No physical tests or reboot.

Current: **0.10.0** adds Settings > Panel installation: Check, Prepare / repair,
and Restore previous setup. Root support, corrected boot wrapper, private hardware
configuration and known Tuya/legacy startup retirement are packaged together.
Use `release/tuya-wall-panel-0.10.0-setup.zip` for another verified G10 panel.
Stock firmware that blocks app-level root still needs its one-time ADB installer;
sideloading alone cannot grant root. Current panel support was upgraded with preserved
settings; app-context status, native ownership and HA online were verified read-only.
No new HA files are needed. Prepare/Restore/reboot checks remain with the user.
See `MIGRATION_0_10.md` for installation, scope and recovery.

Previous 0.9.0: independent boot/update startup, offline local operation and
worker recovery. The old fallback services and boot definitions have been retired;
their executable copies are archived and the separate audio APK is disabled. One
UID-restricted privilege bootstrap remains necessary because the stock su rejects
root access from the actual app process. The app now has verified native ownership
and MQTT connectivity. Copy HA **1.12**, then perform the panel reboot test yourself.
User confirmed automatic startup/HA online after reboot, Open Setup from HA, and
dashboard URL changes from HA all working. The URL sync issue is closed.
See `MIGRATION_0_9.md` and `app-only-verification.json`.

Version 0.8.0 is installed with screenshots and graceful reboot packaged in the app.
HA 1.11 keeps manual Refresh screenshot and lets the app schedule one capture every
five minutes. The user accepted 0.7 display controls. Copy the new HA folder, restart
HA, and tap Start/apply hardware before testing screenshots/reboot yourself. Previous
services remain automatic fallback and still supply global availability; retirement
and the pending dashboard URL delivery issue are separate work. See `MIGRATION_0_8.md`.

Version 0.7.0 added main display/brightness in the app worker and a shared
init fallback. HA 1.10 adds Dim after, Display off after and Dim brightness. Defaults
are 1 minute / 5 minutes TOTAL / ~10%. Both timers are configurable and zero disables
each timer. Touch restores normal brightness; only a dark wake touch is consumed.
Knob/buttons do not wake/reset the main display. App activation and physical checks
remain with the user. See `MIGRATION_0_7.md`. Screenshots/reboot and URL fixes remain.

Version 0.6.0 added Android audio/media control inside the main app and
amplifier operations in its native worker. The user accepted the knob migration.
Tap **Start / apply hardware** for audio acceptance checks. The standalone audio APK
remains installed only as fallback and is stopped while the app owns audio.
Main display, screenshots and reboot still use the existing bridge. HA integration
1.9 preserves the audio entities and adds independent amplifier availability.
Dashboard URL delivery still fails for the user and is deferred until audio passes.
See `MIGRATION_0_6.md`. No physical audio tests are performed by the agent.

Version 0.3.2 added pinned Save/status controls,
encrypted unfinished drafts and revision protection against stale forms overwriting
newer saved settings. A protected copy of the previous preferences was made before
installation. See `SETTINGS_PERSISTENCE.md`; user persistence checks are pending.
Version 0.3.1 fixed garbled punctuation by
explicitly compiling Java sources as UTF-8. The actual APK DEX was checked for correct
Unicode labels and absence of Windows-decoded mojibake. The user performs the visual
check. Version 0.3.0 added Home support on top of the inactive G10 worker and persistent
panel identity. This is not yet the complete hardware replacement.
The app is installed on 172.16.1.26; the existing native bridge still owns hardware.
The legacy browser startup/compatibility hooks were subsequently retired with backups
because they opened Lightning after reboot. Android Home now owns dashboard startup;
user boot verification is pending. See `startup/README.md`. MQTT, input/relay, audio
and backlight services were not restarted.
See `installation-result.json`. Further activation/testing remains separate.
No live panel changes, hardware tests, package disabling, or HA configuration changes
were performed during the 0.2.0/0.3.0 work. The existing setup remains active.

## Implemented in this build

- Separate package `local.wallpanel.app`, Android 6 / API 23 or newer; compiled against
  Android 8.1 / API 27. No dependency on a Tuya APK, no web server, no bundled
  third-party Android libraries or receiver/intercom services.
- Random per-install `wp_…` identity, independent of IP/MAC, saved once and retained
  across updates. Native settings rendering excludes broker/HA credentials.
- ARM32/ARM64 G10 workers and guarded A133 candidate probes included as APK assets (not extracted or started).
  Buttons, relays and offline local bindings are implemented and tested with fake
  UART; other hardware modules have not migrated. See `native/PROTOCOL.md`.
- Android Home launcher registration and **Set as Home app** entry to Android's
  selector. The Home root itself owns the WebView; returning Home keeps the existing
  page when its URL/module selection is unchanged. Invalid/missing dashboard settings
  open setup with a recovery surface. Ordinary dashboard routing does not decrypt
  MQTT credentials. Home selection does not grant device-owner kiosk privileges.
  See `LAUNCHER.md`; no default launcher has been changed.
- Local dashboard, HA URL, MQTT host/port/user/password/TLS settings. MQTT password
  is encrypted with an app-specific Android Keystore AES-GCM key. Android backup is
  disabled. No embedded panel IP, broker credentials or fixed MQTT entity identity.
- Explicit MQTT 3.1.1 **login test** with a unique temporary client ID, clean session,
  optional TLS with hostname verification, and disconnect afterward. No subscriptions,
  discovery, state publications or hardware commands. This is not the runtime HA
  transport yet. HA URL is saved; authenticated HA API access is not implemented.
- Dashboard WebView: HTTP/HTTPS URL validation, JavaScript and local browser storage,
  fullscreen appearance, network wait and failed-load retry. Successful pages are not
  periodically refreshed or reloaded on wake. File/content URLs, JavaScript-to-native
  bridges, mixed-content exceptions and certificate-error bypasses are not enabled.
- Dashboard module selection and persisted hardware-module choices. Generic/unverified
  hardware and G10 profiles are distinct. Selecting a G10 profile is not hardware
  detection. Hardware modules clearly say **not migrated**, and remain unavailable.
- Optional app-level Back navigation lock and administrator PIN (6–12 digits), salted
  PBKDF2 hash, and persistent 30-second delay after five wrong PIN attempts. Hold the
  dashboard's top-left 28 dp corner to open settings; a locked app requires the PIN.
  This is not Android device-owner lockdown: system Home/Recents/settings access,
  secret-pattern customization and HA unlock are still pending.

## Not migrated yet

The existing native bridge still owns buttons, relays, knob, backlight, screenshots,
reboot and audio. The foundation app does not access those interfaces, automatically
disable vendor packages, silently change the default Home app or create HA entities.
Android can launch this app at normal Home startup once the user selects it as Home;
there is no separate boot receiver. The old browser guardians have been gated off
through a backed-up handover; physical boot checks are pending. Existing Lightning-specific dashboard compatibility changes are not copied
into this WebView. Sleep/wake, rendering, PIN/Keystore behavior and app RAM use have
not been tested on the panel. The APK does not include or update WebView itself.

Optional SIP/video intercom, AirPlay, Cast, Bluetooth receiving, external-app shortcuts
and independent button LED control are separate future modules. Both documented
global lighting command tests produced no visible effect on the G10; do not expose
them as working indicator controls.

## Local build and checks

From the workspace run `py unified-app/build.py` and `py unified-app/test.py`.
These reuse the existing local Eclipse compiler, Android API 27, dx and Apktool tools.
No downloads, installation commands, ADB commands or panel tests are in these scripts.

The build first rebuilds the native worker, then creates `tuya-wall-panel-0.7.0.apk`
and `build-result.json` (SHA-256 and size).
It signs with a new, separate development identity stored in `app-signing-key.pem` and
`app-signing-cert.pem`. Keep that private key private and backed up for future app
updates; it is Git-ignored and is never packaged in the APK. This is an Android 8
compatible v1-signed development artifact, not a Play Store release.

66 isolated Java checks passed: URL/port/credential validation, profile/module availability,
MQTT frame construction, successful/rejected/malformed fake-broker replies, and
disconnect without publications, plus hardware settings export without secrets.
Twenty-five additional settings-store checks cover old setup preservation, encrypted
draft restart recovery, explicit apply, stale-form guards and failure handling.
Home routing and launcher manifest checks also passed. Native fake-UART tests passed for input/relay behavior, disabled modules, configuration
guards, feedback, offline local actions and reconnect. Java-to-C configuration and APK
worker hash checks passed on the previous worker build; its code is unchanged in 0.3.0.
Android 0.3.0 UI, Home lifecycle and hardware checks remain for the user
after a later installation/activation step.

See `ARCHITECTURE.md` for the migration sequence.
