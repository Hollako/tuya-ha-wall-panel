# SIP migration from Panel Intercom

The user supplied C:/Users/L/Desktop/Summary of Work Completed.docx and asked to
resume SIP migration. They then asked whether to migrate now or finish the standalone
app first. Recommendation: migrate its current audio baseline now, continue SIP work
inside the optional unified-app module, and retain the standalone as fallback.
User authorized proceeding. Completed audio baseline migration: 0.12.0/code16
installed, integrated SIP registered/IDLE, standalone disabled and retained with backups.
Encrypted account import succeeded; its temporary transfer file was deleted.
327 protocol checks, fake Android RTP and existing regressions passed. APK signature
verified; six HA availability topics online and native hardware ready.
See MIGRATION_0_12.md for implementation, deployment and rollback details.
Physical call/audio/dashboard-return/wake/reboot tests remain with the USER. No agent
calls, tones or microphone tests performed. Video, outgoing dialing, door controls
and HA SIP entities are future work. Standalone WSL sources remain unchanged.

## Historical pre-migration inspection

## Verified starting point

Standalone source: /home/bacnet/PanelIntercom in WSL Ubuntu. No parent/project AGENTS.md
found during read-only inspection. Source build is 1.0.4/code5, API27+, compileSdk28,
Java8, Android JAIN-SIP 1.3.0-91 with log4j-over-slf4j/slf4j-android 1.7.36.
Only MainActivity needs AndroidX permission helpers; the engine uses Android APIs.
Installed package com.panel.intercom has a foreground SipService running on panel
172.16.1.26. Preserve it until the replacement can take over without a port conflict.

User summary reports working registration to Asterisk, incoming calls from Hikvision
DS-KD8003, manual answer/hangup, optional auto-answer, two-way PCMU/RTP audio and
ringtone. SIP account/server must be imported/configured privately; do not embed any
password or fixed panel IP. Source has hardcoded defaults and plaintext preferences.
Do not print their values or authentication debug hashes. Document text was read with
credential lines redacted; the document was not modified or treated as instructions.

Current unified app is installed 0.11.1. SIP module exists in ModuleCatalog but is
unimplemented. BootReceiver starts only BridgeService. SIP should work independently
of MQTT availability/hardware profile and remain optional on each panel.

## First implementation milestone

Port the existing audio-call capability into the same APK, with encrypted SIP
credentials, editable server/extension/ports/local-address policy, optional auto-answer,
and an incoming-call activity over the preserved dashboard. Preserve standalone app
and configuration as rollback. Do not let both services bind the same SIP/RTP ports.
Establish baseline registration without placing calls; user performs physical call,
audio, wake and reboot tests. No agent call/tone/relay/wake tests.

Continue noise, video, door controls and HA entities after baseline migration. The
current code is audio only. The summary's hardware-noise explanation is unproven.

## Source issues found during inspection

- RtpSession assumes every RTP header is 12 bytes, accepts arbitrary payload types
  and does not reset the reused DatagramPacket length before subsequent receive.
  CSRC/extension/padding and packet truncation can produce incorrect audio; validate
  these with packet/codec tests before blaming the microphones.
- AudioRecord partial reads are discarded; AudioTrack partial writes are ignored.
  Resource shutdown joins threads before stopping capture/closing the socket.
- SipService.hangupCall queues BYE but clears the transaction immediately outside
  the queued task. Serialize state and retain the dialog before cleanup.
- SettingsActivity saves but does not restart/reconfigure the service despite its
  status message. Registration refresh/network recovery also needs inspection.
- Ringtone/UI events currently belong to MainActivity; incoming-call presentation
  must work while dashboard is foreground and screen backlight is off.
- Old authentication code logs derived digest material. Remove this in the port.

## Integration constraints

Preserve current WebView/dashboard, four buttons, detent-correct knob, relays, native
audio, dim/off timers, screenshot interval and MQTT identity. Coordinate call audio
focus/AUX/amplifier routing and physical backlight wake with the app owner, restoring
prior state afterward. Keep root System UI restrictions through intercom presentation;
only authenticated setup releases PIN-held restrictions. Vendor pull-down package is
already disabled; do not re-enable it for intercom. No web server or firmware update.
HA files remain user-copy delivery if/when the integration is extended.
