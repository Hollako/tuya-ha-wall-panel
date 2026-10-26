# App-managed knob and HA dashboard delivery — 0.5.0

The user accepted all 0.4 buttons/relay checks. This stage packages the knob input,
physical half-step GPIO observer, vendor press/hold input and auxiliary framebuffer
renderer in the same native worker supervised by the app. Existing HA pages, entity
assignments and MQTT routes remain. Main display/backlight, audio, screenshots and
reboot still use the retained bridge. No physical acceptance tests are performed by
the agent; the new app worker awaits user activation.

## Exclusive ownership

The app acquires `unified-knob-request.lock`, then waits for an exclusive
`unified-knob-resource.lock`. The legacy bridge closes evdev, framebuffer and sampler
handles before releasing its resource lock. It does not publish or accept knob
commands while yielding, including when restarted while the app already owns the
knob. OS locks are released on process death. Fallback waits for full resource cleanup,
reloads saved manual settings and asks HA for a fresh live frame via availability.

The native worker reuses shared `panel-input.h`, `panel-rotary.h`, its half-step
decoder, gestures and `panel-knob-display.h`. No GPIO output/configuration or main
framebuffer writes were added. The exact DT pin mapping and framebuffer layout remain
mandatory. Missing physical decoder readiness fails back rather than silently
returning to the old two-detents-per-event behavior. Single/double/long knob presses
are preserved, with 400 ms single/double classification.

Native commands are bounded canonical `knob FIELD HEX`, `knobmodules INPUT DISPLAY`
and `knob_status`. UTF-8 payloads are validated before changes; frames are RAM-only,
manual settings retain their original root-private persistence. No shell, arbitrary
path, generic GPIO or unknown LED endpoint is exposed. Verified LED options remain
Red/White. No new RGB/off capability is claimed. Module changes are idempotent and
allow a 15-second reconfiguration grace period. Knob frames/input readiness are
independent of relay feedback, preventing initial live frames from being dropped.

## HA 1.8 URL delivery

HA publishes validated versioned desired configuration on `/app/dashboard/set` with
QoS1/retain. The app saves only its dashboard field, advances the setup revision,
preserves credentials/identity/module flags and confirms on `/app/dashboard/state`.
Only deliberate URL changes, already nonempty saved links or explicitly managed
blank values are replayed. Unconfigured blank HA defaults never clear app setup.
HTTP/HTTPS, no embedded credentials, 255-character maximum; malformed input leaves
the prior URL unchanged. A visible dashboard listens for changes and replaces its
WebView without bringing another activity forward or waking the main backlight.
Identical URLs are idempotent and do not refresh. Idle setup forms reload saved
changes; unfinished forms retain stale-save protection. Conflicting open edits must
be resolved by reopening setup. HA reports requested versus confirmed URL and errors.

## Deployment and rollback

The knob gate installer uses the tested backed-up watchdog transaction, requires the
existing MCU gate, returns the app to legacy control first and verifies normal
startup state-query feedback and unchanged backlight PID. It does not start the app
worker or pulse any outputs. Backup is recorded in `knob-gate-install-result.json`.
Recovery uses that backup's `recover.sh` after stopping the app hardware service;
the original MCU gate is retained. The 0.4 app/native backup remains separate.

User next: copy HA 1.8 component folder and restart HA, then on the panel select
**Start / apply hardware**. Check rotation in both directions (one detent per action),
knob single/double/long press, page/menu rendering, display/LED controls, and existing
buttons/relays. Change Dashboard URL from HA while the dashboard is visible. Confirm
the entity's `applied_to_panel` attribute. **Use previous hardware service** remains
the fallback. Reboot/offline/long-idle checks are user tasks.

Validation: eight native fixture suites, six knob-gate recovery/watchdog cases,
80 core Java checks, 33 store checks, 36 MQTT/router checks, activation policy and
knob/URL tests, manifest/DEX encoding/package-hash/signature checks, 72 isolated HA
tests. No panel knob, relay, screen-off, audio or reboot tests run by the agent.
