# Local knob activity mode (app 0.17.0 / integration 1.14.0)

Enabled by default. Turning or pressing the physical knob wakes **only its small
240x240 display** and changes its LED to white. After 15 seconds without another
knob event, the knob display turns off and the LED becomes red. Each input extends
the deadline; the normal single/double/long-press actions remain intact.

The native hardware worker owns the monotonic timer, so the behavior works without
HA or Wi-Fi. Background frame updates, clock changes and main-display power do not
wake the knob or reset its countdown. Activity and expiry only update RAM and the
verified auxiliary LCD/LED controls; they never rewrite saved settings per tick.

The HA **Knob activity mode** switch enables/disables this policy and persists that
choice locally. While enabled, it takes precedence over the saved manual knob
power, follow-main and LED settings. Disable it to restore those settings. Knob
power/LED state reports show the automatic output. The main screen's brightness,
dim/off timers, touch wake, and button behavior are unchanged.

Restarting the worker starts with the knob idle (LCD off, red LED), rather than
replaying a previous activity event. Disabling the knob display module prevents
policy output. The separate knob input module must be enabled to detect use.

The privilege broker launches `/data/local/wallpanel/wallpanel-core`, not the
copy unpacked into the app's private directory. APK-only updates do not replace
that root-owned file. Use panel preparation or the backed-up `sync_root_worker.py
--device PANEL_IP:5555` helper when updating native code. Verification must hash
`/proc/<actual-worker-pid>/exe`, not just app assets.
