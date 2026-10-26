# Active work: packaged panel setup, 0.10

User explicitly requested implementing (1) root privilege service/corrected boot,
(2) hardware permissions/configuration, (3) disabled conflicting Tuya/legacy startup.
Working 0.9 panel user accepted reboot, HA online, Open Setup and HA URL changes.
Do not regress URL or perform agent reboot, relay/audio/display/capture tests.
No subagents. HA deployment remains user copying. Current root panel 172.16.1.26;
never touch TV 172.16.1.11. All project sources in this workspace.

Constraint: actual Android app stock su fails setgid. Cannot silently grant root.
Deliver automatic APK setup if functional app su, plus a portable one-time ADB
bootstrap (explicit target, no hardcoded host) for this firmware. After bootstrap,
app invokes fixed CHECK/APPLY/RESTORE through UID-restricted service. Must say this
honestly; no claim sideload alone provisions root on arbitrary stock panels.

Implementation plan:
- Native --probe: read-only exact G10 DT + UART/profile capability report; unsupported
  variants are not auto-disabled. Existing profiles remain modular.
- Packaged root-owned setup-panel.sh (check/apply/restore), profile preflight,
  private permissions/defaults only, corrected system-shell boot wrapper, exact known
  package/service list, idempotence, backups, detached recovery watchdog, no device
  writes/tests/reboot or blanket SELinux/777 changes. Preserve settings.
- Root bootstrap script copies signed-APK extracted assets as root, installs setup
  helper and new protocol-2 broker. Does not rely on old gate markers/legacy binaries.
- Broker allows SETUP CHECK/APPLY/RESTORE only to app UID, fixed root-owned script,
  no client shell/path; reject mutations while worker/capture active. Existing WORKER
  and CAPTURE behavior preserved. One bounded setup child.
- Android Panel Setup screen (admin-gated entry from existing MainActivity), check,
  apply, restore, clear supported/root/boot/conflict status, root bootstrap guidance,
  no auto apply on launch. Stop/resume hardware around setup, never erase form drafts.
- Portable Python bootstrap CLI + release kit extracts only allowlisted assets from
  provided APK, verifies hashes, explicit ADB target, root preflight, no credentials,
  one command replaces all previous manual provisioning. UI root-su route uses same
  packaged bootstrap when a real app-context root probe succeeds.
- Meaningful fake root/service provisioning and broker auth/mutation tests, APK
  build/integrity and existing regressions. Existing panel install only normal
  startup/read-only checks; let user exercise apply/restore/reboot.

Status: implemented and packaged in 0.10.0; see MIGRATION_0_10.md. The actual panel
was upgraded using --bootstrap-only and its app-context CHECK passed. Prepare,
Restore and reboot on the live panel remain user tests. Automated fixture tests
exercise initial setup and rollback/recovery without physical hardware.
New setup needs no HA integration changes. Current canonical HA folder moved by
user; extracted copy ha-knob/tuya-wall-panel/custom_components/tuya_wall_panel is1.12.
