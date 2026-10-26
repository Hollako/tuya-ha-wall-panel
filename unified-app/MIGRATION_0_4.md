# App-managed buttons/relays — 0.4.0

Installed candidate; user physical checks are next. No relay switching, hardware key
presses, audio playback or reboot tests were performed by the agent.

## What moved

The APK now owns an optional foreground service, bounded MQTT 3.1.1 transport,
packaged G10 native worker and its lifetime. It starts through the panel's existing
`su` with fixed arguments and an app-private packaged script. Read-only checks showed
ARM64, permissive SELinux, and successful UID10065-to-root execution of `id`. Root
ADB alone was not treated as proof of app privilege. Other profiles stay unsupported.

The setup screen has **Start / apply buttons and relays**, a runtime status line and
**Use previous hardware service**. Starting saves/validates setup first. It uses a
private encrypted broker password and the existing panel MQTT base, imported once
from a protected topic-only bootstrap file. No broker credentials are passed through
process arguments. Changed connections/profiles require stopping this runtime first;
module flags can be updated on the running worker without restarting it. Disabled
modules do not act, and with both disabled no UART is opened.

The first handover must become hardware-ready and MQTT-connected within 15 seconds,
or it releases control and returns to the previous bridge. A successful session is
remembered so a verified subsequent boot can keep local relay bindings working even
with HA/Wi-Fi unavailable. Explicit stop clears this verification, so changed broker
credentials must be verified again before a fresh takeover.

## Compatibility gate and fallback

The old process also owns knob/display/audio, so stopping it wholesale would break
those features. Its MCU backend now checks an OS file lock at
`/data/local/panalume/unified-mcu.lock`. The app's native worker acquires that lock;
the old process releases only its UART and stops publishing/accepting MCU commands.
Knob and other helpers remain running. Kernel release of the lock after worker
exit/pipe loss/process death lets the old process reclaim UART, reload current local
bindings and publish fresh state. No relay state is restored or replayed.

The gate was deployed with `install_mcu_gate.py`, a detached recovery watchdog and
automatic rollback on failed health checks. Normal startup state-query feedback was
confirmed; the new app worker was not started by deployment. The existing backlight
PID stayed unchanged. Backup: `/data/local/panalume/before-app-mcu-gate.20261024-204953`.
Rollback is `py unified-app/install_mcu_gate.py --rollback`; it first stops the app
hardware service, checks the lease is free, then restores the previous binary.

## Behavior preserved

- Four separate button streams; exclusive single/double actions use 400 ms.
- No fabricated button hold/down signal and no hardware-button display wake.
- Two relay channels, exact ON/OFF, state feedback and delayed echo rejection.
- Existing local single/double relay mappings imported from shared root state,
  applied offline and saved atomically for both app and legacy fallback.
- Retained commands and QoS1 duplicates cannot repeat hardware actions. Private
  app availability/LWT never overwrites the remaining bridge's availability.
- Bounded native request queue, stale requests/events dropped, no offline event queue,
  finite socket/write timeouts, no arbitrary shell/frame or relay-restore endpoint.

HA integration 1.7 adds **Open setup** over this runtime (PIN stays enforced) and
module capability availability. The user copies `ha-knob/tuya-wall-panel.zip`, replaces
the existing component folder and restarts HA. Keep the integration entry. Dashboard
URL is still an HA-side copy/edit field; automatic delivery is not implemented here.

## User checks

Open app setup, tap **Start / apply buttons and relays**, and wait for
**App hardware and MQTT connected**. Check all four buttons and their existing HA
assignments, then relay control from HA. Confirm the knob still works and the main
display does not wake on button presses. Check offline local assignments only if
configured; **Use previous hardware service** provides the immediate fallback.
Reboot/long-idle checks are user tasks after basic functions pass.

Local validation: 80 Java core/configuration checks, 25 settings-store checks,
36 fake-broker/router checks, six first-activation/offline policy cases, six gate
installer/recovery tests, native lease/death/reclaim/input/relay regressions, and
70 isolated HA integration tests. Existing seven native installer regressions also
passed. Android service/native-process behavior is awaiting user acceptance.
