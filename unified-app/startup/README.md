# Browser startup handover — 24 October 2026

The user confirmed that reboot opened the previous dashboard. Read-only checks
showed our HomeActivity as Android's Home handler, but `panalume_network` and
`panalume_compat` still running the old Lightning launch/compatibility controllers.

Applied the browser-only handover with `py unified-app/handover_startup.py`.
Backup: `/data/local/panalume/before-unified-home.20261024-195801`.
The old browser services are stopped. MQTT/hardware and backlight services remain
running, with their PID files unchanged. No firmware, system/init file, app preference,
relay, GPIO, audio routing or brightness setting was changed. No reboot was performed.

The original start-network/start-compat scripts are backed up. They now have a
marker-gated prefix: when `unified-home.enabled` exists, each asks init to stop its
own service rather than run Lightning. A sleeping fallback prevents a rapid restart
loop if the init stop request is delayed or rejected. Android's selected Home app
owns normal UI startup; the new app waits for connectivity and loads its saved
dashboard. This is separate from the still-pending hardware-worker migration.

The Python preflight requires our app to resolve as Home and a committed HTTP/HTTPS
dashboard with its module enabled. Scripts are staged with hash verification.
Partial-install failures restore originals and the prior browser-service states.
`py unified-app/handover_startup.py --rollback` restores the original browser hooks;
it does not restart hardware/backlight services or erase app settings.

Five local fake-service tests pass: success scope, rollback with original states,
partial failure recovery, repeat-install refusal, and boot-wrapper suppression of
the original browser. Physical boot verification is for the user: reboot and check
that the new app's dashboard opens, not Lightning, and that corner hold opens setup.
See `../home-startup-handover-result.json`. Do not reboot on the user's behalf.
