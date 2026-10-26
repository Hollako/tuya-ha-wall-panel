# Independent HA and intercom networks - 0.15.0 / code23

User reproduced failure when laptop Ethernet DHCP made Ethernet Android's default:
Wi-Fi stayed connected, incoming SIP could ring but camera/session completion failed.
User removed Ethernet and functionality returned. Both interfaces physically and at
IP level work together: Wi-Fi172.16.1.26/24, Ethernet192.168.3.1/24, laptop192.168.3.33.
Network assignment implemented inside the app, without global routes/IP/DHCP changes.

## Configuration

Setup > Network assignments (also reachable from Intercom settings) has independent
Auto/Ethernet/Wi-Fi choices for HA dashboard/MQTT and intercom, each with an optional
fallback checkbox. Fallback defaults false and applies only if the selected interface
is unavailable, not on failed login/server requests. Private network_settings prefs
are separate from hardware/account drafts and survive boot/update. Fresh installs
default Auto. Current panel explicitly sets BOTH profiles Wi-Fi, fallback disabled:
all real servers remain on its Wi-Fi LAN; Ethernet is only the laptop test network.

An explicit intercom choice uses its selected interface IPv4 for SIP Contact/SDP.
The old SIP local-IP override applies only in Auto mode, preventing a stale Wi-Fi
address from defeating a later explicit Ethernet selection. Intercom profile changes
are blocked during calls. Runtime network loss/change closes/reinitializes SIP and
registration retries; active calls are not promised seamless roaming. HA and SIP
reconnect independently; hardware/local relay bindings stay active while MQTT is down.

## Routing implementation

NetworkRouter observes available/default networks and link/IP changes, keeps requested
transports alive where permitted, and chooses connected IPv4 networks without requiring
internet validation. HA binds the app's WebView process default to its selected Network;
it never repeats the bind for an unchanged Network, avoiding unnecessary socket/DNS
resets and dashboard refreshes. Dashboard reloads only for actual HA network/IP changes
or existing error handling, not ordinary screen wake. Strict missing HA blocks new
loads/connects; old process binding is retained rather than falling onto the other LAN.

MQTT runtime, setup login probe and screenshot upload explicitly create their sockets
and resolve hostnames through the same selected HA Network. Interface changes close
the old MQTT session for recovery without restarting the native hardware owner.

SIP JAIN stack uses custom NetworkLayer and AddressResolver: UDP sockets pinned by
Network.bindSocket and hostnames resolved on the intercom Network. RTP socket and
peer resolution use that Network too. Stack identity includes network handle and IPv4,
so reconnecting with the same IP on a new Android Network still rebuilds registration.
Hikvision HTTP unlock uses Network.openConnection, with original restricted command/
authentication/guard behavior retained. No process-wide network switch is used for SIP.

Native MediaPlayer's RTSP sockets do not expose the needed network control, so video
now uses pinned AndroidX Media3 RTSP1.4.1. Custom SocketFactory binds the socket before
connection and resolves the endpoint on that network. RTP forced interleaved TCP so
video cannot open unbound UDP channels. Video-only track selection and volume0 keep
SIP audio ownership; no audio focus request. RTSP debug/Media3 logging disabled to
avoid credential output. Same SurfaceView/aspect-fit/no-crop UI and12s/3-attempt recovery;
network loss/return reflected by call-view polling; release bounded to800ms. User tests
actual decoded video/latency/audio/hangup because no agent media PLAY is permitted.

## Build and verification

Pinned25 official Google Maven/Maven Central dependency artifacts via media3-deps,
SHA256 manifest in vendor/media3. AAR classes/resources extracted safely, resources
linked with existing SDK34 AAPT2 and newly downloaded Android35 resource platform.
Generated library R classes and D8 support multidex (native Android5+); API23 minimum,
target27 and Android8 panel OS unchanged. App source Java8; root helper Java6 and
native provisioning protocol3 unchanged. Apache2 license/attribution bundled. APK~4MB.
Builds use pinned local artifacts after initial fetch; do not run dependency updates
implicitly. Original standalone PanelIntercom sources unmodified.

Local suite passed existing80 config,33 store,36 runtime checks,333 SIP protocol checks,
RTP fake audio,12 ringtone/network-aware video lifecycle checks, fake HTTP door client
and21 actual NetworkRouter/SIP transport tests with fake Android networks. Tests cover
separate choices, DNS/socket ownership, strict absence, fallback, IP changes, stable
callbacks, no repeat binds, muted RTSP TCP, stale callbacks and release. APK signature
and six provisioning payload integrity verified. No agent calls/sounds/camera PLAY/
relay operations/reboot. Source change has no HA integration files to copy.

First installed candidate: SIP freshly registered with user's UPDATED Asterisk while
Ethernet remained Android default103. Both profiles wlan0/172.16.1.26; actual MQTT
runtime/HA six availability topics online and native ownership healthy. Dashboard
WebView reports loaded on Wi-Fi. Read-only NetworkDiagnostics probe: MQTT TCP true,
dashboard HEAD405 (server doesn't support HEAD, WebView GET loads), camera RTSP OPTIONS
200 on local172.16.1.26, no PLAY/SETUP. Network diagnostics contain no credentials.

Backup: /data/local/wallpanel/before-network-routing.20261025T203832Z
See network-install-result.json, app-only-verification.json, dashboard-network-status
and network-runtime-status on panel. User must test incoming call, video/audio and
hangup with Ethernet left connected, plus physical reboot/link-loss tests later.

Other Android apps are not governed by our policies. No Ethernet-to-Wi-Fi bridge,
forwarding, static-IP or default-gateway modifications. Real building Ethernet with
its own servers remains a future physical topology test; current simultaneous Wi-Fi
application traffic despite Ethernet default is verified.

## 0.15.1 video regression correction

0.15.0's Media3 RTSP parser rejects this Hikvision SDP because its H264
sprop-parameter-sets value is empty. This happens even after Ethernet is removed.
The native player previously accepted the parameters carried inside RTP instead.
0.15.1 uses an explicitly network-bound video-only RTSP/TCP transport and Android
MediaCodec, configuring from actual in-band SPS/PPS. No fabricated codec parameters,
camera configuration changes, process-wide intercom binding or audio track setup.
Aspect-fit and the existing call controls/audio remain. Socket cancellation,
15-second first-picture timeout, bounded retries and private redacted phase/error
status are retained. Current supported video codec remains H264.

Checks: loopback fake Hikvision SDP with empty parameters, Digest authentication,
video-only SETUP/PLAY, injected network socket factory, in-band SPS/PPS/IDR,
STAP-A/FU-A, sequence loss/duplicate/wrap, timestamp wrap and cleanup. Native
surface decoder lifecycle uses fake Android APIs. No real camera PLAY, call,
microphone/speaker test, door command or reboot by the agent. User must verify
actual picture first with Wi-Fi only, then with laptop Ethernet connected.

## 0.15.2 authentication correction

User reported 0.15.1 still unavailable with Ethernet unplugged. Private video
status located failure in authorization before codec setup. Hikvision returns two
WWW-Authenticate headers: Digest, followed by Basic. The header map overwrote Digest
with Basic, which this transport intentionally does not use. Preserve the Digest
challenge in either header order. Regression now reproduces both orders. Production
RTSP code was verified against the real camera with authenticated DESCRIBE only:
login and H264 description accepted. No SETUP/PLAY or media capture performed.
0.15.2 keeps the in-band header decoder and independent network policy unchanged.
Physical video acceptance remains for user; SIP audio was not changed.
