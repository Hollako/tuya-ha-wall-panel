# Tuya Wall Panel for KSA Home Assistant

Version **1.10.0** adds **Dim after**, **Display off after** and **Dim brightness**
configuration numbers for app 0.7/shared controller. Timers count total inactivity
from the last screen interaction; zero disables each. Defaults are 1 minute dim,
5 minutes off and ~10% dim brightness. Main display controls normal brightness,
which stays stable while actual PWM dims. Copy the component folder and restart HA.

Version **1.9.0** supports app 0.6 audio ownership and independent amplifier
availability, keeping the existing media player and control IDs. Copy the component
folder and restart HA; keep the integration entry. Audio listening tests remain with
the user. Dashboard URL delivery is still under review in the user's environment.

Version **1.8.0** implements **Dashboard URL** delivery to app 0.5+ through MQTT,
including pending changes while offline. The entity attributes show the panel URL,
confirmation status and delivery error. An open dashboard switches to the new URL;
unchanged URLs do not reload. Existing unconfigured blank HA fields are not applied.
The knob input/display now have independent availability during app handover.

Version **1.7.0** also adds **Open setup** (available when the app runtime is connected)
and respects individually disabled button/relay modules. Opening setup retains the
app's configured PIN protection. Copy the component folder and restart HA to upgrade.

Version **1.6.0** adds an editable **Dashboard URL** text entity and a Configure
page for saving/copying the link from HA. It stores the URL in HA only; automatic
delivery to the Android app is not implemented yet. It preserves path/query/fragment,
works while the panel is offline, and sends no MQTT or hardware commands. Upgrade
by replacing the component folder and restarting HA; keep the existing entry.

Current package: **1.10.0**, using `custom_components/tuya_wall_panel` and the
`tuya_wall_panel` domain throughout the integration. Follow [INSTALL.md](INSTALL.md)
for the upgrade instructions. Existing `tuya_wall_panel` users replace the folder
and restart HA, keeping their configuration entry, pages and button assignments.
The earlier installation records below
describe previous releases; their old internal paths are not current instructions.

This is a local custom integration for the existing rooted Sznaner/PX30 panel bridge. **Targets and button functions are selected in HA's UI, never fixed in Python or YAML.** It requires the matching panel bridge with `frames` and `local_buttons` capabilities and HA's existing MQTT integration. It has no broker password, external account, cloud endpoint or pip requirements of its own.

The HA integration, package, folder, storage keys and device-event type use the Wall Panel name. MQTT topics and Android service paths remain the existing hardware transport interface; this package changes only the HA component.

## Configure in HA

Add **Tuya Wall Panel** under Settings → Devices & services → Add integration. Its MQTT base defaults to `wallpanel/px30_172_16_1_26`. Choose any number of entities from **Climate, Weather, Light, Cover and Media Player**. They can be from mixed rooms/domains. The multi-entity picker supports rearranging the selected entities. Clock is a separate checkbox and uses HA's configured timezone; 12/24-hour format is configurable.

Use the integration's **Configure** button at any time:

* **Entities, Weather and Clock**: add/remove entities, enable Clock, choose adjustment steps and optional automatic red/white LED feedback.
* **Page order**: move any selected page, including Clock, up/down and save. Changing the selected entity list resets this explicit ordering to the entity picker's order, with Clock appended; arrange pages again if desired.
* **Button assignments and scenes**: choose a physical button, numbered 1–4 top-to-bottom. Configure its single and double press independently. Select a preset function or use HA's ordinary action editor to activate scenes, run scripts or call other HA actions. No YAML editing is required.

Version **1.4.2** unifies the two HA devices into one **Tuya Wall Panel** device owned by this integration. It adds native ownership of the existing two relay switches, four separate Button event entities, one Knob event entity, three manual text fields, knob-display power/follow controls, two color selectors, external-speaker switch and amplifier hardware level. Together with the existing controller, audio and management entities this is 28 entities. MQTT remains the local transport and is still required, but panel entities no longer belong to its integration.

The earlier MQTT consolidation uses HA's `async_update_entity_platform` API while the old MQTT entities are unloaded. It preserves existing MQTT registry entries when applicable. Panel-specific discovery metadata is backed up and suspended. Other MQTT devices remain unchanged. No panel driver binary or firmware is changed. A hot MQTT migration is refused to avoid duplicate owners. The current **tuya-wall-panel.zip** uses the new `tuya_wall_panel` domain and storage keys; replacing the old custom integration is a clean installation, not a domain migration.

The controller adds Active entity, Entity control enabled, Next entity, Selection menu and diagnostic status entities to the unified device. Disabling entity control pauses knob-driven HA control and automatic text rendering, allowing manual text fields to be used. Independently configured button HA actions and local relay assignments remain separate.

## Knob gestures

Version **1.2.0** adds panel management to the same Tuya Wall Panel device:

