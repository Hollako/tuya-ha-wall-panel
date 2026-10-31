# G10 Bluetooth receiving correction (30 October 2026)

## Observed failure and qualification

The AP6212A/BCM43438A1 G10 firmware has its A2DP Sink resource overlay enabled.
There were no Bluetooth service crashes. During phone-initiated connection attempts,
Bluetooth PAN connected repeatedly but A2DP stayed disconnected; device discovery
was active. Cancelling discovery and requesting profile 11 for the existing bonded
phone established SBC stereo audio at 44100 Hz without removing any bonds.

Playback then starved the firmware Bluetooth AudioTrack repeatedly. A short
Wi-Fi-off interval produced clear sound, and glitches returned afterward. Wi-Fi
power saving was already off. The decoder quota was seven SBC frames per 20 ms,
with matching Android output rate; no decoder change was made.

The Broadcom driver reported firmware 7.45.96.123, FWID 01-457251af and btc_mode=1.
A separate-guarded 30-second mode-4 trial kept Wi-Fi on; the user reported clear
audio. Automatic restoration to mode 1 was verified. This is a listening result,
not a long endurance or Wi-Fi-throughput qualification.

Vendor explanation of modes:
https://community.infineon.com/t5/AIROC-Wi-Fi-and-Wi-Fi-Bluetooth/Bluetooth-audio-streaming-WiFi-inteference/td-p/379269

## App 0.30.2

The G10 audio hardware worker uses a fixed Broadcom ioctl allowlist. It checks the
magic and exact qualified firmware ID before changing btc_mode from 1 to 4. It
rechecks every ten seconds to handle Wi-Fi driver reloads. It does not modify
firmware/NVRAM, radio power, decoder, AirPlay, synchronization or pairing records.
Other firmware IDs and unexpected mode values are left alone. On orderly audio
worker shutdown it restores its original mode, provided it still owns mode 4.
No correction is compiled into the A133 backend. The G10 audio module must be on.

Bluetooth receiver settings add explicit pairing visibility and a bonded-phone
music connection dialog. The shared BluetoothAudioConnect helper cancels discovery
and requests the sink profile for the selected phone. It closes its profile proxy
and has an eight-second bind timeout. It never unpairs, changes profile priority,
forces playback or retries indefinitely. This shared helper is available to the
standalone product; no standalone APK update/deployment was performed here.

## Validation and remaining checks

Fake-I/O tests cover exact firmware gating, fixed mode allowlist, throttle/retry,
failed writes, original-mode restoration, and respecting externally changed modes.
The existing native hardware regressions passed and the APK compiled successfully.
The user should test sustained Bluetooth audio, reconnect via the new button,
normal Wi-Fi/HA operation, reboot and playback after boot. No calls, relay tests,
reboot or agent-initiated playback are part of installation.

Deployment: APK 0.30.2/code 66 was installed preserving settings. The root service
uses its own fixed worker copy, so that copy was also updated after verifying the
G10 probe and both checksums. Backup: /data/local/wallpanel/wallpanel-core.before-bluetooth-0302.
The live worker hash matched the new packaged asset, all hardware reported ready,
and a separate read-only query confirmed btc_mode=4. A133 was not updated.

Rollback requires stopping the app hardware service, restoring that root worker
backup, then reinstalling the prior signed APK preserving data and starting the
service. Reinstalling the old APK alone does not replace the pinned root worker.
Orderly shutdown of the new worker restores its previous coexistence setting;
a Wi-Fi driver restart also resets the runtime setting. The diagnostic trial
utility is not a startup service.


## 0.30.3 setup-start recovery

After the diagnostic deployment stopped the app process, starting MainActivity
alone resumed no saved receiver/SIP service. Starting BridgeService restored only
hardware; ReceiverService (the web server and group discovery owner) remained off.
The user also had a receiver attempt aimed at Panel 2's retired address. Restoring
HomeActivity resumed the receiver/web lifecycle; both group APIs returned HTTP 200,
room discovery showed the current addresses, and the user's Bluetooth group then
reported capture signal and a connected Panel 2 receiver.

