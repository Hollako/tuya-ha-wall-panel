# Home Assistant HTTP integration

Base URL: http://172.16.1.26:8099/api/v1/

| Method | Route | Purpose |
|---|---|---|
| GET | status | Receiver state, metadata, audio and privilege status |
| GET | metadata | Current track metadata |
| GET | cover or cover.jpg | Artwork, JPEG or PNG |
| POST | enable | Start/restart the receiver |
| POST | disable | Stop receiver; keep HTTP API available |
| POST | timing | JSON {"latencyOffsetMs":-500..500}, integer; save and apply live without restarting |
| POST | volume | JSON {"volume":0..100} or {"trim":0..100}; local software trim |

Optional authorization header: Authorization: Bearer TOKEN, using the configured token. Query-string token authentication is not supported. HTTP is local and unencrypted.

Enable/disable requests return accepted before the asynchronous operation completes. Poll status afterward; do not equate HTTP 200 with a successfully running native receiver.

root.available is false: the app does not have root. root.helperAvailable indicates an active PTP helper lease. receiver.error reports startup failures. receiver.protocol=airplay2 is the compiled capability, not the negotiated session protocol.

Test evidence: ../verification/http-lifecycle.json. Home Assistant entity/dashboard configuration has not been installed or modified in this session. This API cannot yet control the remote sender's play/pause/next operations.

Version 1.1.0 timing response: {"accepted":true,"restartRequired":false}. Poll audio.timing.requestedOffsetMs and backendObservedOffsetMs during playback. Receipt does not prove acoustic settling. audio.timing includes timestamp validity, generation, frame counters, estimated output rate, queue, route and underruns. Empty timing means no current output connection. synchronizationVerified remains false until separate acoustic validation; audio.corrections is a legacy placeholder, not a measured correction count.

## Web timing controls (1.1.1)

Open http://PANEL_IP:8099/ from a phone/iPad on the local network. The built-in mobile page offers the -500..500 ms live slider, -5/+5 ms and Reset. Changes save automatically on this panel only; no app restart is requested. It shows receiver name/state, track and output-clock/underrun diagnostics. Large jumps may briefly trigger native resynchronization.

The HTML shell is accessible without a token so it can display a token-entry form; every status/control API request still uses the existing bearer authentication. A configured token stays in page memory only and must be entered again after reloading. No external assets or cloud services are used. Failed saves remain visible; rapid changes are serialized/coalesced to preserve the last requested offset. The Refresh status button reads current status; after a failed write, adjust again to retry.

Panel URLs after installing 1.1.1: http://172.16.1.26:8099/ and http://172.16.1.33:8099/. This page is not present in installed 1.0.5.
