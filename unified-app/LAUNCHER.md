# Home launcher support — version 0.3.0

Version 0.3.0 was subsequently installed at the user's request and setup opened.
The agent has not selected it as default Home. Do not describe Home behavior as
physically tested yet; user launcher checks are next.
Post-update read-only resolution reports `local.wallpanel.app/.HomeActivity` as
the default Home handler. Android selected/resolved it without an agent command
changing preferred activities. The existing hardware/startup controllers remain
unchanged; their startup behavior still needs the planned handover.

The manifest registers `HomeActivity` with MAIN / HOME / DEFAULT, singleTask mode
and its own `local.wallpanel.home` task affinity. `MainActivity` stays the normal
application icon/settings entry. The dashboard preview remains private.

The Home root hosts the WebView directly. A router that launched a separate dashboard
above a singleTask Home would destroy that dashboard on subsequent Home presses;
this implementation avoids that path. Repeated Home intents/resume preserve the
WebView when configuration is unchanged. A changed saved URL rebuilds the view;
process death necessarily creates a fresh view. Old-view callbacks cannot mark a
replacement page as failed or completed.

Dashboard routing reads only public local settings, not MQTT credentials or broker
connectivity. Missing/invalid URL or a disabled Dashboard module shows setup, with
an Open setup recovery surface if authentication is cancelled. Normal configured
Home does not require an admin PIN; opening administrator settings does when locked.
The dashboard's top-left hold gesture remains available without network or HA.

The **Set as Home app** button saves validated local settings, then opens Android's
Home selection screen. It falls back to Default apps settings or manual instructions
if this firmware does not expose those screens. Default-Home status is shown in setup
and refreshed when it resumes. No preferred launcher is silently replaced, no root
command sets defaults, and no receiver races Android's normal Home startup.

After a later installation step, the user can select **Tuya Wall Panel** as Home.
Check Home returns to the dashboard, repeated Home does not reload it, setup stays
reachable via the corner hold, and incomplete setup remains recoverable. Cold-boot
verification remains for the user. The old browser launch/compatibility hooks were
subsequently retired through the backed-up handover in `startup/README.md`, because
they were overriding Home after reboot. Hardware/backlight services remain active.

Launcher support is separate from device-owner/lock-task or system-wide kiosk
enforcement. Home/Recents/settings escape prevention and HA-admin unlock still need
the planned managed-kiosk work. Existing hardware worker assets remain inactive.

Validation: 66 pure Java checks including eight Home routing/preservation cases,
manifest checks for the Home filter/task/private preview/no boot receiver, Android
API 27 compilation, and local APK signature verification. No Home/reboot test or
default-launcher change was performed on the physical panel.
