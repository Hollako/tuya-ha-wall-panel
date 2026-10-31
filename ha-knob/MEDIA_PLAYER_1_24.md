# Wall Panel integration 1.24.0

The original controller Audio entity now includes room grouping, per-room volume
and mute (1% steps), source-aware transport, and local music browsing. Its unique
ID is unchanged, preserving the existing local knob media route and automations.
The controller's redundant Room audio registry entry is removed on discovery;
remote rooms and saved groups remain separate players.

Group API failure no longer requires using a separate player: MQTT audio remains
available. Group errors distinguish an unreachable address, rejected API token,
and invalid directory response. Duplicate group poller startup was removed.

## Current lab connection issue

Read-only checks on 31 October 2026 found HA still configured for 172.16.1.26.
Panel 1 is now 172.16.1.40. Both panel directories report both rooms online and
authorized. This is a stale HA endpoint, not an audio-engine failure.

## Install

1. Replace `/config/custom_components/tuya_wall_panel` with the folder from this
   package, then restart Home Assistant.
2. In this integration's Configure menu, open Audio groups connection. Set the
   host to `172.16.1.40`, port `8099`, enable grouping and enter Panel 1's API token
   from its audio/web settings if one is configured. Save.
3. Confirm the existing Audio player and Panel 2 Room audio are available. The
   Panel 1 Room audio duplicate disappears when the directory is reachable.
   Test joining/leaving, volume, mute and source playback from those players.

Reserve the panels' current IPs in DHCP to avoid another stale configured host.
No automatic DHCP/mDNS endpoint recovery is included in this release.
No APK update, panel reboot or audio-engine modification is required.
Code checks: 120 isolated HA integration tests passed. Physical playback and HA
deployment remain for the user to verify.

Bluetooth's strict single-sender rule is still unreliable and explicitly deferred.
