# Unified app architecture and migration

## Ownership

The Android app owns setup, dashboard UI, administration, module configuration and
eventual transport coordination. Model adapters own hardware interfaces. Keep the
validated native G10 implementation rather than reimplement its UART/GPIO timing in
Java. A single privileged worker owns each interface; an Android package installation
alone does not grant root, replace firmware or establish hardware compatibility.

The old G10 hardware fallback services are retired. The user authorized app/support
installation; reboot and physical hardware tests remain with the user.
HA integration files are delivered to the user for copying; the user performs physical
tests. Do not change the TV at 172.16.1.11 as part of this project.

## Current boundaries

Current 0.10.0: fixed CHECK/APPLY/RESTORE operations extend the UID-authenticated
root broker. The APK carries verified setup scripts and native payloads. A private
Panel installation activity manages setup after bootstrap; actual app-context root
can perform the bootstrap, otherwise the portable explicit-target ADB kit is required.
Preparation is limited to the verified G10 layout, preserves configuration, snapshots
known state and journals recovery. It has no arbitrary shell/path API. Current panel
received support upgrade only, with read-only app-context and runtime checks; no
physical Prepare/Restore/reboot tests. See MIGRATION_0_10.md.

Previous 0.9.0: independent foreground app/boot receiver and canonical MQTT LWT; old
fallback services removed from boot and stopped. Actual app su is blocked, so one
UID-authenticated local-socket privilege bootstrap launches the app-packaged native
worker/capture only. It has no independent hardware policy, network or shell API.
Root-owned copies are staged through the provisioner; this remains a required setup
step on a new panel. Real native ownership and MQTT readiness are now verified.
HA 1.12 is user-deployed. User reboot/I/O acceptance remains pending. Historical
acceptance alone did not prove app ownership while fallbacks remained available.
See `MIGRATION_0_9.md` for recovery and current boundaries.

Checkpoint: 0.8.0 screenshots/reboot are installed for user activation and acceptance.
0.7 display/brightness/dim/off were accepted. The app owns capture scheduling (300s),
isolated binary PNG upload and explicit graceful reboot via its native worker.
HA 1.11 separates management/display ownership and avoids duplicate screenshot jobs.
Legacy services remain fallback; old MQTT still supplies global availability, so
service retirement remains separate. Dashboard URL delivery is pending. See
`MIGRATION_0_8.md`. No agent physical screenshot/reboot tests were performed.

Previous checkpoint: 0.7.0 display/brightness and configurable dim/off were installed for user
acceptance. Audio and knob were accepted by the user. Shared display runtime and
request/resource handover preserve instant wake and safe dark-touch consumption.
HA 1.10 exposes timers/level and keeps normal brightness separate from actual dim
PWM. Screenshots/reboot still use legacy management; URL delivery is pending.

Previous checkpoint: 0.6.0 audio migration was installed. Knob was accepted
by the user; URL delivery is still failing and deferred. Android audio now runs inside
the app foreground service, with root trim/power/preference operations in its worker.
The standalone audio APK is retained for automatic fallback, not used during app
ownership. See `MIGRATION_0_6.md`.

Previous checkpoint: 0.5.0 was installed. The user
accepted all 0.4 buttons/relay tests. Knob input/renderer share the app worker and
have a separate request/resource ownership handshake. HA 1.8 desired dashboard URL
delivery and confirmation are implemented. See `MIGRATION_0_5.md`.

Previous checkpoint: 0.4.0 was installed. Home/dashboard routing and saved setup were confirmed
by the user. The old browser startup was retired separately while hardware helpers
remained active. The new buttons/relays worker, foreground supervisor and MQTT runtime
are packaged and opt-in. The old bridge has a cooperative UART lease gate with a
protected backup and automatic failure recovery. No app-worker takeover or physical
acceptance tests were performed by the agent. See `MIGRATION_0_4.md` and
`native/PROTOCOL.md`.

