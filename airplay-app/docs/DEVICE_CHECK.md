# Device check

Device: 172.16.1.26:5555, Android 8.1, ARM64. Trusted ADB shell UID 0. AirPlay package UID 10063; unrelated Wall Panel app UID 10065. SELinux was already Permissive and was not changed.

Read ../verification/executable-identity.txt for actual executable paths, hashes and credentials. Read ../verification/final-start.txt for listeners and process state.

Required listeners: nqptp UDP 319/320 and localhost:9000; Shairport TCP 7000; app HTTP 8099. Root is used only during PTP socket binding; runtime nqptp and Shairport must be unprivileged.

Never use app-side su checks to diagnose this panel. Verify the separate helper socket using the allowed AirPlay UID and verify SO_PEERCRED rejection of other UIDs.

Existing Wall Panel service baseline is ../verification/wallpanel-before.txt. Do not remount or change hardware configuration to diagnose AirPlay.
