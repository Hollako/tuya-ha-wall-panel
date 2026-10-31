# Confirmed group feedback — HA 1.26.1

SmartWay Panel 0.34.3 publishes the actual room membership from its directory to
`<mqtt_base>/app/group_state` whenever it changes. HA applies live messages
directly to its players, without waiting for its five-second HTTP poll. These
messages do not contain API credentials. Retained messages are ignored by HA;
HA requests a fresh snapshot on startup through `/app/group_state/get`.

Audio Streamer 1.11.2 and SmartWay Panel 0.34.3 acknowledge `_leave` only after
the local group service has stopped. The controller immediately reads the remote
room after its acknowledgement, then publishes that confirmed result over MQTT.
The UI does not optimistically assume that a command succeeded. A room that
has left reports no HA group members, even if an older leader value is present.

HA rejects malformed snapshots and snapshots for another directory. An HTTP
request that was already in flight cannot overwrite a newer MQTT update. HTTP
polling remains as recovery and for metadata not carried in membership messages.
There can still be network/service processing time before confirmation; this
removes the additional HA polling wait. Peer changes outside the controller's
command path still depend on its ordinary peer-status refresh.

Replace `/config/custom_components/tuya_wall_panel` with this package's folder
and restart HA after updating both panels. Test removing Panel 2 in HA: its
toggle should follow the confirmed leave without waiting another 5–10 seconds.
No playback/relay/call/reboot test is performed by the installer.

Checks: 139 isolated HA tests; both APK builds; existing receiver payload and
shared output checks. The decoder and synchronization engines are unchanged.
