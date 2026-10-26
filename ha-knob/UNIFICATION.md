# Tuya Wall Panel integration consolidation

Prepared 24 October 2026 for KSA HA 172.16.1.22 (Core 2026.10.0)
and G10 panel 172.16.1.26 only.

## Package and migration

- Package: `tuya-wall-panel.zip`, integration version 1.4.2.
- SHA256: `f680cda0a61831830e70b2e8d40ef6515a44b0e7bf707ad0dff5fad8fb1d00fa`.
- The archive filename and visible labels use Tuya Wall Panel. The existing
  internal `panalume_knob` domain/storage keys and entity IDs remain compatible.
- 16 MQTT entities move into the existing custom integration alongside its 12
  entities. Keep controller device ID `66cb9625f498c240d4f391759c3221e5`.
- Preserve every entity registry ID, entity ID, unique ID and user customization.
- Configuration includes the existing eight pages, Clock, button actions and
  existing gesture timing. No user action mappings are reset by migration.
- The old MQTT device ID had no references in the checked YAML/dashboard files.
- Use public HA registry migration APIs during a cold setup. Refuse hot migration
  when any legacy entity is loaded, and refuse unrelated entities on the device.
- Add device automation triggers for single/double presses on four buttons and
  rotation/single/double/long press on the knob. Reject retained/offline events.

## Panel scope

Only `/data/local/panalume/mqtt-discovery.jsonl` is suspended (empty file).
The original is backed up on the panel as `mqtt-discovery.before-unification.jsonl`
and locally with that name. Its 33 retained discovery topics are cleared from
the KSA broker while Core is stopped. No other discovery topic is touched.

All four driver binaries and saved knob/button/brightness settings were checked
unchanged. No panel reboot, Wi-Fi operation, audio playback or relay command
was performed. MQTT remains the authenticated local transport.

## Backups and validation

HA backup directory: `/homeassistant/panel-backups/wall-panel-before-unification/`.
It contains the previous component, entity/device registries, panel config-entry
snapshot and controller storage. Permissions restrict access to its owner.
The runtime migration also saves a panel-specific pre-migration registry audit.

- 63 isolated controller/audio/management/hardware/migration tests passed.
- Python source compilation passed.
- `ha core check` passed after staging the new component.
- Discovery migration report: `panel-172.16.1.26/ha-unification-discovery-result.json`.
- Initial 1.4.0 migration verified in HA UI: one device, 28 entities. Final 1.4.2 verification passed: 28 total panel registry entries, one native integration/device, zero legacy MQTT devices; all identity and user-customization comparisons passed.

## Core stop/start handling

Stopping Core disconnected HA's ingress-based Terminal; external SSH is not
available. User was asked to run `ha core start` at the host/VM console.
For future maintenance, queue automatic start/recovery in the same remote shell
before stopping Core, rather than depending on its ingress connection remaining up.

## Rollback if needed

Stop Core with a restart/restore command already queued, or use its host console.
Restore the previous component from `component/` and the saved entity/device
registries from the HA backup directory while Core is stopped. Restore controller
storage if necessary; keep any later user edits rather than overwriting them.
Run `panel-172.16.1.26/unify_ha_discovery.py --restore` from this workspace to
restore only the panel's discovery file and its retained discovery records.
Start Core and verify the original 16 MQTT / 12 custom-integration entities.
Never restore whole registries over newer unrelated changes without review.

The user performs final live hardware tests after installation is verified.

## Discovery replay protection (1.4.1)

After host startup the broker replayed 30 old retained discovery payloads, creating
16 extra MQTT entities alongside the correctly migrated originals. Version 1.4.1
retires exactly the panel's 33 legacy discovery topics using HA's MQTT connection
after setup and panel reconnect. Existing native registry rows always take
precedence over MQTT duplicates. No actuator topics are used by this cleanup.
The managed ha core restart queues both stop and start through Supervisor.

Version 1.4.2 additionally removes registry-only MQTT leftovers when an exact native
counterpart is present under this config entry. Loaded MQTT entities are left
untouched until their discovery removal unloads them. Unmigrated entities and
other panel identifiers are never removed. Initial registry comparison found
28 native entities plus 16 unused MQTT copies; config options were unchanged.

## Final result

HA Core is running. The full-registry comparison (including MQTT rows) and config-entry comparison passed: before 28, after 28, all IDs/unique IDs/user customizations preserved, config/options unchanged, legacy MQTT device count 0. The transport check passed: all four panel services online, both relays OFF, no panel discovery records retained, and driver/settings checksums unchanged.
