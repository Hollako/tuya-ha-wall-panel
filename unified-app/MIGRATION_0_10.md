# Packaged panel installation - 0.10.0

The app now packages the root service, corrected system-shell boot wrapper,
hardware configuration policy and retirement of the known conflicting G10 Tuya
packages and old project services. Settings > Panel installation offers a read-only
check, Prepare / repair, and Restore previous setup. It does not apply on opening.
No HA integration update is needed; HA 1.12 remains current.

## Install on another panel

Use `release/tuya-wall-panel-0.10.0-setup.zip`. Extract the entire kit and run:

```
python bootstrap_panel.py --device PANEL_IP:5555
```

Python 3 and Android platform-tools are required. Use an explicit USB serial instead
of the IP for USB ADB. The installer verifies the extracted APK payload and hardware
layout before APK installation, preserves app data, and never reboots or tests outputs.
It selects our Home activity, installs the root service and boot definition, preserves
existing hardware settings, creates missing defaults and disables only known conflicts.
Set the profile/modules, dashboard URL and HA/MQTT credentials in the app afterward.

The APK can perform initial setup itself only when a real root request from its own
Android process succeeds. This panel's stock su fails setgid; the one-time ADB step
is therefore still required on that firmware. Sideloading cannot grant root, enable
ADB, change the firmware permission policy or make unknown wiring compatible.
Setup currently accepts only the verified ARM64 G10/PX30 device-tree layout and
Permissive policy. Unknown panels are refused without disabling their vendor apps.
WebView updates are separate and are not included.

## Setup and recovery boundaries

The UID-restricted socket accepts only fixed worker/capture operations and the three
setup modes. It has no generic shell, network or caller-supplied path API. Root files
are private; hardware device nodes are never made world-writable. Vendor kernel and
audio services remain installed. No blanket Tuya-package deletion is performed.

Prepare stops app hardware, snapshots the known package/Home/awake/init/configuration
state, applies the changes and resumes previously enabled app hardware on success.
The corrected init service executes `/system/bin/sh` with the root-owned wrapper;
direct init execution of the native binary under `/data` is not used.

Snapshots and the persistent interruption journal live under
`/data/local/wallpanel/setup`. A bounded watchdog and wrapper recovery handle
interrupted preparation. Failed restoration retains its recovery journal and is not
reported as successful. Restore means the state before the latest preparation,
not factory defaults. It leaves app hardware stopped to avoid competing with restored
vendor components. Keep the backups, including the separate bootstrap backup.

## Current panel deployment and validation

0.10 is installed on 172.16.1.26. The portable installer was used with
`--bootstrap-only`: matching root support was upgraded with backups, without applying
a new hardware/package/Home policy. Existing saved settings and runtime were retained.
The actual Android app's read-only check reported protocol 2, root ready, supported
G10, permission policy ready, boot ready and no conflicts. Native ownership and all
six MQTT availability statuses were verified online. The current default Home remains
our app and old startup services remain retired.

Automated checks cover factory-style initial provisioning, setting preservation,
exact restoration, unsupported wiring, read-only system refusal, unknown init actions,
partial failures and interrupted setup; authenticated broker operations; boot-wrapper
recovery; payload hash/tamper/path/line-ending checks; existing Java/runtime behavior;
and native APK hash/CLI/signature checks. All hardware-facing tests use fake fixtures.

No Prepare/Restore, reboot, relay, audio, dim/wake or screenshot test was performed
on the live panel. The user performs those checks. See installation-result.json,
app-only-verification.json and panel-setup-verification.json for the current records.

During deployment the user tested Restore and received no_previous_setup. Hardware
was subsequently observed disabled; its cause was not established. Production Java
transition tests confirm that no-snapshot Restore returns before any hardware stop,
repair resumes prior enabled hardware, and a successful real Restore leaves it stopped.
Setup diagnostics now record action, runtime_before and runtime_after, including
exceptions. Agent restored the prior enabled runtime without testing controls; final
native readiness and all six HA availability topics were online. User next tests
Prepare / repair; the setup snapshot does not exist until preparation succeeds.