MainActivity now reconciles the same three saved modules as HomeActivity:
hardware, SIP and the receiver. These paths are idempotent and respect saved
module enablement. Opening setup after a process restart no longer depends on
visiting Home first. Group playback is still explicitly started by the user.
0.30.3 contains identical native audio, hardware and synchronization payloads to
0.30.2. No automatic playback or new startup services were added.


## 0.30.4 settings Back recovery

During the Bluetooth investigation the app was force-stopped for startup checks.
The later Android-settings inspection found accessibility_enabled=0 and the
configured accessibility-service list empty. The existing SettingsBackService
was re-enabled preserving any other service entries. BluetoothSettingsActivity
was also missing from the exact settings-scope allowlist; 0.30.4 adds it. The
navigation scope/capability tests passed. No Bluetooth, Wi-Fi or group engine
changes are included in this patch. The app still offers the normal Android
accessibility enablement page when the administrator has disabled the service.
# Phone-initiated reconnection investigation, 2026-10-30

**2026-10-31 confirmation:** the user tested the incoming connection fix and
grouping repeatedly on both panels and reported all good. A new report concerned
simultaneous senders: a laptop could play in parallel with the phone on A133.
Single-music-sender admission is now installed on both variants (0.34.1/1.11.1),
retaining the first sender until disconnected. See `shared/audio-receiver/SINGLE_BLUETOOTH_SENDER.md`.

Clean pairing also failed; the user confirmed that selecting the same phone on
the panel connects immediately, while A133 accepts the phone directly. G10 uses
SBC and A133 negotiates AAC; this update does not alter codec configuration.

SmartWay Panel 0.34.0 implements a narrowly bounded workaround on Android 8/9:
after a bonded device's ACL_CONNECTED event, allow 750 ms for normal setup, then
make one ordinary sink connect request if no music connection is present or in
progress and no other phone is using the profile. This uses the same API as the
existing manual BluetoothAudioConnect button. It does not scan, toggle the radio,
disconnect anything, change priorities or issue playback commands. Pending work
is canceled on ACL disconnect/requested disconnect or radio-off; duplicate and
stale events, unbonded devices and rapid repeats are guarded. Android 11's working
connection flow is not given this assist. 31 host policy guards pass.

This is a targeted compatibility workaround, not a proven explanation of why the
G10/phone initially chooses PAN instead of initiating A2DP. Real phone-only
reconnection validation remains pending. App installation preserves the running
persistent Bluetooth route and native audio engines.

Follow-up: the phone's Media audio ON / Internet sharing OFF test still failed.
The same phone connects directly to A133. Read-only probes show both adapters
advertise the same UUID list: HFP AG 0x111F, Headset AG 0x1112 and Audio Sink
0x110B. Latest G10 attempts again show PAN rejection and no A2DP connection.
No audio routing or pairing policy was changed. Panel 1 Bluetooth settings were
opened; a clean two-sided re-pair and phone-only reconnect test is pending.

If clean pairing still fails, a bounded app-side music-profile assist is the next
targeted option: react only to a bonded phone's incoming ACL connection, wait
briefly for normal A2DP setup, then make one ordinary A2DP sink connect request
using the same proven API as BluetoothAudioConnect. Never scan, unpair, change
radio state, switch another connected phone or start media playback. This option
is not implemented yet; assess the re-pair result first.

After persistent capture solved group playback, the user reported that a paired
phone connects for music only when selected through the panel's existing Connect
paired devices action. Passive capture of a failed phone-side attempt showed PAN
connections dropped by PanService because tethering is off; no A2DP sink session
was established. Both paired-device source priorities already permit connection.

A read-only adapter probe confirmed local Audio Sink service UUID 0x110B is
advertised and scan mode is connectable (21). This does not yet prove whether the
phone's cached service/preference state or another incoming-profile behavior is
responsible. The user is checking Media audio ON / Internet sharing OFF and a
phone-only reconnect before any panel policy, pairing or app changes are made.
The working persistent Bluetooth/audio route and Wi-Fi coexistence mode remain
unchanged during this investigation.
## Current status — 31 October 2026

The incoming paired-phone connection assist was subsequently installed and
confirmed working repeatedly by the user. The strict single-sender rule remains
unreliable and is deferred. SmartWay Panel 0.34.4 also restores the fractional
room gain to standalone Bluetooth output; listening validation of that last
change and the separately reported grouped loudness remains with the user.
