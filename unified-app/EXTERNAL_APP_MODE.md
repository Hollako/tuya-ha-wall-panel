# Installed-app mode — 0.22.0

Setup now offers **Web dashboard** or **Installed app** as the display mode. Both
use the existing Dashboard / app module. The selected installed app runs in its
own Android task, supervised by Wall Panel. No app-internal settings, automation,
content injection or downloaded APKs are involved. Install/configure the target
app separately before enabling supervision.

1. Open administrator setup and choose **Installed app**.
2. Use **Choose installed app** to select a launchable package.
3. Grant **Allow administrator hotspot** in Android's overlay permission screen.
4. Keep Dashboard / app enabled and tap **Save and open**.

Prepared root support with the new System UI helper is required. The target is
not launched into supervised mode until the helper acknowledges its app guard.
If permission, root support or the selected package is unavailable, Home retains
an administrator recovery screen. The current verified deployment target is G10;
A133 root/hardware provisioning remains a separate unfinished adapter task.

## Session behavior

- Home, boot and package replacement reconcile the saved app-mode session.
- A foreground service holds the existing UI restriction lease and a small,
  invisible top-left administrator hotspot. Hold it to open setup. When PIN
  locking is enabled, restrictions remain until PIN authentication succeeds.
- A root monitor checks the foreground task every 500 ms. It reopens the selected
  package after an exit, with bounded backoff. It permits Wall Panel's call/setup
  screens and standard Android permission prompts. In-app navigation stays with
  the selected package; leaving that package triggers recovery.
- System UI restrictions remain active, and an immersive rule is scoped to the
  selected package while preserving pre-existing rules. Normal lease closure
  restores the previous policy if it has not been changed by another owner. A
  root-only backup recovers a stale rule on the next lease after reboot/death.
- SIP takes priority. The administrator hotspot is removed during calls; the call
  screen uses the existing guard and returns to the selected app afterwards.
- Authenticated setup stops supervision and restores system controls. Returning
  Home or using Save and open resumes it. HA Open setup follows the same PIN path.
- HA dashboard URL changes update the saved web URL without changing display mode.
  The HA reload action opens Home; external-app mode reopens its selected app.
- The host does not create a WebView in app mode. Web dashboard mode retains its
  saved URL, network selection, rendering fixes and existing controls. Wall Panel's network assignments apply to its own WebView/MQTT/SIP connections; the selected external app retains its own Android network behavior.

Display mode/package are saved with the existing revision and encrypted draft
handling. Existing installs default to Web dashboard; updating does not activate
external mode automatically. No HA integration update is required.

## Root interface and verification

The native broker's authenticated UI_GUARD operation remains unchanged. The root
Java helper advertises app_guard support, accepts one strictly validated APP
package command on that live lease, and acknowledges before supervision starts.
There is no generic command runner or network API. Its fullscreen-policy backup
lives at `/data/local/wallpanel/app-mode-policy` and is root-only.

The new APK and matching root-owned wallpanel-system-ui.jar are both needed on an
already prepared panel. Full setup kits contain the new helper for both ARM32 and
ARM64. Replacing the APK alone does not replace an existing root helper.

Local checks exercise production target validation, task recovery/backoff,
call/setup/permission exceptions, stale-policy restoration, EOF cleanup, private
restart handling, stored mode migration and unchanged HA URL behavior. Existing
SIP/audio/video/network and architecture checks also run against fixtures.

Physical testing is left to the user: target launch and navigation containment,
top-left PIN recovery, HA Open setup, call takeover/return, app exit/recovery,
reboot recovery and switching back to Web dashboard. Apps with cross-package
sign-in flows should be configured before supervision. Repeated crashes, system
dialogs and vendor-specific task behavior need device validation.
