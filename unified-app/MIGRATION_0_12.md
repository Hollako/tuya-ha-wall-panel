# SIP audio intercom migration 0.12.0

Panel Intercom's audio baseline is integrated into Tuya HA Wall Panel as an optional
module. Setup > Intercom settings configures server/server port, extension/auth user,
password, local IPv4 override or automatic address selection, local SIP/RTP ports,
auto-answer, ring timeout, call volume, echo cancellation, suppression and gain.
The same SIP selection appears in main setup. Saving SIP module selection preserves
the protected main setup draft. Credentials are encrypted with the app's existing
Keystore identity. No fixed account, password or device IP is embedded in the APK.

SIP runs independently of MQTT and hardware profile. Its foreground service starts
after boot/update when enabled/configured and recovers registration/network failures.
Incoming calls open a private call activity above the existing Home task; answering,
declining and hanging up keep the dashboard WebView underneath. Manual answer is the
fresh-install default. Auto-answer is optional/live and pending auto-answer is cancelled
when the setting changes or the caller cancels. Ringtone belongs to the service.
The existing root System UI restrictions transfer across the call screen.

Calls temporarily acquire audio focus, pause the app's playback, select Android audio
from AUX if needed, request amplifier/main display availability and use communication
audio. Call end restores prior routing/focus/voice volume and paused app playback.
The physical display is kept awake during calls through its existing native owner;
normal idle timers resume afterward. These physical behaviors require USER testing.

## Protocol and media corrections

The standalone 1.0.4 codec decoded the PCMU wire byte without complementing it; silence
could become a large sample. The port uses standard 16-bit G.711 µ-law conversion,
tested with known silence/extreme/sample vectors and every codeword round trip.
This is a concrete software defect; actual noise improvement remains a user audio test.

RTP validates version, negotiated PCMU payload type, CSRC/header extensions/padding,
source endpoint and packet order. Receive length resets for each datagram. Partial
microphone reads/audio writes are handled; sockets/capture/effects/threads close on
failure, cancellation and module shutdown. No microphone opens before answer unless
the user enables auto-answer. Media-start/shutdown races cannot restart a closed session.

SIP state is serialized, 180/200 use the same tag, CANCEL gets its own response and
terminates only ringing INVITEs, busy/decline work, remote/local BYE clean up, and
reINVITE updates direct-media endpoints. Digest supports 401/407, nonce/qop handling,
MD5/MD5-sess/SHA-256; credentials/digest material are never logged. Registration refresh
and reconnect do not rely on dashboard activity. Unsupported video tracks are answered
with port zero; this milestone is audio only, without outgoing dialing/door unlock/HA
SIP entities/video. Those are later development work.

The pinned Android JAIN-SIP RI has an event-scanner shutdown issue: forceStop sets its
flag but leaves the queue reader waiting. The app captures only that stack's scanner,
stops the stack, interrupts/joins it and resets the factory. A lifecycle owner lock
prevents overlapping old/new stacks and parser executors on quick restarts.

## Deployment and fallback

Installed 0.12.0 on 172.16.1.26. The standalone package com.panel.intercom was disabled,
not uninstalled. Its original APK/preferences are backed up; WSL source was not edited.
The old preference XML had unquoted attributes; migration recovers that syntax only
in memory and leaves the original untouched. Saved manual-answer selection was copied.
Transfer JSON was protected by app ownership/mode600, consumed, encrypted and deleted.
The integrated service received RECORD_AUDIO permission but no test call/microphone
capture/tone was performed by the agent. Registration succeeded with Asterisk, IDLE.
Hardware/native ownership, six HA availability statuses and dashboard restrictions
were verified online. No HA integration files changed.

Device backup: /data/local/wallpanel/before-sip-migration.20261025T163052Z
Local original APK: sip-migration-backups/20261025T163052Z/PanelIntercom-original.apk
Rollback: python migrate_sip.py --device 172.16.1.26:5555 --rollback
Rollback disables unified SIP through the app before restoring the old package/service.
Do not run both owners on the same SIP/RTP ports. The live rollback path was not
exercised, to avoid interrupting the newly registered service.

## Verification

327 pure/protocol checks with the real JAIN-SIP stack and loopback fake PBX cover
codec/RTP/SDP/digest, registration, tags, busy, renegotiation, local/remote hangup,
cancellation, auto-answer/live-off, restarts and zero leftover event threads. Real RTP
code runs against fake Android audio for partial I/O, short/full packets, wrong-peer/
payload rejection, cancelled-session refusal and cleanup. XML recovery tests preserve
entities/typed values and refuse unrelated input. Existing configuration/store/MQTT/
audio/display/screenshot tests pass. Real hardware call/audio/wake/reboot tests remain
with the user; no claim that those have passed yet.

Build now uses existing WSL Android SDK D8 (34.0.0, Java17) for the app, since the old
dx silently skipped Java7 library classes. Required SIP classes are checked in the
resulting DEX. Root System UI helper remains separately compiled. Four dependency
JARs are pinned by SHA256 in vendor/sip/manifest.json with original Maven metadata
included in APK assets/licenses/sip. See sip-migration-result.json and app verification.
