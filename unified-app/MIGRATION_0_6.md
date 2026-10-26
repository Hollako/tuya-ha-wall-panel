# Audio migration — 0.6.0

The user accepted the knob migration. Dashboard URL delivery still fails in the
user's environment and is explicitly deferred until audio acceptance. Do not claim
the URL issue is solved or run physical tests without the user's instruction.

## New ownership

The main app now hosts AudioEngine (MediaPlayer, MediaSession, Android volume,
media-key forwarding, board AUX/output selection) inside its foreground BridgeService.
It reuses the proven VolumePolicy including zero-volume logical-mute handling and
slider-to-audible unmute, with AudioManager notification flags zero. There is no
second Android audio service, broker login, local socket or vendor application
dependency in the active app audio path. VendorAudio accesses the same verified
world-readable/writable kernel AUX/horn nodes; its optional legacy notification bus
is not required and no extra vendor DEX is bundled.

The private native worker owns amplifier trim, bounded fixed mixer power operations
and saving the speaker preference. It exposes only exact canonical audio commands,
does not record audio and does not start the obsolete software PCM loopback. Hardware
AUX remains the board route. The Audio and Amplifier modules are independently
selectable, including amplifier-only trim control. No new RGB/LED capabilities or
audio receiver/SIP functions are included in this stage.

The native request/resource lock pair makes the legacy bridge close its audio IPC,
stop the known standalone `local.wallpanel.audio` package and reap earlier root audio
jobs before granting resources. A failed retirement retains the resource lock;
failed activation returns to legacy control. The old package stays installed and
enabled as fallback and is explicitly started by the legacy bridge only after the
app releases ownership. Existing playback in the old APK stops at user-initiated
takeover; media URLs are not restored or auto-played. AUX/output/Android volume are
observed in place; engine startup does not change their values.

The worker automatically releases locks on stdin EOF/process death. Its fixed
settings/tinymix jobs have bounded lifetimes and are terminated/reaped before releasing
resources. Native command identities reject duplicates/old requests. Disabled modules
cancel pending jobs. Android commands reject retained/unknown/internal messages,
use a bounded main-thread queue and discard stale operations. State publication runs
off the UI thread and latest state is republished on MQTT reconnection.

## HA compatibility

The existing media player, external-speaker switch and hardware output level keep
their IDs and protocol. HA 1.9 adds independent amplifier availability and an audio
owner marker so an app outage does not incorrectly disable legacy-owned audio.
No integration entry is recreated. Deliver files to the user; do not deploy to HA.

The local source component folder had been moved while copying to HA. It was restored
from the existing extracted 1.8 package before audio-only changes. Package creation
now also refreshes the extracted component folder so it cannot silently stay older
than the ZIP. URL transport code has not been changed in this stage.

## Deployment and checks

App 0.6.0 and a separately backed-up audio ownership gate are installed; the app's new
runtime is left stopped for user activation. Original preferences are backed up at
`/data/local/tmp/wallpanel-app-before-0.6.0/wall_panel.xml`. Gate backup:
`/data/local/panalume/before-app-audio-gate.20261024-225031`.
Use **Start / apply hardware** to start the app audio runtime and **Use previous
hardware service** to return all migrated modules to the validated bridge.

User next copies HA 1.9, restarts HA, starts hardware and tests existing playback,
40-to-80 volume, zero then raise, explicit mute/unmute, source selection, external
speaker selection and amplifier output level. User also checks buttons/knob remain
normal. AUX listening requires a connected input. No playback, source/output changes,
volume changes, relay switching or reboot tests are performed by the agent.

Local checks: ten simulated native suites, six audio-gate watchdog/rollback tests,
80 Java core checks, 33 store checks, 36 MQTT/router checks, activation and knob/URL
policy checks, production volume and audio allowlist tests, 74 isolated HA tests,
manifest/DEX encoding/hash/configuration/signature checks. The installer verifies
normal startup relay-state queries and unchanged backlight PID; this is not a
physical relay or audio test.