Version **1.2.1** changes automatic screenshots to five minutes and corrects the native reboot invocation to `/system/bin/sh /system/bin/svc power reboot`. On this firmware `svc` has no shebang, unlike `am` and `settings`, so directly calling `execl` on it failed. The corrected bridge is installed, and the user subsequently confirmed that reboot works. The user explicitly requested to perform live panel tests themselves. Do not trigger further panel tests without a new request.

Version **1.2.2** adds immediate volume-slider state with bounded pending targets to avoid bounce from stale MQTT feedback. The updated audio APK distinguishes Android's zero-volume auto-mute from explicit mute, and slider changes above zero unmute playback. Only the dedicated vendor volume-overlay service is disabled. See `audio/README.md` and `audio/volume-fix-install-result.json`. The user subsequently confirmed the volume fixes work.

Version **1.3.0** adds selectable Media Player pages and sends the first rotary adjustment without the old 300 ms pause. A bounded worker keeps the latest target per entity while preserving accumulated steps; subsequent rapid updates are limited to approximately 10 per second. The accompanying native response fix removes button-triggered main-display wake and excludes the knob from backlight input grabs. The user explicitly chose to keep exclusive single/double press with the existing 400 ms window. Physical response testing is left to the user.

* **Main display** is a brightness-capable light. OFF uses the existing physical backlight-off controller; Android, Wi-Fi and the dashboard remain awake. ON restores the saved brightness and resets the five-minute idle timer.
* **Turn display off** and **Wake display** are explicit buttons. A touchscreen contact wakes the main display and is consumed. Knob rotation/press and the four hardware buttons work with the main display off and do not wake it.
* **Reboot** under Configuration requests Android's graceful reboot. It is never sent by startup, reconnection or options reload. The native bridge rejects retained commands and deduplicates a pending reboot.
* **Screen screenshot** is an image entity. It receives a full-resolution PNG on startup and approximately every **five minutes** (updated in 1.2.1); **Refresh screenshot** requests another (minimum five seconds between requests). Image reads return cached bytes and do not start captures or change timestamps. Capturing while the backlight is off does not wake it.

Brightness is saved atomically as a root-only 1–255 value, independent of display power. It persists across waking and rebooting. The screenshot helper captures and publishes from RAM on a separate bounded process/MQTT connection, so image transfer does not block knob/buttons/relays. PNG payloads are not retained by MQTT, and HA keeps only the latest image in memory. The panel opens no new network listener. Screenshot access uses HA's normal authenticated image entity facilities. See the [official image entity interface](https://contributors.home-assistant.io/core/entities/image/).

Validation: 19 existing controller/audio tests, six media-player tests, six management tests, native command safety checks, and `panel-172.16.1.26/management-live-result.json`. The native install report checks preserved kiosk/network processes, knob preferences, button bindings, both relays and audio availability. Native backups are `/data/local/panalume/panel-mqtt.before-management` and `panel-backlight.before-management`; HA's previous component is `/homeassistant/panel-backups/wall-panel-before-management`.

Version 1.1.1 also adds **Tuya Wall Panel Audio** (`media_player.tuya_wall_panel_audio`) with source selection, URL/HA media playback, volume/mute and play/pause/stop. See [audio setup and verified limits](../audio/README.md). The audio entity operates independently of the Knob entity control switch.

| Gesture | Normal page | Selection menu |
| --- | --- | --- |
| Rotate | Adjust the active entity, or browse weather forecasts | Move highlight, without operating the entity |
| Single press | Light power, AC power, cover stop, supported media play/pause or mute; weather returns to current conditions | Confirm highlighted entity |
| Double press | Next page, wrapping around | Next highlighted entry |
| Long press | Open the selection menu | Cancel and return to the previously selected entity |

Menu timeout defaults to 20 seconds and can be changed in HA. Clock is read-only. The four physical buttons emit reliable single/double events but **do not expose a reliable long-press/release signal**; knob long press remains supported.

## Entity behavior

* **Climate**: rotate target temperature using the entity's own step, or a user override. Respect min/max and supported features. Heat/cool range mode shifts both endpoints while preserving the range. Press turns off, or restores a previously observed supported mode (otherwise prefers available cooling for these ACs).
* **Light**: rotate brightness, default 5%/click; press toggles. On/off-only lights retain press control and do not receive unsupported brightness commands.
* **Cover**: rotate position, default 5%/click, only when the entity supports position control and reports a known position. Press stops only if supported. Unknown positions are never guessed.
* **Media Player**: rotate volume, default 5%/click, configurable in HA. Requires a known volume and absolute volume support. Press pauses a playing entity or resumes a paused/idle/on entity where supported; otherwise it toggles mute if available. The knob shows volume, mute/playback status and available track title. Choose the panel's own `media_player.tuya_wall_panel_audio` or another media player; no media entity is hardcoded or automatically selected.
* **Weather**: show current temperature/condition. Fetch HA's hourly forecast when supported, otherwise daily/twice-daily; rotate through periods, press returns to current conditions. Cache for five minutes. It never operates another entity.
* **Clock**: show time and date from HA's configured timezone. Update on minute changes; no hardcoded timezone or HA time/date helper is needed.

