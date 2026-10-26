# Tuya Wall Panel 1.14.0

Version 1.14 adds **Knob activity mode**, requiring Android app 0.17.0. By default,
physical knob use wakes its small display and makes the LED white. Fifteen seconds
after the last use, its display turns off and the LED returns to red. This runs
locally without HA/Wi-Fi and takes precedence over manual knob power/follow/LED.
Disable the mode to restore the old manual behavior. The main display is unchanged.

Version 1.13 adds **Reload dashboard** and **Restart app** under Configuration.
These require Android app 0.16.0 or newer. Reload refreshes the existing WebView
without restarting SIP/hardware. Restart briefly disconnects all app services,
ends any active call, and relaunches the app using its saved settings and module
enable flags; it does not reboot Android. Both commands are non-retained.
The three manual knob text entities are removed; automatic knob frames continue.

## Upgrade an existing `tuya_wall_panel` installation

Extract `tuya-wall-panel.zip` locally. Copy its `custom_components/tuya_wall_panel`
folder over `/config/custom_components/tuya_wall_panel` (or the corresponding
`/homeassistant/custom_components/tuya_wall_panel` path), then restart HA Core.
Keep the existing integration entry, entities, assignments and MQTT configuration.
Do **not** delete/re-add the integration for this upgrade.

Version 1.10 adds Dim after, Display off after (minutes; 0 disables a timer) and
Dim brightness (%) under the device's Configuration entities. Defaults: dim after
1 minute to ~10%; off after 5 minutes TOTAL from last screen interaction. The existing
Main display light controls normal brightness/power. Dim time must precede off time
when both are nonzero. Copy this update, restart HA and tap Start/apply hardware on
app 0.7 for user verification. Knob/button actions do not wake/reset the main display.

Version 1.9 supports the app-contained audio engine in app 0.6, separate amplifier
availability and automatic ownership fallback. Existing audio entity IDs are kept.
Dashboard URL delivery remains under review; no URL transport fix is included here.

Version 1.7 adds **Open setup**, which sends a non-retained app command and is available
when the optional app runtime is connected. PIN protection is enforced by the app.
It also honors app-reported button/relay module enable flags; the knob and other
remaining bridge features keep their independent availability.

The new **Dashboard URL** text entity appears under the same Wall Panel device.
You can also edit it through **Configure → Dashboard URL**. Save the URL here to
apply it to app 0.5+. Leave blank to clear. HTTP/HTTPS links up to 255 characters are supported, including query
parameters and fragments. This uses the [HA text entity state limit](https://contributors.home-assistant.io/core/entities/text/).
The setting is stored in HA and remains editable while the panel is offline.
With app 0.5+ and its hardware service running, it is delivered automatically and
saved on the panel. Pending changes are retained until reconnection. An open
dashboard switches to the new URL. Entity attributes `applied_to_panel`, `panel_url`
and `delivery_error` distinguish the requested URL from panel confirmation. A blank
URL explicitly clears the panel dashboard; untouched blank HA defaults do not.

## Historical clean installation from the old test domain (1.5.0)

The component folder, integration domain, storage keys and device-event type now
use `tuya_wall_panel`. This release deliberately starts fresh; it does not migrate
the former test integration's configuration or automation triggers.

1. In HA, open Settings → Devices & services → the existing **Tuya Wall Panel**
   integration. Delete its configuration entry from the entry's three-dot menu.
   Do this **before** deleting its files, so HA can unload and remove it normally.
2. Delete the old `/config/custom_components/panalume_knob` folder. If your file
   browser calls the HA configuration directory `/homeassistant`, use
   `/homeassistant/custom_components/panalume_knob` instead. These paths refer to
   the same configuration directory in the respective tools.
3. Extract `tuya-wall-panel.zip` on your computer. Copy its **tuya_wall_panel**
   folder into `/config/custom_components/`. The result must be
   `/config/custom_components/tuya_wall_panel/manifest.json`, with no extra
   nested `custom_components` directory. Overwrite the whole new folder if it
   already exists.
4. Restart Home Assistant Core. Refresh the browser if the integration picker
   still shows cached results.
5. Add **Tuya Wall Panel** under Settings → Devices & services → Add integration.
   Keep MQTT base topic `wallpanel/px30_172_16_1_26`. MQTT itself must remain
   installed and configured.
6. Select the knob pages and configure button assignments again. Check the
   freshly created entities, and recreate any test automations that used the
   former integration's device triggers. The new event type is
   `tuya_wall_panel_event`.

The package contains only the new integration folder. No Android driver,
dashboard, firmware or MQTT broker change is needed. Android's existing service
paths are outside this HA naming change.

The old installation ZIP can also be deleted; it is not used by HA at runtime.
Old storage files are inert after removing the old integration; do not edit HA's
registry files manually.
