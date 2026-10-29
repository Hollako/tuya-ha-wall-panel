# Bluetooth group input

The phone pairs with the source panel's existing Android A2DP receiver. In the
group web page, select that source panel and the destination rooms, then choose
**Bluetooth input → Play Bluetooth on selected rooms**. In HA, select Bluetooth
on that panel's Room audio player. Selecting an input starts the selected group.
The source room also uses the synchronized return, rather than an early direct
copy. Existing per-room timing, volume and mute controls remain available.
The group buffer adds latency; this mode is for music, not video lip-sync.

Stopping the source group releases its routing policy and returns Bluetooth to
normal local playback. Group play/pause tries the Bluetooth phone's AVRCP media
session; phone support varies. Paused group audio is silenced regardless.

The trusted-root provisioner installs a separate fixed-purpose system-UID worker
on these two lab panels. It authenticates the app's Linux UID on its local socket.
Its dynamic mix requires BOTH Bluetooth UID 1002 and media usage. It captures no
microphone, calls, SIP or unrelated apps. PCM stays in memory and TCP; no recording
files are created. Closing the capture lease unregisters the mix. A new private
init entry starts only the idle worker after boot; capture is opt-in.
The helper drops root and supplementary root groups before starting ART, has no
effective Linux capabilities, and accepts no paths or shell commands from apps.

Build the helper with `unified-app/build_bluetooth_bridge.py`, then build both
products normally. Its JAR and ARM32/ARM64 bootstraps are separate assets; all
AirPlay and group decoding/synchronization binaries remain unchanged.
`unified-app/install_bluetooth_groups_local.py` performs a backed-up installation
and provisions the helper only on the two known lab devices. New models need a
verified A2DP sink, Bluetooth UID/audio-track check and trusted-root provisioning;
installing an APK alone does not grant Android's privileged routing permission.

API/policy construction, peer authentication, packaging, web/HA commands and idle
readiness are checked without registering a mix or playing test audio. The first
real Bluetooth capture, independent room levels, source stop/route restoration,
pause/resume and reboot persistence require the user's physical checks.

On the tested A133 Android 11 firmware, installing or removing the exclusive
route can invalidate Bluetooth's existing AAudio stream. Start the Bluetooth
group first, then reconnect the phone and press Play on the phone. Reapplying
the same Bluetooth input updates membership without replacing the capture mix.
An automatic firmware-stream handoff is not yet implemented.
