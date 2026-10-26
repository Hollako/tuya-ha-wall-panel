# Main display, brightness and HA-configurable idle policy — 0.7.0

User accepted audio 0.6.0. This stage moves the main display/backlight into the same
native app worker and adds dim/off policy. Dashboard URL delivery remains explicitly
pending; screenshots/reboot remain in the legacy management bridge.

Defaults: dim at 60 seconds to raw 26 (~10%), off at 300 seconds, both counted from
the last screen interaction. Normal brightness remains a separate 1–255 setting.
Dim output is capped by normal brightness so it cannot brighten an already-low
display. Touch on a dim screen restores normal brightness and passes through; touch
on a dark screen wakes the backlight and is consumed through release. Knob/buttons
are excluded from the input allowlist and do not wake/reset main-display inactivity.
Android stays awake; no navigation, page reload, power sleep or network disconnect
is introduced by the display controller.

HA 1.10 preserves Main display and adds three configuration numbers: Dim after,
Display off after (whole minutes, 0–1440; zero disables each timer) and Dim brightness
(1–100%). A nonzero dim timer must precede a nonzero off timer. Policy changes are
atomic/persistent and non-retained, independently updating one field to avoid lost
concurrent edits. A brightness/policy change while dark does not wake it; the HA
light's explicit turn-on remains the wake action. Normal brightness is reported
separately from actual PWM/phase, avoiding slider bounce when dimming. Supplemental
display feedback overrides stale display fields from management/screenshot updates.

## Driver and ownership

`display-runtime.h` is shared by the app worker and the new `panel-backlight-v2.c`
init fallback. A request lock plus the existing `backlight.lock` ensures the old
controller releases all input grabs/descriptors before the app acquires hardware.
The passive fallback remains running and reclaims ownership when the app exits.
Disabling the app Display module deliberately returns this function to the fallback.
Elapsed touch inactivity is carried across ownership changes using root-private
temporary feedback, preserving dark/dim state without a forced-bright handover.

Kernel brightness reads on this board are `===>N`; this was verified read-only and
is handled by the shared parser. Input names are explicitly restricted to goodix-ts,
rk8xx_pwrkey, gpio_ir_recv and adc-keys. The app does not grab knod-aispeech/gpio_ring.
Settings are stored under the existing protected state directory. Feedback changes
use `/dev/wallpanel-display-state` (RAM); touch sampling does not wear flash.

Both old management and app MQTT routes understand brightness, DISPLAY_ON/OFF and
per-field display policy. The old bridge ignores these while the app holds the
request, but continues screenshots/reboot. App commands reject retained payloads,
unknown fields, malformed numbers and invalid cross-field policy. Native request
IDs prevent duplicate action replay. Readiness failures return to the working bridge.

## Deployment and validation

The backed-up installer replaces both MQTT bridge and fallback controller with a
detached recovery watchdog. Backup:
`/data/local/panalume/before-app-display-gate.20261024-235104`.
Preferences backup: `/data/local/tmp/wallpanel-app-before-0.7.0/wall_panel.xml`.
Both services were restarted for installation and normal startup/query/PID feedback
was checked. No manual brightness, dim/off, wake, audio, relay or reboot test was run
by the agent. The new app runtime remains stopped for user activation/testing.

79 isolated HA tests, 11 native fixture suites, seven dual-service recovery cases,
Java core/store/MQTT/audio/display-routing tests and actual APK signature/DEX/native
package checks passed. An additional isolated startup fixture confirms a dark
handover retains input grabs and last activity. Fresh WSL uptime is handled in that
test so it does not generate impossible negative activity timestamps.

User next: copy HA 1.10, restart HA, tap Start/apply hardware, then test normal
brightness, 1-minute dim, 5-minute total off and touch wake. Check buttons/knob still
act with main display off. Use previous hardware service remains the fallback.
