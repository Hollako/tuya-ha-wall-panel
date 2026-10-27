# Architecture

Canonical runtime details are in ../STATUS.md.

- Android app UID 10063 on this panel; discover UID on every provisioning, never reuse the Wall Panel app UID.
- App -> /data/local/wpar-airplay/ptp.sock -> root-owned wpar-ptp-helper. Filesystem socket is mode 0600 owned by the allowed UID; parent directory root-owned 0711; SO_PEERCRED independently checks peer UID.
- Fixed operations: STATUS and PTP. PTP is a single active lease held open by the app. Helper launches only root-owned /data/local/wpar-airplay/nqptp with fixed arguments/environment. No shell/path/config supplied by the client.
- nqptp binds PTP ports before dropping UID, GID and supplementary groups to the app/inet identity. No effective capabilities remain. Parent-death signal is reapplied after setuid; closing the socket lease stops the daemon.
- Native shm_open/shm_unlink wrappers use private app-owned mmap files. Both binaries link the same adapter. No global shared-memory mount is required.
- Shairport launches directly from the APK native library directory as the Android app UID. It decodes into framed 48 kHz 16-bit stereo PCM on an app-private abstract Unix socket; each peer verifies the same app UID. Java AudioTrack uses the existing STREAM_MUSIC path; no mixer, routing or hardware worker changes.
- Metadata FIFO parser handles hex codes, base64 text and binary artwork. HTTP status and controls stay available while receiver is disabled.
- tinysvcmdns publishes primary and secondary upstream TXT arrays, RAOP and AirPlay 2 services. Native TXT strings have their own 255-byte allocator; DNS names retain their 63-byte limit.

The 1.1.0 Android backend replaces the raw audio pipe. It returns downstream queue delay, presentation statistics and generation-scoped flush feedback to the existing Shairport controller. Java models AudioTimestamp using CLOCK_MONOTONIC; native statistics separately sample MONOTONIC_RAW and MONOTONIC, without subtracting timestamps from unlike clocks. No second Java rate controller or panel-elected reference clock is introduced. Timestamp fallback is explicitly degraded; acoustic synchronization is unverified.

AirPlay helper boot entry is a new independent file. Existing wallpanel_privilege init configuration is unchanged. No reboot test has been performed.

## Independent receiver modules (1.0.4)

AudioSourceModule defines observation and snapshots; AirPlaySourceModule wraps existing state and BluetoothSinkModule observes the firmware sink. ReceiverService owns their observation separately from AirPlay process lifecycle. This is a boundary for a future source coordinator, not an implementation of selection, volume arbitration or SIP priority. Bluetooth decoding/output remain in firmware. See BLUETOOTH.md for hardware results and the required future control contract. No existing Wall Panel audio path was changed.
