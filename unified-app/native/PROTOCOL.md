# G10 worker protocol v1 — local, not activated

`wallpanel-core` is a standalone ARM64 executable using the validated G10 transport,
frame parser and exact device-tree guard. It reuses the existing 400 ms completed-click
classifier. There is no MQTT client, network listener, web server, broker address,
credential store, shell-command API, arbitrary-frame API or Tuya Java dependency.

This is a partial hardware migration: buttons and relays only. Knob, display, audio,
screenshots and reboot remain with the existing installation. **No live activation or
handover is included.** Current app 0.1.0 stays installed; candidate 0.2.0 contains
the worker as an inactive asset. No app code extracts, starts or grants it root.

## Configuration and identity

`--describe` reports the adapter's static capability contract without hardware access.
`profile_verified:false` means this description is not device detection. `--check-config
FILE` validates only a regular, current-owner-only file; it does not open UART/GPIO.
Only `--run FILE` can operate hardware, requires root, and refuses an unknown layout
or occupied/unverifiable UART. It never disables or kills another owner.

The UTF-8/ASCII line configuration is versioned and limited to 4096 bytes. See
`example-g10.conf`. Required: `version=1`, `profile=g10`, `device_id` (1–48 lowercase
letters/digits/underscores), `buttons=0|1`, `relays=0|1`. All lines end in newline.
Unknown or duplicate keys, symlinks, unsafe ownership/modes and malformed values are
rejected before hardware access. Parsing is atomic. Optional `button_1_single` through
`button_4_double` each accept 0 (none), 1 (relay 1), or 2 (relay 2). Saved bindings do
not activate if their button/relay modules are disabled.

The app now creates a persistent random `wp_…` identity, independent of IP or MAC.
`NativeSettings.render()` produces this hardware-only configuration, excluding HA and
MQTT credentials. A future privileged supervisor must validate/copy it to a root-owned
private location. Do not pass an app-owned file directly to a root worker expecting
its ownership check to be bypassed.

## Private pipe commands

The future supervisor supplies stdin/stdout pipes. Every command is one exact ASCII
line, at most 191 characters before newline:

```
v1 1 status
v1 2 relay 1 1
v1 3 relay 2 0
```

Positive request IDs must increase within a worker lifetime (maximum 2147483647).
Duplicates/older IDs never repeat writes, including after reconnect. Invalid numbers,
extra tokens, noncanonical formatting and unknown operations are rejected. Invalid
framing closes the session. There is no toggle, LED, factory-default, reboot, shell
or relay-restore command. This worker receives no MQTT messages; the future app must
reject retained actuator commands before translating them to this interface.

Stdout is versioned JSON Lines: `status`, `button`, `result`, and `error`. Unknown relay
state is -1. `result.ok:true, confirmed:false` acknowledges transmission only; actual
relay state comes from MCU feedback. Status contains selected-module flags; relay
values in a disabled module must not produce enabled HA controls. `button` includes
the physical button 1–4, `press` or `double_press`, original module ID, and local
binding target. The app must avoid also executing an HA scene for a gesture assigned
to a local relay; `local_binding` describes the assignment, not proof of actuation.

Local relay assignments run inside the worker and need no network. Rapid local
toggles track a short-lived pending target rather than toggling stale feedback.
Disconnect/stale feedback cancels pending gestures and targets. Commands require
fresh state from both relays; feedback older than 45 seconds is unavailable. Ignore
possible TX echo until a follow-up query at least 500 ms after a write.

On UART open/reconnect, only the four verified scene-mode settings and state query
are sent. Periodic queries occur every 20 seconds. Relays are never restored/replayed.
Scene mode prevents factory direct-relay bindings from competing with selected local
assignments, including when button reporting is disabled. Button hold/down/up support
is not fabricated. No command attempts independent LED color/off control.

On shutdown, pipe loss, signal or output congestion, release UART, restore its original
termios/direction and cancel pending work. Relay outputs remain as last physically set;
shutdown does not force them OFF. A later supervisor must serialize handover, monitor
health, and restore the previous owner on failure before this can replace the current
bridge. Root installation/privilege acquisition and this handover are still pending.

## Local verification

`test-core.c` uses fake UART/profile/output with the actual production state machine.
It covers unsafe/duplicate configuration, profile and ownership refusal, disabled
modules, four buttons, single/double classification, command rejection/deduplication,
state-feedback/echo gating, offline bindings, reconnect without relay replay, and
stale/failure cancellation. `test_cli.py` runs only description/config checks on a
host build, checks Java-to-C settings compatibility, and verifies the APK asset hash.
No physical panel test has been performed for this new worker.
