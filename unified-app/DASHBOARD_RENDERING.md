# Permanent dashboard transparency rendering — 0.15.3

The previous Lightning fix converted CSS #RGBA and #RRGGBBAA declaration values
to rgba() and repeatedly applied that transformation through a separate watcher.
The unified app had not inherited that watcher. Its targetSdkVersion=27 also kept
WebView in legacy hexadecimal-color compatibility mode.

The permanent app-level fix targets API 28, retaining minimum API 23 (the panel
still runs Android 8). This opts WebView into native CSS Color Level 4 alpha parsing
for all stylesheet types, inline styles, future SPA updates and shadow-root styles.
There is no CSS rewriting, hardcoded dashboard origin, development/debugging port,
JavaScript bridge or external watcher. The existing FOREGROUND_SERVICE permission
and usesCleartextTraffic=true remain explicit, preserving background operation and
local HTTP dashboards under target API 28. TLS and mixed-content policy are unchanged.

After a page finishes, DashboardRendering performs a hidden color-only diagnostic
and immediately removes its probe element. It records computed four/eight-digit
alpha colors, transparent zero, target SDK and WebView version in the app-private
files/dashboard-rendering-status.json. No URL, cookies, account information or page
content is recorded. It does not reload the page or inspect SIP/camera media.

Source: https://developer.android.com/about/versions/pie/android-9.0-changes-28
section CSS RGBA hex value handling. The public compatibility flag is preferred
over private WebView reflection or a custom stylesheet parser.

Deployment: standard APK replacement, prior APK/preferences backed up, network
preferences retained. No HA integration update or dashboard server edit needed.
See dashboard-rendering-install-result.json for the actual panel verification.
User visual acceptance of dashboard transparency/buttons remains pending.

Panel verification: 0.15.3 installed, native_alpha_correct=true; four/eight-digit
RGBA and fully transparent colors computed correctly in WebView 138.0.7204.63.
SIP registered, selected Wi-Fi profiles and saved network preferences preserved.

## 0.15.5 — HA dropdown theme token compatibility

User screenshot showed white HA select fields despite correct native alpha colors.
A temporary developer build identified the exact mismatch: HA-SELECT inherits the
legacy dark theme (#142326 card / #1b3033 select / #edf4f1 text), but its new
HA-PICKER-FIELD reads --ha-color-form-background from a light palette (#f3f3f3).
The disabled token was #ccc and the unknown-state warning token was light too.
This is separate from the native alpha compatibility fixed in 0.15.3.

The packaged assets/dashboard-theme-compat.js runs automatically on HA pages.
Only HA-SELECT/HA-COMBO-BOX scopes with a dark legacy theme AND a mismatched light
new palette receive mapped form, neutral, warning, border and text tokens from
existing theme variables. Warning tint uses the existing warning color. The script
restores previous inline values when a complete theme or light theme is selected,
preserves externally edited values, and leaves other dashboards/controls untouched.
Idempotent injection, debounced mutation observation across shadow roots and a
five-second lazy-root fallback cover SPA navigation and late-loaded components.
No server edit, HTTP fetch, JS/native bridge, private WebView API or external service.

Ten isolated Node tests passed for matching, disabled/warning tokens, repeat work,
light/complete-theme restoration, preserved external edits, non-HA no-op. Final APK
asset bytes verified. Temporary inspection is explicitly disabled in the final app;
diagnostic 0.15.4 APK/ZIP removed. Final 0.15.5 visual acceptance is exclusively the
user's check, per their instruction. Do not capture/test the UI without being asked.

## 0.15.6 — selected option and hover highlight

User confirmed closed dropdowns correct, then requested a screenshot of an open
menu. The requested screenshot (dashboard-dropdown-menu.png) showed the menu body
already dark but the selected row still pale (#eff9fe, matching HA's light primary
quiet palette). Extend the SAME scoped compatibility mapping to primary quiet and
normal resting/hover/active tokens. Derive highlights from the existing theme
primary/accent color blended with the existing field background; text follows theme
primary/foreground tokens. No fixed white/black palette. The same detection and
restore rules continue to leave light/complete themes and other dashboards alone.

17 isolated compatibility tests pass, including all six primary state mappings,
hover distinction, repeat stability and light/complete-theme restoration. Packaged
asset is updated; no UI interaction or selection changes by agent. User checks the
open menu after install. Screenshot was taken only at the user's explicit request.
