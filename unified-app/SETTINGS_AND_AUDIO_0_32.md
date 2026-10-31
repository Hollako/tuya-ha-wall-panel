# Shared settings and audio controls

Unified app **0.32.0 (72)** and dedicated Audio Receiver **1.9.0 (33)** use the
same settings renderer, playback page, source controller and room-volume authority.
Existing saved credentials, panel identity, group presets and timing values are retained.

## Panel and browser

Open app setup, or visit `http://PANEL_IP:8099/settings`. The audio/rooms page is at
`http://PANEL_IP:8099/`. Use the configured LAN port if it differs from 8099.
Both interfaces edit the original settings; there is no web-only configuration copy.

Sections separate dashboard/kiosk, HA/MQTT, network assignments, audio routing,
AirPlay, Bluetooth, grouping, display, knob, SIP/video/doors, hardware and LAN access.
The dedicated audio app shows the relevant audio sections only. One Audio network
selection covers discovery, grouping and AirPlay; Automatic follows the panel network.
HA/dashboard and SIP/video retain their independent network assignments.

Only changed fields in the saved section are submitted. Blank secret fields keep
their saved value; clearing requires an explicit checkbox. RTSP address and camera
credentials have separate fields, so an IP change retains the encrypted login.
Dirty forms survive status refreshes and navigation asks before discarding changes.
The administrator PIN protects browser settings when dashboard locking is enabled.

Android permissions/pairing and initial installation/repair still open native panel
dialogs. Hardware root preparation and restoration require their existing on-panel
confirmation. HA entity/page/action selection remains in the HA integration.

## One playback control

Select a source room and listening rooms, then use the main Play/Pause button.
Automatic detection recognizes active Bluetooth, app audio, group/library playback,
AirPlay and the unified app's local player. Conflicting active sources require an
explicit choice. Selecting a library track, URL or saved group prepares a selection;
the main button starts it. Membership changes preserve an unchanged source pipeline.

AirPlay grouping remains controlled by its sending device. Native local-player or
Line Input controls stay local; library tracks, Bluetooth and captured app audio use
the panel grouping engine. Previous/Next are available where the source supports them.

## One room level

Each room has one saved 0–100% volume in 1% steps and one mute state. App, browser,
HA and knob use that authority, with the same level whether grouping is active or not.
The bulk slider changes each selected room's level; it is not another gain stage.
Setting volume to zero does not latch explicit mute. Raising it restores output.

Fine software gain only fills the space within Android's hardware steps for app-owned
and grouped outputs. Direct third-party/Bluetooth playback retains Android's hardware
step granularity. AirPlay input trim stays separately labelled Advanced. Music-level
changes during SIP calls defer hardware changes until the call ends.

## Startup and changed addresses

The lightweight LAN/discovery service starts after boot independently of AirPlay
enablement. Audio capture and streaming start only on request; stopped groups do not
automatically restart after reboot. Address changes refresh discovery and the web
listener. Active receivers retry their leader while retaining volume, mute, timing and
pause state. A moved room with a saved token must pass a fresh identity challenge before
its token is sent to the new address. Changed credentials still need explicit pairing.

No native AirPlay decoder, clock/synchronization implementation or group executable
was changed. See [network recovery](../shared/multiroom/NETWORK_RECOVERY.md).

## Validation

Host checks cover room migration/persistence, 1% levels, mute/zero, external volume,
SIP deferral, live PCM gain and call priority, source selection, web settings/credentials,
listener replacement, leader retry/identity, native payload hashes and both ARM builds.
The provisioning tests run in Linux because their fixture symlinks require it.
Physical playback, reconnection, reboot and listening checks remain user-run.
