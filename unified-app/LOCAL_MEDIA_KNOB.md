# Media knob control — Android 0.19.0 / HA 1.16.0

On media pages, rotation adjusts volume, single press toggles mute, hold toggles
play/pause, and double press advances to the next entity. Hold retains its menu
behavior on other entity types and cancels an already-open selection menu. A
physical button assigned to **Knob: open / cancel selection menu** can open that
menu from a media page. Play/pause and mute require the corresponding features on
remote HA entities; an unsupported action does not substitute a different action.

HA identifies **this integration entry's** own Audio media player by its registry
unique ID. It does not guess from the friendly name, entity ID spelling or IP.
Renaming Audio therefore still permits local routing. Selecting another panel's
Audio entity follows the ordinary remote HA path.

Once the app advertises local-media support, HA sends the selected page context
and configured volume step to `knob/media/set` (versioned JSON, non-retained).
Opening the menu, selecting another entity, or switching off knob entity control
disables local routing. The app saves the current route so local volume, mute and
hold remain available when HA/Wi-Fi is absent, including after app restart. Page
selection/navigation remains HA-owned. Double press has no local audio action and
does not clear the cached route until HA actually changes the page.

The app consumes local media gestures in its existing AudioEngine on the Android
main thread. It reads the actual VolumePolicy snapshot and moves at least one
hardware volume step per detent; bounds, zero-volume/unmute behavior and intercom
priority use the same policy as HA audio commands. Hold controls the built-in URL
player or forwards Android's play/pause media key for an external Android player.
Feedback for an external player's track/state remains subject to the existing
Android audio integration; this change does not add a media-session listener.

AudioEngine reports update the knob frame locally and continue reporting the
existing Audio state to HA. HA-originated knob frames are suppressed only while
the local page is active, avoiding stale state overwriting immediate feedback.
The native screensaver/activity timer and main-display policy are unchanged.
Frame commands are deduplicated and go through the existing bounded worker pipe.

Published knob events retain their normal type and include `local_handled: true`
when the app consumes a media action. HA's controller skips executing that action
again; event entities and user automations still receive the gesture. Native
feedback publications use a bounded background queue so a stalled MQTT writer
cannot hold the input reader/bridge lock. Expired events and work associated with
an old connection are dropped; audio state resynchronizes after reconnect.

Host checks cover mute/hold mapping, capability guards, exact own-entity registry
resolution, local route selection/menu/disable transitions, duplicate prevention,
hardware volume quantization/bounds, safe display frames and existing volume
behavior. APK and integration must both be updated. Installation preserves saved
settings; physical knob/audio tests are left to the user.