| Layer | Current owner | Unified app status |
| --- | --- | --- |
| Local setup and module selections | New app | Implemented; on-device verification pending |
| Dashboard | App HomeActivity | User verified after startup handover |
| HA/MQTT runtime | Native bridge plus optional app service | App transport implemented; 0.4 accepted by user |
| Four buttons, two relays | App worker when enabled | User accepted 0.4; cooperative fallback retained |
| Knob input | Same app worker when enabled | Half-step GPIO and press/hold input migrated; user accepted |
| Knob framebuffer | Same app worker when enabled | Shared mirrored RGB565 renderer; user accepted |
| Display/backlight/idle/wake | Same app worker with init fallback | 0.7 implemented; configurable local dim/off; user checks pending |
| Audio, routing, amplifier | App AudioEngine + native worker when enabled | 0.6 accepted by user; old APK retained for fallback |
| Reboot and screenshots | Native bridge | Preserve 300-second screenshot interval; migration pending |
| Startup and connectivity | App Home plus retained hardware init | Browser handover done; optional service resumes when enabled |
| SIP and receiver modules | Separate experiments/plans | Off; not implemented here |

## Migration gates

1. **Foundation (0.1.0):** standalone local configuration, dashboard viewer, PIN gate,
   conservative capability model and non-actuating MQTT login test. Local build and
   core tests complete. No live installation.
2. **Portable native runtime:** replace fixed per-device MQTT identity, host/topic,
   paths and dashboard assumptions with a versioned validated configuration. Use a
   per-install stable device ID and unique MQTT clients. Test translation against
   existing HA topics without generating duplicate entities or replaying commands.
3. **Privileged adapter supervisor:** define a small versioned command/status interface
   with bounded input and explicitly enumerated operations; no arbitrary shell endpoint.
   Detect the exact G10 layout read-only, verify privilege availability, package native
   binaries and their hashes, and refuse unknown layouts. Stop the previous owner before
   acquiring UART/GPIO/display resources. Failed activation restores the prior owner.
   Do not assume that root ADB implies a normal APK can obtain root automatically.
4. **Feature migration:** move one module at a time: inputs and relays, knob rendering,
   display/idle/wake, audio/routing, screenshot and reboot. Preserve no-wake hardware
   keys, 400 ms single/double classification, one rotary event per detent and direction,
   no fabricated button hold, no relay replay, zero-volume/unmute semantics and no volume
   overlay. Model unavailable hardware explicitly. Module disable must release owned
   workers while retaining preferences and shared dependencies.
5. **HA and dashboard management:** runtime MQTT, entity state/availability, Dashboard
   URL + explicit Apply acknowledgement, module configuration, allowed app shortcuts
   and administrator unlock through the unified integration. Do not accept retained
   actuator/unlock/reboot commands. Keep boot values locally without replaying actions.
6. **Startup and kiosk:** wait for connectivity, recover only failed pages, replace the
   current guardian after validated handover, provide recovery/unlock without HA, and
   test device-level Home/Recents/Back restrictions. No Tuya package dependency; preserve
   required Android/kernel drivers and audit remaining vendor services individually.
7. **User acceptance and reuse:** user checks cold boot, prolonged backlight-off/wake,
   offline relay buttons, reconnect, all inputs, audio/routing and administrator recovery.
   Verify RAM and stability before removing fallback components. Additional panel models
   require separate verified profiles; a manual module toggle never proves hardware.

## Configuration and transport rules

Keep supported, selected, verified and currently available separate. Lost MQTT does
not mean missing hardware. Default unknown hardware to inactive; unavailable modules
must not create functional-looking HA entities. Preserve stable identities across
module disable/re-enable. Separate settings from commands, validate before persistence,
acknowledge applied values, and never navigate on each URL keystroke.

Use the existing authenticated MQTT architecture for remote management; no embedded
web server. The foundation's probe publishes nothing and does not compete with the
working native bridge. Firmware and WebView updates are separate explicit procedures,
not silent side effects of installing the APK.
