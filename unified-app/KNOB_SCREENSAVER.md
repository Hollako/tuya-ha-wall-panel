# Knob screensaver — Android 0.18.0 / HA 1.15.0

The auxiliary knob LCD shows a local clock, date, weather icon and temperature
after 15 seconds without knob input. The LED is red while idle. Turning or
pressing the knob restores the current control page and white LED immediately;
the original gesture still reaches HA. This policy never wakes the main display.

In HA, open **Tuya Wall Panel → Configure → Knob screensaver**. Select the weather
entity, 12/24-hour format and whether to show the screensaver. Leave weather empty
for clock/date only. Disable the screensaver to restore the 15-second LCD-off
policy. **Knob activity mode** must be on for either automatic idle policy.

Clock and Weather no longer appear among selectable control pages. Existing
weather selections supply the initial screensaver weather choice. Saving control
entities preserves this migrated choice; clearing weather explicitly is honored.
Light, Climate, Cover and Media Player selections and page order remain supported.

The native worker owns the idle timer and renders directly to the verified
240×240 auxiliary framebuffer using the existing mirror correction. Built-in
graphics cover HA's weather conditions; no downloaded images, browser, emoji font
or second rendering process is needed. The native clock uses the panel's system
time plus HA's UTC offset. The offset follows HA time-zone/DST changes while
connected. Offline operation keeps the last weather and offset; weather is
last-known information until HA reconnects. With no weather data, a dash replaces
the icon and temperature.

HA sends a non-retained, validated `knob/display/screensaver/set` configuration:
`UTC_offset_seconds\n12h_or_24h\ncondition\ntemperature`, or `OFF`. The exact MQTT
route is allowlisted through Java and the native private pipe. The worker caches
changed configuration atomically in a root-only file. Minute ticks and input
events do not write settings. Weather/configuration updates do not extend the
activity timer or replace the saved control frame.

Deployment needs the APK **and** updated root-owned native worker. APK extraction
alone does not replace `/data/local/wallpanel/wallpanel-core`; use preparation or
the backed-up `sync_root_worker.py` helper. Copy the HA component update manually.

Host checks cover idle boundaries, preserving first gestures, all weather icon
paths, cached configuration, optional LCD-off mode, input validation, integration
selection migration, cleared weather, HA time-zone data and non-retained routing.
Physical appearance and knob interaction are tested by the user after deployment.

Android 0.18.1 increases time/date type, centers each weather icon below the date, and separates the temperature band to prevent overlap. No HA integration update is required for this layout-only adjustment.

Android 0.18.2 / HA 1.15.1 adds the **Knob edge circle** switch. It hides/shows the outline on both control pages and the screensaver, preserves the selected accent color, and remembers the visibility on the panel after restart. Existing panels default to showing the outline.

Android 0.19.1 shifts the entire screensaver content down six pixels, preserving its order, font sizes and spacing. The edge circle remains fixed. No HA update is needed for this visual adjustment.

Android 0.20.0 / HA 1.17.0 adds a red muted-speaker overlay: the screensaver reflects the local AudioEngine mute state; media pages reflect the selected player. Local feedback stays local; remote media frames carry a validated fourth ON/OFF line on media_frame/set. Normal frames clear the media icon. Neither mute feedback nor icon changes extend the activity timer or write settings. The local speaker mute route is internal to the app/native pipe and is not exposed as an MQTT control.

Android 0.20.1 enlarges both mute symbols to roughly 29 pixels. Media pages omit the textual Muted label, retaining a track title where present. The renderer also accepts the previous HA mute text format and removes that label locally, so this refinement needs no HA update.

Android 0.20.2 moves the media mute symbol to the former Muted status-line position (center y=180), clearing the volume percentage. A track title, when present, is drawn below the symbol while muted. The screensaver mute icon is unchanged. No HA update is needed.
