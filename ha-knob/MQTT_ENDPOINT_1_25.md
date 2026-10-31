# Automatic group API address updates — integration 1.25.0 / SmartWay Panel 0.34.2

SmartWay Panel publishes a retained `<mqtt_base>/app/group_endpoint/state`
message containing only `v`, stable room `id`, IPv4 `host`, and API `port`.
It announces on MQTT connection/reconnection, HA birth, address/port changes,
and every 30 seconds. The address belongs to the selected audio/API interface,
which can differ from the default Android network on a dual-network panel.

HA subscribes under the controller's existing MQTT base. It verifies the new
address with `/api/v1/identity` before using it. When an API token exists, a
random challenge and HMAC-SHA256 proof verify knowledge of that existing token
before HA sends credentials to the new address. Without a token, identity is a
stable-ID match anchored in the existing trusted MQTT topic, not cryptographic
device authentication. The group directory must report that same local ID.

Verified addresses are saved in HA storage, separately from the options seed,
so learning an address does not reload the integration or restart audio. Group
polling, commands and local library browsing all use the verified address.
Changing the configured host/port/token manually invalidates the saved override.
Configure shows the effective address; the Audio entity exposes `group_api_host`
and `group_api_port` for diagnostics. Disabling Audio groups prevents learning.

This release also includes 1.24's consolidation into the existing Audio player;
remote panel players and saved groups remain available.

## Install and check

Replace `/config/custom_components/tuya_wall_panel` with this package's folder
and restart HA. SmartWay Panel 0.34.2 is required on the unified panel. The
dedicated Audio Streamer does not need an update: Panel 1's directory already
tracks its peers, and HA queries that directory through the recovered endpoint.

After installing, confirm both players are available and grouping works. To test
address recovery later, let DHCP give Panel 1 a different reachable address;
HA should adopt it without editing Audio groups connection. Physical network
changes, playback tests, and HA deployment are left to the user.

Validation: 130 isolated HA tests, full APK build, and receiver payload checks.
API tokens and MQTT passwords are never included in announcements or reports.