The first rotation dispatches immediately. Rapid rotations accumulate pending targets and coalesce subsequent commands at 100 ms intervals, so four half-degree clicks become a two-degree adjustment rather than repeatedly starting from stale HA feedback. Slow service calls do not block gesture receipt or create an unbounded command queue. Pending commands retain their original entity even when the displayed page changes. Unavailable entities receive no actuator commands; disconnecting or disabling control discards queued adjustments.

## Four independent button functions

Each button's **single press** and **double press** can independently be:

* No additional action (preserves the raw HA event for existing automations).
* Next / previous knob entity.
* Open/cancel the knob menu.
* Confirm the highlighted entry.
* Toggle built-in relay 1 **locally**.
* Toggle built-in relay 2 **locally**.
* Run user-selected HA actions, including scenes and scripts.

Local relay settings are sent to and saved by the panel, not executed by HA. They work without HA or Wi-Fi, provided the Android panel and its internal relay module are powered and working. Both gestures are classified on the panel, so a double press does not also operate a single-press relay assignment. A single press waits 400 ms to distinguish a double press. All four buttons operate on the first press with the main display off and do not wake it. HA actions and knob menus require HA connectivity.

The vendor MCU retains ownership of its UART through the existing adapter. Its scene mode remains in place so all four button IDs remain independently available. There are exactly **two physical relays**. Binding configuration, MQTT reconnection and startup never toggle a relay; a fresh hardware gesture is required. Local commands use MCU feedback and bounded pending targets. Offline button events are not replayed into HA after reconnection. Existing separate HA Button 1–4 and Knob event entities remain intact.

## Panel protocol and persistence

Normal HA rendering sends non-retained messages to `BASE/knob/display/frame/set`, with exactly three newline-separated text fields (32/16/32 supported characters). The frame is validated atomically and kept **only in RAM**. Clock/sensor changes do not rewrite root settings. Manual text values are saved separately from automatic display content. The current Follow Main preference, requested display power and accent color are not changed by the integration. Optional automatic LED feedback is OFF by default; when enabled, it chooses the physically verified red/white states.

`BASE/button/N/binding/set` uses `single,double`, where each value is 0 (no local relay), 1 (relay 1 toggle) or 2 (relay 2 toggle). Root-only `/data/local/panalume/button-local-bindings` saves all four assignments atomically. Matching `/binding/state` topics report applied values. The panel ignores retained command/configuration messages. The integration resends configuration when the panel reconnects. `BASE/knob/controller/capabilities` advertises support.

The integration stores its last selected page, enabled state and observed AC modes using HA's storage API. Options reload cleanly unsubscribes, cancels timers/tasks and unloads HA action scripts. It installs no Android app and changes no main-display sleep/wake logic.

## Verification

Installed on KSA HA Core 2026.10.0 on 23 October 2026. Open [Tuya Wall Panel](http://172.16.1.22:8123/config/integrations/integration/panalume_knob) and choose Configure. The initial pages are KSA Home Forecast and Clock; other target entities remain user-selected. The controller device is [Tuya Wall Panel](http://172.16.1.22:8123/config/devices/device/66cb9625f498c240d4f391759c3221e5).

Live UI checks confirmed the entity picker, saved Weather/Clock pages, page-order form, separate single/double button functions, and the standard HA action picker. A temporary Button 1 local relay assignment was saved and echoed by the panel without operating either relay, then cleared. All four local bindings finished `0,0`, both relays OFF, Follow Main OFF, and the persistent knob-settings SHA256 remained `1ebb61d2c0071923eb41547df357085a06c03b10ea4938508637fedbc4c85f9c`. See `live-configuration-check.json`. Actual new offline button-to-relay operation still needs a physical check; the native offline behavior has been tested with simulated outputs. No live climate/light/cover actuator commands were used for validation.

`python -m unittest test_controller test_audio_entity test_device` passes 44 isolated checks using fake services/MQTT, including immediate first-detent dispatch, slow-service step accumulation, media capabilities and the earlier volume fixes. Native `test-mqtt-input.c` checks one event per simulated rotary pulse; `test-module-bridge.c` and `test-gestures.c` use fake sockets/events. The latest native install report is `panel-172.16.1.26/response-fix-install-result.json`; it confirms unchanged saved settings and kiosk/network processes. No real hardware input or actuator test was performed for this release.

`build_package.py` produces the integration ZIP without tests, caches or credentials. `transfer_package.py` serves only that ZIP to HA's IP, once, and closes within three minutes. HA installation places only this component under `/homeassistant/custom_components/panalume_knob`; it does not edit configuration.yaml, automations, scenes or existing integrations.

Final consolidation verified on 24 October 2026: exactly 28 panel registry entries, one device, zero panel MQTT entities/devices, and preserved registry IDs, entity IDs, unique IDs, user customizations and config options. Version 1.4.2 retires panel-specific discovery metadata after startup/reconnect and removes unused MQTT copies only where an exact native counterpart exists. Driver binaries and saved panel settings were unchanged. See `UNIFICATION.md` and `unification-deployment.json`. Final live hardware testing remains with the user.
