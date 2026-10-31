# One media player per panel — HA integration 1.26.0

The controller device has one Audio media player. Saved groups no longer create
additional media players on that device. Their old media-player registry entries
are removed on integration setup, scoped to this integration entry and its saved
group IDs. The existing Audio entity ID remains unchanged.

Choose a saved group from Audio's source selector, for example `Group: EH KSA`.
This activates the existing panel-owned preset; it does not delete or recreate
the saved group. The Audio player retains source playback controls, room volume
and mute, joining/leaving, media browsing, and live group membership. Each other
physical panel keeps its own player for independent room control. Preset editing
also remains under the integration's Audio groups configuration and panel web UI.

Install: replace `/config/custom_components/tuya_wall_panel` with this package's
folder and restart HA. No APK update is required. Any dashboard or automation
that previously targeted a saved-group media entity should instead select
`Group: <name>` on the existing Audio player.
