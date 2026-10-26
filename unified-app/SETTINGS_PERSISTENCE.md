# Saved setup investigation and fix — 24 October 2026

The user reported missing setup after 0.3.1. Read-only inspection found the same app
UID 10065 and existing `wall_panel.xml`, last modified at 19:19, before the 19:31:31
update. It contained an existing identity and blank/default form values, empty module
selection and no dashboard, broker or credentials. No Android `.bak` was present.
Only field presence/lengths were printed; credential and URL values were not logged.
See `settings-presence-report.json`.

The file was not deleted by the update. The changes visible in the user's later
photo had not reached committed preferences. It is not established whether Save was
not pressed or validation failed. Do not blame the user or claim that previously
committed data was demonstrably erased. Uncommitted entries cannot be reconstructed
from this saved file; do not invent their previous values.

## Version 0.3.2

- Save settings, Save and open dashboard, status and validation errors are pinned
  above the scrollable form. Successful saves also show a confirmation.
- Unfinished entries are recorded after a 750 ms debounce and when leaving setup,
  as an AES-GCM encrypted draft using the app's existing Android Keystore identity.
  Drafts preserve partial/invalid field text without applying it to runtime settings.
  Passwords remain protected; a new PIN is explicitly excluded from drafts.
- Setup restores a matching draft after reopening, process restart or package update.
  Drafts remain best-effort until the visible draft-saved message; force-killing
  immediately after a keystroke can still beat the debounce.
- Explicit Save validates all fields, commits the configuration and revision, and
  clears the draft. Old preferences without a revision migrate in place as revision
  zero. Existing identity, credentials, module choices and settings are retained.
- Stale drafts cannot replace a newer commit, and an older open form cannot commit
  over a newer setup. Module/relay activation still requires its separate handover.
- Keystore or commit errors remain visible and never cause a plaintext fallback.

Validation: 66 existing Java core checks plus 25 production-ConfigStore checks using
fake Android preference/Keystore facades. These cover old configuration/identity,
encrypted draft restart recovery, explicit apply, stale-form rejection and failed
encryption/commit handling. The fake Keystore is not an Android cryptography test;
real Keystore and UI lifecycle checks remain user acceptance work. APK compilation,
Unicode DEX checks and local signature verification are performed before update.

The user explicitly confirmed they were not re-entering settings and authorized the
0.3.2 update. Back up the current private preference file before installing. Do not
trigger relay, audio, reboot or other physical tests; user checks the new UI.
