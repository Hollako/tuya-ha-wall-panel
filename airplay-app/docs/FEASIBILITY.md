# Feasibility update

Static musl executables avoid Android bionic pthread cancellation limitations. The custom ARM64 toolchain and existing staging successfully build this receiver; there is no remaining toolchain architecture blocker.

A rooted ADB shell does not grant root to Android apps. The panel restricts app-side su. A separate UID-authenticated PTP lease helper resolves the specific privileged UDP port requirement without weakening su or reusing the Wall Panel hardware helper.

Classic AirPlay playback is now confirmed by the user and native logs. AirPlay 2 discovery has been verified in an isolated instance after correcting the mDNS registration and TXT-length bugs. Final-build AirPlay 2 Buffered playback from Elie’s iPad is now confirmed in native logs and by the user. Live track title/artist/artwork are received. Measured multiroom synchronization remains pending.

The FIFO-to-AudioTrack bridge is suitable for single-panel playback, which has been demonstrated. It does not yet feed Android hardware-clock timing back to Shairport; no precise multiroom accuracy is promised.

Read ../STATUS.md for installed version, evidence and remaining tests. Previous blanket claims of guaranteed feasibility, certification, DRM compatibility, exact latency, RAM limits and deterministic builds should not be treated as verified results.
