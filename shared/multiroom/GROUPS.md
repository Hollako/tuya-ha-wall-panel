# Rooms and panel-owned groups

Unified app 0.28.0, dedicated receiver 1.6.0, HA integration 1.21.0.

Open Group playback > Rooms and saved groups in either app, or open
`http://PANEL_IP:8099/groups` from a phone. The existing API/web option must be
enabled in Audio Receiver settings. Room names can be changed on this page.

Panels advertise `_wallpanel-group._tcp` using Android NSD. Each installation has
a persistent UUID, so a DHCP address change does not create a different room.
Previously discovered rooms remain listed when offline. Availability is checked
by HTTP; an mDNS goodbye alone does not mark a playing room unavailable.
If multicast discovery is blocked, add a room by IP/hostname and API port.
If the room has an API token, enter that room's token in Add a room. Tokens are
stored only in private app preferences and are never returned in directory JSON.
An address change requires pairing again when a token was bound to the old address.

## Playback and membership

Local music can be browsed from the web and HA. On the web group page, choose a
Source room, open Local music on source panel, then Browse folders or All music.
Search matches title, artist and album. Play selected rooms starts that file on
the chosen panel and uses the checked rooms. Select source only saves the file
without playing (stop that source room first if it is already playing).

In HA, open Room audio or a saved-group player, then Browse media. Select the
panel's local music, a folder or All music, and a song. Selecting music on a room
that is currently a receiver makes that room the source while retaining the
current members. A saved group uses its configured source panel and members.
The separate Home Assistant media branch remains available as well.

Music is read from Android's external audio MediaStore, with read permission.
The panel app requests that permission when entering the group manager. Existing
test panels are granted the declared read permission during installation. New
files must be indexed by Android (USB/MTP or a media scan); Refresh library reloads
the index. Tracks are paginated, up to 10,000 indexed files per panel. Only opaque
audio/folder IDs are exposed, not filesystem paths or file download endpoints.
Existing decoder format limits still apply. Queues, shuffle and automatic next
track are not added in this update. Changing a playing track restarts that source
with the existing group engine; gapless switching is not promised.

Configure an audio file on the source panel using Choose local audio file and
Save source without playing, or use a direct HTTP/HTTPS audio URL. Files are read
on that source panel; selecting another source does not transfer a file from the
previous panel. Group audio currently supports these file/direct-URL sources,
not capture of YouTube, Spotify, AirPlay or arbitrary Android apps.

Choose Source room, check the required rooms, then Play selected rooms. Adding or
removing receivers preserves an already playing leader. Moving a leader into a
different group stops its unselected former receivers rather than leaving them
trying to reconnect indefinitely. Leaving a leader dissolves its live group;
leaving a receiver affects only that room. Pause/resume applies to the source.
The old per-panel settings still allow pausing only that receiver.

Each room has independent fine volume, mute and timing. Mute preserves its volume;
raising volume clears mute. Idle room changes save group settings without changing
another active audio source. Starting group playback restores the saved room level.
Group volume sets the active members to the same absolute level; the displayed
group level is their average. Timing, decoder and synchronization engines are not
changed by discovery or membership management. Intercom retains priority.

Save selection creates a named preset without playing. Presets contain stable room
IDs, source room and name. They are stored in private preferences and replicated
between discovered panels; offline panels catch up later. Deletions replicate as
tombstones. Recalling a group with unavailable rooms reports partial failure while
other accepted rooms continue. Presets do not start automatically after a reboot.

## Home Assistant installation

Copy `custom_components/tuya_wall_panel` from the supplied ZIP/folder over
`/config/custom_components/tuya_wall_panel`, then restart HA. No YAML or MQTT
discovery migration is needed. Existing entities and settings are retained.

In the existing Tuya Wall Panel integration, choose Configure > Audio groups
connection. Enable it and enter any updated panel's IP, API port (8099 by default)
and its API token if configured. HA polls this panel's directory every five seconds.
It creates a child device for each discovered room, with Room audio, Group timing
and Group connection entities. Standard `media_player.join`/`unjoin` operates on
these Room audio players. The original panel Audio entity remains unchanged.

Configure > Saved audio groups creates/edits/deletes presets. Each preset also
appears as a media player. Play recalls it; pause/stop/volume/mute control that exact
active membership. An inactive preset cannot pause another live group that happens
to use the same source. The Audio rooms sensor lists available panels and membership.

HA is a client of the same authenticated LAN API. Disconnecting HA does not stop
audio, erase presets, change membership, or remove control from panel/mobile pages.
If the configured directory panel itself is offline, HA marks these controls
unavailable; another panel's group page still works for its reachable rooms.

## API

GET `/api/v1/groups`: version, self ID, panel states, saved presets, last operation.
POST `/api/v1/group-command` accepts an `op` and its fields:

| Operation | Fields |
| --- | --- |
| `pair` | host, port, token (optional) |
| `room` | target ID; volume 0–1, muted, roomDelayMs -500–500, name (optional) |
| `source` | target ID, source HTTP/HTTPS/content URI; room must be stopped |
| `set_group` | leader ID, members list (source always included) |
| `leave` | target ID |
| `transport` | leader ID, command play/pause/stop |
| `group_volume` / `group_mute` | leader ID, volume / muted |
| `save` | name, leader, members; optional existing preset id |
| `activate` / `delete` | preset id |

Existing bearer authentication protects both APIs. `accepted` means the command
was accepted, not that a physical speaker was tested. Actual connection/playback
states follow asynchronous service startup and discovery polling. Errors identify
unavailable rooms. No HA or separate Snapcast server is required.

GET `/api/v1/library` accepts target (optional panel ID), folder (root/all/opaque
folder ID), offset, limit (1–200), q and refresh. It proxies other rooms using the
existing private peer credentials. New commands `select_track` and `play_track`
take leader and an opaque track ID; play_track also accepts members. `play_source`
plays a direct audio URL with the same panel-owned group orchestration. Browsing
never selects a source or opens an audio device. Local songs remain on the source
panel and keep playing when HA is unavailable.
