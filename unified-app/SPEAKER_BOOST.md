Historical experiment: REMOVED in0.14.4 at user request; no audible benefit reported.
Do not reintroduce without request. Larger controls remain.

# Larger controls and speaker boost - 0.14.3 / code21

Buttons increased from88x58dp to112x72dp, labels12to14sp, icons23to28dp. Same single
horizontal row and complete uncropped camera frame. User found max audio too quiet.

Read-only inspection: saved codec trim100/current rk817_spk_volume raw3; Android
voice stream reached5/5 and music20/20. Already max within established driver range.
No writes beyond that range. Firmware audio_effects.conf declares loudness_enhancer.

SipSpeakerBoost attaches Android LoudnessEnhancer to the specific ringtone MediaPlayer
and received-audio AudioTrack session, never session0 or the microphone. Config field
speaker_boost_db0-12, fresh/existing configs default0. Current panel enabled6dB via
private consumed options import; ring/call volumes100 retained. Intercom settings
has the boost field, zero disables. Target600mB means6dB maximum amplification with
platform compression for out-of-range samples; physical speaker limits still apply.
Unsupported/rejected effect falls back to normal playback; always released before
its player/track. Mic gain/codec/AEC/NS/routing and outgoing RTP remain unchanged.

RuntimeJSON reports configured speaker_boost_db and call_boost_active; false is normal
while IDLE. Check during a USER call if boost seems ineffective. Actual attachment,
loudness/clarity and echo require user verification, not claimed by fake tests.

Local tests pass334 SIP checks and14 production presentation checks; output-session456
is distinct from fake input123, target600 and effect release verified. Constructor
failure fallback verified; RTP cleanup/mute/outgoing payloads unchanged. Existing
regressions and signed-APK integrity checks pass. No agent calls, tones, mic capture,
camera playback or real door commands.

Installed0.14.3 on172.16.1.26; SIP registered/Hikvision brand preserved. Prior encrypted
preferences backup /data/local/wallpanel/before-speaker-boost.20261025T185351Z.
See speaker-boost-install-result.json. No HA/root/native-driver changes.
Reference: https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer
