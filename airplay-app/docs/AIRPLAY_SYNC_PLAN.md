# AirPlay output synchronization plan

## Diagnosis from installed 1.0.5

Current installed1.0.5 launches Shairport5.6.1 with output_backend=pipe,48000Hz S16LE stereo. Its audio_pipe.c play() ignores timestamp/playtime; delay,stats,flush callbacks are NULL. PcmPump starts an AudioTrack and forwards FIFO bytes with blocking writes, without AudioTimestamp or playback-head feedback. The manual offset changes an RTP anchor but does not measure or discipline Android's output clock. Both existing backend and documentation describe this as partial synchronization. This is an architectural limitation, not proof every reported gap has this cause; packet loss, clock health, route changes and underruns must also be measured.

## Recommended timing ownership

Retain AirPlay2's protocol-selected PTP reference and RTP presentation timeline through nqptp. Do not create another elected speaker clock for normal AirPlay playback. Each receiver must make its own Android audio presentation follow that common timeline. A panel coordinator cannot compensate a missing output-clock observation just by comparing wall-clock time, packet arrivals or ready-frame counts.

Keep the latency slider as residual per-device calibration for fixed output delays not represented accurately by firmware timestamps. Do not automatically reuse the previous95ms queue difference. Observe current user values rather than resetting them. A slowest-device policy must be about verified output latency/headroom, not the latest late Wi-Fi packet. Any group-added delay must respect source deadlines and interoperability with other AirPlay receivers; changing only our receivers' group delay can make them late relative to external speakers.

## Implementation sequence

1. Instrument AudioTrack without changing playback behavior. Track frames accepted, extended presentation-frame position, monotonic presentation timestamp, timestamp validity/age, effective presentation rate, underrun count, route, queue estimate and generation. Validate timestamps advance consistently on Android8 G10 and Android11 A133. Handle32bit wrap, stop/flush resets and warmup. Android recommends periodically acquiring stable timestamps and maintaining a frame/time mapping, rather than interpreting very frequent timestamp differences as rate changes. Extrapolate between trustworthy samples; discard outliers. A playback-head fallback is degraded evidence, not a claim of accurate speaker timing.
2. Add a dedicated Android output backend to the native receiver, with app-private Unix socket/shared-memory transport. No new root operations or inter-device clock port required. Carry fixed-format PCM frame blocks plus presentation metadata and a session/generation identifier; return bounded timing feedback and counters from the Java output engine. Both native executable and Java engine stay at the ordinary app UID.
3. Implement the native backend delay/stats/flush lifecycle contract so Shairport's existing timing controller and interpolation can use real downstream measurements. Account for every stage: transport backlog, Java pending frames and Android/HAL presentation estimate, including atomic counter updates around partial writes. Raw PCM FIFO alone is insufficient. Native common.c uses CLOCK_MONOTONIC_RAW, whereas AudioTimestamp uses CLOCK_MONOTONIC/System.nanoTime: measure and maintain the clock relationship explicitly; never subtract these timestamps directly. Counts remain in negotiated output frames, not blindly in HAL sample-rate units.
4. Use the existing Shairport rate/phase controller as sole owner of continuous correction. Avoid an independent Java speed controller fighting native resampling. Prefer small bounded, filtered interpolation corrections; avoid repeated restarts and arbitrary sample dropping. Flush/rebuffer explicitly on discontinuity or excessive error, with generation/ack handling so old PCM and timing feedback cannot contaminate a new session. Treat stale/invalid feedback as a real error/degraded state, not zero delay or a fabricated lock. Determine stable control limits from measured hardware, not a promised millisecond number.
5. Add local timing observability and, optionally, group status over the existing8099 API. Report reference clock identifier, stream/session identity, timing validity, estimated error, rate correction and underruns. Group telemetry is for diagnosis and policy, not a replacement for AirPlay clocks. Preserve existing bearer-token rules; do not put privilege commands on a network endpoint.

## Validation and practical limits

Test two independently running receivers with the same iPad stream, startup, pause/resume/seek/reselect, network jitter/recovery, screen off and clock-reference changes. Run at least30-60minutes to assess drift. Verify fixed offsets and drift with simultaneous acoustic recordings at comparable microphone distances; account for sound propagation and acoustic echoes. Equal queue depths or synchronized log clocks do not prove speaker alignment. Test against an external AirPlay receiver if interoperability matters. Do not promise submillisecond,1ms or10ms output alignment until measured.

If firmware presentation timestamps are unreliable, accurate automatic correction may require a different Android audio backend or device-specific calibration. Bypassing Android through direct ALSA would conflict with the requirement to preserve the existing Wall Panel audio system and is not the default solution.

## Local implementation status: 1.1.0, not deployed

Implemented the Android output backend in native/wpar-android-output.c, built in the existing pipe compilation slot but registered as android. PCM headers include generation, presentation target and RTP diagnostics. Feedback carries accepted/presented frames, monotonic timestamp/rate, underruns and the saved live offset. Native delay accounts for socket backlog and Java pending frames as well as the AudioTrack queue. Feedback older than 250 ms or from an old generation is rejected. Flush resets the AudioTrack and counters under a new generation. Large native blocks are divided into bounded transport messages. A broken transport exits the native receiver with an actionable error instead of silently reporting zero delay.

PresentationClock handles 32-bit wrap, timestamp warmup, stale samples, implausible rates and counter resets. Timestamp models are refreshed every 10 seconds once stable; feedback is emitted every 20 ms. Playback-head fallback is labelled as an estimate and is excluded from valid hardware-clock statistics. Invalid counters require a fresh AudioTrack generation. Underruns/route changes invalidate the timestamp model. These limits are initial engineering choices requiring device validation.

The slider, +/- buttons and Reset save/apply while playing; they never call service restart. The native RTP anchor conversion reads the feedback offset so Shairport remains sole owner of phase/rate correction. A large offset change can cause a native resynchronization/buffering interruption, even though the app and service remain running. The backendObservedOffsetMs diagnostic confirms receipt of an offset, not completion of acoustic settling. Existing preferences are preserved.

GET /api/v1/status exposes audio.timing; POST /api/v1/timing accepts an integer latencyOffsetMs from -500 to 500 using existing bearer authentication. No extra network clock or privileged operation was added. synchronizationVerified stays false; reference-clock identity, group coordination, acoustic error and a real correction counter are not implemented in this release.

Bluetooth investigation remains paused. G10 is powered off per user and was not contacted or changed. Its last verified AirPlay enabled preference remains false from isolation; restore only when it is powered on for testing. Panel2 and stock .34 were not modified. Existing Wall Panel services/routing/startup and installed root-owned PTP binaries remain untouched.

Sources:
- https://github.com/mikebrady/shairport-sync (upstream synchronization/pipe explanation)
- https://developer.android.com/reference/android/media/AudioTrack#getTimestamp(android.media.AudioTimestamp)
- https://developer.android.com/reference/android/media/AudioTimestamp
- Local build/native-work/shairport-sync-5.6.1/audio_pipe.c,audio.h,common.c and Android PcmPump.java/NativeStack.java.
