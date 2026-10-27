# Testing and evidence

See ../STATUS.md for the authoritative verified/pending list.

| Check | Result |
|---|---|
| ARM64 static build / APK build | Pass |
| Peer UID authentication, unsupported operation rejection | Pass |
| Privileged port binding then UID/capability drop | Pass |
| /proc/PID/exe path and hash match | Pass |
| Initial iPad audible playback | User confirmed; logs show Classic AirPlay |
| Separate AP2 discovery test with real TXT/public-key records | Pass |
| HTTP volume / restore, disable / enable, repeated enable | Pass |
| Existing Wall Panel service hashes and PIDs unchanged | Pass |
| Final-build AP2 audio playback | Pass: user confirmation and native AirPlay 2 Buffered playback line |
| Real AP2 metadata / artwork | Pass: title, artist and artwork in API |
| Actual reboot/autostart | Pending; no reboot performed |
| Multiroom timing / endurance / network recovery | Pending |

Verification fixtures and captures are in ../verification/. The initial failed lifecycle test was followed by an explicit LocalSocket shutdown fix and passing repeated lifecycle tests. Do not claim the initial failure remains unresolved.

For replay, inspect files/app.log for the playback line. Classic AirPlay and AirPlay 2 are explicitly distinguished by the native player. receiver.protocol in the current API describes compiled capability, not proof of a negotiated AP2 session.

Do not inject test music, change mixer controls, stop the Wall Panel worker, or reboot while the user is playing.

## Second-panel results

Version1.0.2 ARM32 on 172.16.1.33 passed installation, privilege separation, native hash verification and real reboot/autostart. Both panels simultaneously report the same track, active AirPlay2 playback and artwork without receiver errors. User confirmed working on both. Evidence: ../verification/panel33/final-result.json, both-receivers.json and post-reboot.txt. Inter-speaker timing has not been measured; do not infer a numeric synchronization tolerance from shared metadata or simultaneous streaming.

## Local 1.1.0 synchronization checks (not device validation)

Run bash tests/audio-sync/run.sh under WSL with a JDK and gcc after native source preparation. Pure-Java tests cover frame wrap/reset, stale timestamps, rate outliers and submitted-frame clamping. The Linux protocol test compiles the real C backend with -Wall -Wextra -Werror and a deterministic socket peer; it checks full-pipeline delay, live offset acknowledgement, bounded splitting of large PCM blocks, generation flush, stale/invalid feedback and head-fallback statistics. Results and final build logs: verification/audio-sync/.

Pending on both actual receivers: install the new APK preserving preferences; verify running native executable/version; inspect timestamp warmup and fallback; change slider/API offset during playback and verify unchanged app/native PID and restart count; test pause/resume/seek/reselect/route changes and API authentication/errors; listen and record at comparable distances for 30-60 minutes. Large timing jumps may trigger native resynchronization without an app restart. Do not claim measured alignment or resolved drift until this passes. G10 remains powered off; no 1.1.0 installation or hardware tests were performed.
