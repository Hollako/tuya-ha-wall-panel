# Dashboard gray status-bar background (0.17.1)

The observed 32px gray strip was `android:id/statusBarBackground` in
`local.wallpanel.app`, not an overlay belonging to the separate audio receiver.
The dashboard WebView and content already filled 1280x800. SurfaceFlinger showed
only the dashboard's visible application layer; Android StatusBar and receiver
activity windows had no visible surfaces.

The dashboard relied on immersive View flags. Its flags had become `0x1700`,
missing FULLSCREEN/HIDE_NAVIGATION, leaving the Material theme's `#757575`
status-bar background drawn by DecorView. Root notification/navigation restrictions
were still active and do not replace window-level fullscreen behavior.

`DashboardFullscreen` adds the native fullscreen window flag, sets the status-bar
background transparent, and reasserts immersive flags after visibility changes
only while the dashboard is resumed and focused. It cancels pending work on pause
and removes its listener on destroy. Setup and other apps keep their own windows.

The isolated fake-window test checks cleared-flag recovery, focus/pause guards,
idempotence, cleanup and other-window isolation. No HA/frontend changes are needed.
