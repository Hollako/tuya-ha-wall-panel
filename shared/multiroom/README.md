# Panel-hosted audio groups: first playback milestone

One opted-in leader runs Snapserver plus a local Snapclient; receiver panels run
Snapclient against the leader's IP. No HA or external server is required. The
leader's own output takes the same synchronized receiver path. No native grouping
process, recording, or network listener is started by installation or boot.

This milestone accepts direct HTTP/HTTPS audio URLs and Android document-provider
audio files (44.1/48 kHz, 16-bit decoded PCM, mono/stereo). It does not yet capture
Spotify, YouTube, Bluetooth or Line Input. A declared remote-submix route is not
proof those sources can be captured. There is no automatic leader election or
discovery/room checklist yet: joiners specify the leader IP and leave by stopping
their local group. A leader stopping disconnects its receivers.

The group uses Snapcast's independent clock/buffer algorithms and Oboe output.
Presentation timestamps replace the original fixed output delay when available;
otherwise queued frames plus the Android-reported HAL latency estimate are used.
The default group buffer is two seconds and the leader can choose 1000–5000 ms.
Per-room timing is -500 to +500 ms: positive delays the room, negative advances it.
Room timing has a signed slider with fine-step and reset buttons. Changes save on
release and apply through the native control loop without reopening audio output;
allow a few seconds for synchronization to settle. Changing the
leader's buffer requires stopping/starting its group. Do not use a fixed room
offset to mask a gap that keeps changing or a connection that is starving.
The existing receiver web page also has a separate Group timing slider. Its
authenticated POST /api/v1/group-timing accepts an integer roomDelayMs from -500
to +500. It writes the same private live-control file as the app; no playback or
service restart is triggered. GET /api/v1/status includes the group role, active
state, status text and saved roomDelayMs. AirPlay's separate timing API is intact.

Room volume now has a separate local PCM attenuator (0–100%, 1% resolution)
in addition to Android's coarse media steps. It only reduces amplitude, with a
20 ms gain ramp; sample counts, clocks and synchronization are unchanged. HA and
the local media knob use the finer logical volume while grouping is active. The
app and existing web page expose room volume too (POST /api/v1/group-volume with
normalized volume). A nonzero adjustment automatically clears zero-volume mute.
Starting group playback restores the room's saved group volume and mute setting.
The unified app's own URL player also uses per-player attenuation; external apps
and Line Input retain their existing Android volume behavior. AirPlay is unchanged.
An isolated two-second time-sync timeout retains the existing clock estimate and
retries after one second; three consecutive timeouts trigger reconnect. Actual
socket errors still reconnect immediately. Connection generations suppress stale
callbacks and duplicate reconnects. This does not repair sustained network loss.
Music synchronization needs physical verification;
video lip-sync is not provided. PCM uses about 1.54 Mbit/s per stereo 48 kHz client
before TCP overhead. Group traffic is for a trusted LAN (TCP 1704); control and
raw source sockets bind to loopback only. Source URLs/URIs stay in private app
preferences. Group volume and mute use the panel's normal media controls.

In the unified product, selecting a group switches to Local Playback and holds
the existing AirPlay local-output gate without stopping/altering its decoder or
clock. Knob/HA play/pause controls a leader's source or the receiver's local room.
Changing source or stopping group playback closes grouping. Incoming SIP calls
stop only this panel's group receiver and rejoin it afterward; the leader keeps
feeding other rooms. The dedicated product has no SIP module.

Before production: test pause/resume, source end/replay, leader loss, SIP priority,
room levels and acoustic synchronization on both architectures; add authenticated
membership and automatic discovery, network assignment, compression, proper queue
controls and the separately verified third-party-app capture adapters.
