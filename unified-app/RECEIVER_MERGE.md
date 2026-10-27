# Receiver merge - 0.23.0 / HA 1.18.0

This is an offline-built pilot. Neither panel was installed, stopped, rebooted or
tested by the merge work. The A133 knob issue was resolved by the user's power
cycle; no further knob diagnostics or framebuffer writes are needed.

## Changes

- Single receiver Java source set shared with dedicated Audio Receiver 1.2.0.
- Single SIP protocol core extracted without protocol changes.
- Both receiver native ABIs pinned and included in the unified APK. Existing root
  timing helper remains a fixed, UID-authenticated service; no generic root API.
- Setup > Audio receiver settings: enable, receiver name, trim, live timing offset
  and optional interface. Disabled by default. Existing HA/SIP setup stays intact.
- HA AirPlay receiver switch, timing offset (-500..500 ms) and trim controls replace
  the dedicated receiver's mobile web controls. Timing/trim update live and do not
  restart the engine; changing name/interface restarts only this receiver.
- Receiver sessions select Android playback routing and the existing amplifier.
  Knob/system media volume and mute stay local; HA sees AirPlay source and title,
  artist/album through the same Audio media entity. Older APKs keep two sources.
- Call leases silence current/new receiver AudioTracks before ringing and restore
  the prior output selection afterward. Receiver clocks and PCM transport continue.
- Receiver default network follows HA assignment; explicit interface overrides it.
  Interface/IP loss/change restarts only the receiver, not SIP/HA/WebView bindings.
- Shared service waits for local IPv4 and retries failed native transport startup
  with bounded backoff. Existing standalone private preferences/signing remain.

## Safe handoff on an existing supported receiver installation

First install the merged APK on the intended authorized panel. Do not install it
on client panel .35. Do not cut over A133 as a full hardware pilot: its direct
hardware support is still unverified, independently of receiver ARM32 support.

`receiver_cutover.py --device PANEL_IP:5555` only inspects a plan. Add `--apply`
when ready for the brief app interruption. It checks trusted root, existing PTP
init/helper hashes, separate valid UIDs and idle SIP. It backs up the root wrapper
and both receiver preference files in a protected device directory, disables the
standalone without uninstalling it, changes the fixed helper's authorized UID,
imports only receiver name/calibration/port/interface, and opens unified Home.
HA/SIP credentials, Bluetooth bonds, hardware mappings and system partitions are
not imported or rewritten. Failed cutover restores the saved receiver ownership.

Retain its printed backup path. `--rollback BACKUP_PATH --apply` restores the
original wrapper/UID/preferences and package state. It leaves all unrelated
unified configuration intact. No reboot, playback or call tests are automated.
The old A133 launcher shim may stay present but cannot launch its disabled package;
retire it only after acceptance. Existing PTP init continues using its wrapper.

Fresh-device AirPlay helper provisioning is separate; the existing full G10
hardware installer alone does not prepare it. This pilot refuses unknown helper
builds instead of overwriting them. A future provisioning release must integrate
that preparation per verified hardware/firmware profile.

## HA update and user acceptance

Copy `custom_components/tuya_wall_panel` from `ha-knob/tuya-wall-panel` to HA and
restart HA, or use the supplied integration ZIP. No HA deployment was performed.

User tests after cutover: select receiver from iPad; check name/calibration,
stereo sound, volume/mute and knob feedback; receive/answer/end calls while music
plays; verify silence during ringing/talking and synchronized output afterward;
check Wi-Fi rejoin, display off, soft reboot, hard power cycle and multiroom
endurance. Local/fake-backend tests cannot establish real acoustic synchronization
or firmware audio-route behavior. Keep the standalone rollback until these pass.

Bluetooth G10 glitches, firmware A2DP focus control, source-side AirPlay transport
commands and new-model hardware support remain outside the completed merge.
Nothing is pushed to GitHub.


## 2026-10-27 - Product deployment completed

G10: unified0.23.0, receiver UID10065. Old standalone receiver fully uninstalled
after verifying the new engine. Protected old APK/settings backup:
/data/local/wpar-airplay/before-unified-20261027-220922.
A133: dedicated Audio Receiver1.2.0/code15, unchanged UID10092. Unified app absent.
Each panel has one Shairport/nqptp engine, expected UID, zero app capabilities and
matching native hashes. Settings preserved; A133 helper/wrapper unchanged.
G10 SIP registered and IDLE. No agent playback, calls or reboot. HA1.18 files
provided for user copy, not deployed. No GitHub push.

First G10 handoff rolled back safely due to default-Home restart racing the staged
import. Corrected cold-start ordering and retired pending imports before rollback
components launch. Retry succeeded. Rollback can reinstall the protected old APK
after uninstall and derives its actual new UID. Seven simulated rollback tests
pass. Setup kit refreshed. User playback/multiroom and SIP acceptance pending.
