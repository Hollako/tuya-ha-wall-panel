# App recovery controls (0.16.0 / HA 1.13.0)

Two non-retained, exact commands on `<base>/app/command/set`:

- `RELOAD_DASHBOARD`: refresh the existing WebView on the UI thread. Reuses its
  session and current URL, applies existing compatibility styling after load,
  and uses the normal network retry if disconnected. SIP/hardware keep running.
  If no viewer exists, opens the configured Home dashboard.
- `RESTART_APP`: arm a private explicit Android alarm, publish app offline, stop
  BridgeService and SipService without changing saved enable flags, then exit
  the process. The alarm restarts enabled services and Home. Android stays up.
  Any active call ends; never test this remotely on behalf of the user.

No force-stop is used because it would cancel the relaunch. No root command,
additional network listener, settings reset or external activity is introduced.
Retained messages and unknown/misrouted actions are rejected before dispatch.
