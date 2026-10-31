# App audio grouping

Available in unified 0.31.1, dedicated Audio Receiver 1.8.1 and HA integration 1.23.0.
Select a source room and members in /groups, then Play app audio on selected rooms.
Play media in an external app on the source room. Switching between installed media
apps does not require reselecting the input. Stop the group to restore direct output.
HA room/saved-group players expose App audio when the source helper supports it.

## Audio path and isolation

The existing root-provisioned bridge drops to system UID before starting Android.
The app accesses it only through a peer-UID checked local socket. CAPTURE_APPS uses
a fixed 48000-Hz stereo PCM stream, USAGE_MEDIA/USAGE_GAME and a bounded list excluding installed system/shared UIDs and the product UID. The receiver product's
own UID is excluded so synchronized playback cannot recapture itself. Installed system/Bluetooth identities are excluded; app capture refuses multi-user
panels. There is no microphone capture or
communication/alarm/notification usage rule. No protected-content capture override
is enabled; apps, firmware and protected content may restrict capture.

The policy redirects matching tracks to the capture mix instead of playing a second
early local copy. The unchanged group server and clients play both source and
receiver rooms. A disconnected capture socket releases the AudioRecord and policy.
Only one capture lease (Bluetooth or apps) can run at once. Group pause silences the
forwarded stream and attempts media-session transport on an eligible source app.
The receiver product's own WebView/player is deliberately excluded with its UID;
local files and AirPlay retain their existing separate paths. New ordinary media apps do not require their own inclusion rule. Restart the group
after installing a system/shared-UID app so exclusions can be revalidated.

The legacy Bluetooth helper socket, service, filenames and STATUS/CAPTURE protocol
remain compatible. STATUS_APPS/CAPTURE_APPS are capability-negotiated additions.
The native bootstrap has a no-capture --probe path; privileges are dropped before
ART starts on both Android versions. The helper still has zero Linux capabilities.
No AirPlay decoder, synchronization binary, network assignment, codec firmware,
Bluetooth coexistence mode, relay state or screen setting is changed here.

## Deployment and evidence

Both existing helpers were backed up before bridge.jar/bootstrap replacement. Their
new read-only probes passed with uid=1000, routing/record permissions granted, app
policy builder supported and capture_started=false. AirPlay helper/wrapper hashes
were unchanged. Both apps were updated preserving their UIDs and saved settings;
both group APIs report appAudioGroupAvailable=true. Existing group sessions were
stopped for the update; no capture or playback was started by the installer.

Offline checks passed: ordinary-app/owner/system/cross-user UID isolation, HA room
input targeting, web input controls and native payload contracts. Actual YouTube
capture, acoustic sync and extended app switching remain user acceptance checks.
If required, stop the group, restore the helper files from its before-app-audio
backup, restart the helper and reinstall the matching earlier signed APK. No new
init entry or firmware partition change is required for these existing helpers.


## Capture rule limit correction

A133's native policy dump exposed a 20-criterion limit: 0.31.0/1.8.0 silently lost
ordinary app UID rules after the first 18 identities. YouTube UID 10053 was not in
the effective mix, despite allowPlaybackCapture=true. The bridge now uses the
framework excludeMixRule API for installed system/shared identities and its own
feedback player, leaving ordinary media apps eligible. It refuses multi-user
panels and rejects oversized policies before registering a route. MEDIA/GAME are
still the only capture usages. Regression covers more than 100 ordinary app UIDs,
feedback/system exclusions and fail-closed behavior at the native rule limit.


## Boot availability correction

The G10 power-cycle check found the installed app was 0.30.4 (without App audio UI)
and init explicitly refused the existing wallpanel_btgroup entry because its
SELinux domain was undefined. The app was updated to 0.31.1 and synced. Both existing
capture init entries were backed up and given the same u:r:su:s0 launch label as
the already working root panel service; vendor was returned read-only. The Java
capture worker still drops to UID 1000 with zero capabilities before ART starts.
No reboot was performed by the agent. Cold-start validation remains the user's
check. These changes do not explain conclusively why G10 returned to the older
APK; monitor the installed version after the next user power cycle.
