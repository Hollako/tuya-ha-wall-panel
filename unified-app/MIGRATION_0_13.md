# Incoming chime and RTSP video — 0.13.0 / code17

Installed on 172.16.1.26 after user confirmed the 0.12 audio migration: no noise,
answer/hangup/close and two-way audio work. Standalone Panel Intercom and Linphone
were completely uninstalled at the user's request. Old rollback app is no longer
installed; historical 0.12 migration rollback instructions are not directly usable.

The previous ringtone relied on Android's default ringtone URI/ring stream and was
not audible to the user. New SipRinger plays an app-owned looping PCM chime through
the existing intercom amplifier/source lease on STREAM_MUSIC. It has a separate
ring enable/0–100 volume option, default65, and restores original music volume/mute
on answer, decline, remote cancel, timeout, service disable or media failure. A zero
ring volume/disabled ring changes nothing. Volume is restored before switching to
communication audio. No ringtone/call audio was played by the agent.

Intercom settings now has optional RTSP preview and a masked URL field. It is stored
inside the same Keystore-encrypted SIP config. Existing account and all defaults
migrate without resetting SIP credentials; video remains disabled on fresh installs.
An allowlisted private pending-options import can update only video/ringing settings
without exporting/decrypting the existing account outside the app. Unknown keys fail.

User supplied camera 172.16.1.30 and login. Same-connection RTSP DESCRIBE authenticated
successfully (200) and reported H264/90000 and PCMU/8000 at /Streaming/Channels/101.
No RTSP PLAY/SETUP was sent. Credentials were transferred via stdin/root-private file,
imported/encrypted and the transfer file deleted. No account details are in this doc,
APK defaults, build output or public status. Backup of previous encrypted preferences:
/data/local/wallpanel/before-sip-video.20261025T170947Z

IntercomVideo uses Android native MediaPlayer/SurfaceView in the existing call window,
starting on ringing and continuing after answer. RTSP is a separate video preview;
SIP audio negotiation still rejects SIP video and owns the microphone/two-way audio.
The preview is volume-zero before preparation/start and its audio track is deselected
when possible; it requests no audio focus. Surface is fitted to the stream aspect ratio.
Video setup runs on its own worker so connecting does not block answer/hangup/UI.
First-frame timeout12s, at most3 attempts; failures leave audio controls available.
Each attempt owns its own player, avoiding stale cleanup releasing a newer player.
Generation checks ignore old prepare/error/frame callbacks after retry/close. Pausing
the call activity, surface destruction or call completion releases the preview and
its worker. A slow native networking call can delay that worker's queued release;
it never blocks the main or SIP thread. Actual firmware RTSP behavior requires USER test.

Regression suite passed: existing configuration/store/MQTT/audio/display checks,
333 SIP codec/protocol tests with fake PBX/media, real RTP against fake Android audio,
and12 production presentation checks covering mute/volume restore, disabled/failure
paths, muted preview, stale callbacks and isolated player cleanup. WAV format checked;
signed APK chime asset is ZIP_STORED for AssetManager.openFd. APK signature and all six
provisioning payloads pass integrity checks. No agent calls, tones, mic capture, camera
playback or reboot. SIP registered/IDLE after installation. User should test ringing,
preview before/after answer, two-way audio, hangup/caller cancellation and dashboard return.

Reference: Android MediaPlayer supports RTSP setDataSource:
https://developer.android.com/reference/android/media/MediaPlayer
Hikvision RTSP path reference:
https://supportusa.hikvision.com/support/solutions/articles/17000129064-how-do-i-get-my-rtsp-stream-
