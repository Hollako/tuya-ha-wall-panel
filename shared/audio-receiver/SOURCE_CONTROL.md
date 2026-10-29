# AirPlay source control

Unified 0.25.1 and dedicated receiver 1.3.2 share the same source-control implementation.

For an active AirPlay 2 connection, play, pause, play/pause, next and previous send the upstream
`modernMediaRemoteCommand` over the sender's encrypted event channel. These
commands affect source playback and may affect its whole multiroom group. Classic
AirPlay control is unavailable; an unavailable or rejected command never becomes
a local mute. Commands are not retried or replayed after reconnecting.

The unified app accepts the existing HA media commands and local media knob hold
gesture. Its playback feedback follows the source's reported rate, not an
optimistic command result. HA integration 1.19.1 exposes next/previous buttons. Mute,
source selection, local stop and SIP interruptions remain local output controls.

The dedicated receiver provides Play / Pause / Play-pause / Next / Previous controls in its Android
UI and existing optional timing page. Unified 0.24.4 exposes that same optional
page through Audio receiver settings, including web enable, port and token controls.
Changing its enable or port restarts only the receiver service. Its HTTP API accepts
`POST /api/v1/transport` with `{"command":"play"}`, `pause`, `play_pause`, `next` or `previous`.
Status includes `receiver.remote` with availability, source state and command
result. An accepted request means queued; an acknowledgement is distinct from
reported playback state.

Native control uses a per-process abstract Unix socket, checks the app UID,
permits only fixed operations and binds commands to the current connection ID.
The native controller and Java client have bounded requests, no root privileges
and no audio clock responsibilities. The PTP helper and nqptp are unchanged.

Unified 0.25.3 / receiver 1.3.3 keep discovery TXT updates on the existing
responder thread and socket: no goodbye/rebind cycle on session state changes.
QU questions receive unicast answers, and advertisements refresh every minute.
The bounded session timeout is 30 minutes instead of upstream's one minute, to
allow long pauses without aggressive TCP keepalive expiry. Actual transport
errors or a sender closing its connection still end that session.

Host checks: `airplay-app/tests/test_control.py` (Linux/WSL),
`unified-app/test_receiver_remote.py`, existing PCM, output-gate, audio-clock and
SIP audio-lease checks. Physical source / multiroom behavior remains a user test.

Installed on 2026-10-27: unified 0.24.0 on G10 172.16.1.26 and dedicated 1.3.0
on A133 172.16.1.33. Both installed APK hashes, native executable hashes,
control listeners, app UIDs, zero native app capabilities and preserved settings
were verified. Existing privileged helpers are unchanged; G10 SIP is registered
and idle. No playback commands, calls or reboots were used for verification.
These feature builds have not been published to GitHub.
