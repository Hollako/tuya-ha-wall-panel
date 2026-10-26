# Dashboard System UI protection - 0.11.0

## Vendor overlay correction - 0.11.1

User swipe test proved the menu still opened. Window Manager identified its visible
TYPE_APPLICATION_OVERLAY as com.sznaner.pulldownmenu, UID1000, dedicated MenuService
process. Android's status bar was correctly disabled; the separate Tuya app bypassed
it. Disabled only this package (disable-user and force-stop), preserving the original
enabled state 0 in /data/local/wallpanel/vendor-menu-original.json. No UID-wide app-op
was changed because its system UID is shared. The visible overlay window disappeared.
Installer CHECK/APPLY/RESTORE now includes this exact package; 15 provisioning and
rollback cases pass with it enabled in the factory fixture. New APK/setup kit 0.11.1.
Native hardware and intercom packages are unchanged. User retries the physical swipe.

The dashboard now leases Android status-bar restrictions through the authenticated
root broker. It disables notification/quick-settings expansion, notification alerts,
status icons and Home/Back/Recents/search controls while displaying the dashboard.
The existing top-left administrator hold and HA Open setup remain the exit paths.
Authenticated setup releases the restrictions. If navigation lock/PIN is enabled,
the lease remains held through the PIN prompt; cancellation returns it to dashboard.
Other apps/intercom can present their own windows; this does not disable SystemUI or
intercom packages, block WebView dialogs, filter arbitrary third-party windows, or
implement the future intercom module. Regular volume operations still use no UI flag.

The root-only helper uses a single Binder token and releases restrictions on socket
EOF, normal exit or Binder-process death. Partial startup failure clears its flags.
No permanent settings, device-owner enrollment, accessibility overlays, polling
collapse hack or blanket package disabling is used. The helper has fixed policy,
no shell/command parser/network and is independent of the hardware worker lifetime.

The broker is protocol 3 (setup status remains protocol 2). New fixed request:
UI_GUARD. Only the app UID may request it. The Java client retries with bounded
backoff and releases both socket directions to terminate the lease promptly.
Root-owned wallpanel-system-ui.jar is the sixth verified provisioning component.
The setup kit and APK stage/check it together; use the current bootstrap_panel.py.
Historical prepare_privilege.py stages only older components and is not the current
installer. Initial setup still requires root as documented in MIGRATION_0_10.md.

Installed APK/root support on 172.16.1.26 using --bootstrap-only. Settings and
hardware policy preserved; no Prepare/Restore/reboot/control/gesture tests by agent.
Read-only checks report protocol 3, native ownership and six MQTT statuses online.
Android reported mDisabled1=0x3ff0000, mDisabled2=0x7 with exactly one
local.wallpanel.app.dashboard record. User tests the physical downward swipe,
administrator entry, return to dashboard and reboot persistence.

Tests: real broker authentication/guard lease/release/independent worker (8 cases),
factory provisioning/recovery (15), payload integrity (5), real root helper against
fake Binder services (exact flags/token, EOF, partial failure, non-root refusal),
real client against fake socket (PIN handoff/reclaim/authenticated release), existing
Java store/config/runtime/MQTT/audio/display/screenshot regressions and signed APK.
No HA component changes are required.

Diagnostic files: app-private ui-guard-status.json, Android dumpsys statusbar,
system-ui-verification.json and app-only-verification.json. Do not claim arbitrary
third-party overlays are blocked or that user swipe/reboot tests have passed.
