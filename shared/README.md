# Shared product engines

`audio-receiver/src` is the single Java receiver engine consumed directly by the
dedicated Audio Receiver Gradle build and the Tuya HA Wall Panel build. Keep its
`com.wallpanel.audioreceiver` source namespace; it does not change either Android
application ID, signing identity or private settings directory.

`sip/src` is the single SIP protocol/codec core. The unified build and its tests
consume it directly. A dedicated SIP product can consume the same source set;
that product's launcher/settings packaging is not built in this change. Android
audio, video, door APIs and UI adapters remain canonical in `unified-app/src`.

The dedicated receiver retains its own launcher, boot adapter and optional web
control. The unified product owns its own boot, PIN/setup, HA connection and media
routes. Its receiver web server is disabled. Neither product copies the other's
HA/SIP credentials. Run one receiver owner per physical panel.

The unchanged native AirPlay backend, shared-memory shim and fixed PTP helper are
canonical in `airplay-app/native`. Both APKs consume the same verified native
outputs. `audio-receiver/native-payloads.json` pins the current ARM32/ARM64 baseline;
the unified build refuses missing, changed or wrong-architecture artifacts.
Rebuild those with `airplay-app/scripts/build-isolated-native.py` after a native
engine change, then explicitly review/update the manifest and rebuild both APKs.
Do not silently repin changed binaries during an ordinary app build.

Local source backups were made before extraction. Native work trees/toolchains
and original app signing identities remain in their existing private locations.
They are not committed by this change. Preserve them: a fresh clone still needs
the documented native toolchain/dependency preparation and actual pinned outputs.
Complete third-party source/notices/distribution review before publishing builds.

## Output and call policy

`AudioOutputGate` applies output selection and nested call leases to the actual
AudioTrack. A lease silences the current track before SIP routing/ringing starts;
a track created during that call also starts silent. PCM consumption and measured
presentation feedback continue, preserving the existing synchronization engine.
Releasing the last call lease restores the previous selected/paused policy.
Old session callbacks cannot select a newer stream. Product adapters decide AUX,
amplifier and local-player routing; the receiver never issues board commands.

AirPlay play/pause in the unified media entity controls local output, not the
sender's transport. Remote DACP controls remain unsupported. Bluetooth stays
firmware-owned; its SIP arbitration and G10 glitch investigation are not claimed
as solved by this merge.
