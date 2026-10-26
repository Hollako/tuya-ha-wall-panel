# Call controls and doors — 0.14.0 / code18

User heard 0.13 chime and saw live RTSP video. Requested more ring-like/louder sound,
full-window video with overlay green Answer/red Hangup/mic Mute, then door controls.
Correction: NO CROPPING or stretching; preserve full source frame/proportions and leave
remaining aspect-ratio bars alone for now. Existing Math.min fit remains unchanged.

Installed 0.14.0 on 172.16.1.26 after confirming previous call IDLE. Background video
now has the entire window, with translucent header and bottom controls as siblings
above its normal SurfaceView. Green Answer/red Decline while ringing; microphone
Mute/Unmute and red Hangup after answering. Mic mute replaces outgoing PCMU payload
with silence; keeps RTP timing/headers/receive audio and never globally mutes Android.
Mute resets for each call. New ringtone is a repeating telephone-style 440+480Hz
double burst, soft edges and pause, stored PCM WAV; current saved ring volume85%.
Original music volume/mute restoration and independent call volume remain intact.

Door station172.16.1.30 identified via authenticated GET as DS-KD8003-IME1(B).
GET /ISAPI/AccessControl/RemoteControl/door/capabilities returned doorNo min1/max2
and cmd opt open,alwaysOpen,resume. GET VideoIntercom capabilities also reports
remoteOpenDoor support. NO real control/PUT/open request sent by the agent.

App settings have separately encrypted door HTTP(S) server/user/password and independent
Door1/Door2 enable switches. Current supplied station login configured privately,
Door1 enabled, Door2 disabled/hidden. No credentials in APK defaults or public status.
Unlock uses this station's relays, not the Tuya wall panel's local relays. App sends
only PUT /ISAPI/AccessControl/RemoteControl/door/{1|2} with RemoteControlDoor/cmd=open;
station owns pulse duration. No alwaysOpen/resume or arbitrary user-selected URL/path.
Digest challenge supports MD5/SHA256/sess/auth; no global authenticator, redirects or
automatic retry after transport failure. Only401 permits one authenticated follow-up.
Success requires HTTP200 plus XML statusCode1 and UI says command accepted, not proof
of physical lock movement. Requests limited to enabled doors in ringing/active calls,
one request at a time, minimum2s spacing; queued auth attempt checks original call token.
An already-sent command cannot be recalled. Credentials stay in memory/encrypted store.

Local tests passed: existing regression suite,333 SIP protocol/codec checks, production
RTP fake audio now checks silence when muted and restored speech on unmute,12 fake
Android chime/video lifecycle checks. New production door client tested against local
fake station: digest auth, exact open-only body/path, disabled Door2 rejection,
ended-call rejection before auth retry, no redirects/replay, response failure handling.
Signed APK integrity checked. No physical calls/ringtone/mic/video/door/reboot tests
by agent; user tests new UI, ringing, mute, door click/behavior when ready.

Existing encrypted settings backup:
/data/local/wallpanel/before-call-controls.20261025T173007Z
Private options import consumed/deleted, SIP registered afterward. No HA files changed.
See call-controls-install-result.json and app-only-verification.json.
