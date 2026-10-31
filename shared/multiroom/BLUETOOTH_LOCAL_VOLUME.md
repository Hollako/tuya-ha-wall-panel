# Standalone Bluetooth low-volume correction

The persistent Bluetooth route previously rendered standalone music with an
AudioTrack at full per-track gain. Android's coarse STREAM_MUSIC index changed,
but the fractional RoomVolume gain used by local/AirPlay/group output was missing.
Thus multiple 1% slider values could produce the same audible hardware step.

The owning APK now includes the existing room gain in its credential-checked
private status socket. A separate helper thread reads it; the PCM delivery thread
never performs socket requests. BluetoothLocalOutput applies that gain to its
local AudioTrack, including mute, before playback. It remains silent until a valid
initial value is available, and ignores invalid/unavailable later values.

The captured PCM sent to group members is unchanged. Each group room already
applies its own RoomVolume gain, so applying this adjustment to capture would
incorrectly attenuate every receiver twice. Maximum volume and the room slider
mapping are unchanged. This corrects standalone Bluetooth; reports of excessive
grouped loudness still need listening feedback after comparing these paths.

SmartWay Panel 0.34.4 and its rebuilt Bluetooth bridge are required together.
The fix is in shared source for the dedicated Audio Streamer's next build as well.
Only G10 is targeted for this deployment. No HA integration update is needed.

Host checks: 32 actual local-output checks, 65 persistent route checks,
13 private profile checks, and 438 shared room-volume checks.
