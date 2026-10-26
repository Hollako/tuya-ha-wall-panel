# Door station brands — 0.14.1

The intercom's SIP audio, RTSP video and call UI remain independent of door brand.
Settings > Intercom settings > Door station brand explicitly selects:

- Generic SIP / RTSP (no door API): audio/video configuration remains available;
  vendor door buttons are hidden and the door client rejects requests without I/O.
- Hikvision (ISAPI): separate HikvisionDoor adapter owns the tested digest HTTP/XML
  momentary-open protocol. IntercomDoor only routes by the configured brand.

Akuvox, 2N and Dahua are planned adapters, not implemented/selectable yet. Add a
brand's documented/supported protocol and tests behind IntercomDoor, register its
stable ID/name/capability in SipConfig, then expose it in settings. Keep credentials,
supported door count and command semantics explicit; never fall back to Hikvision
for an unknown brand. Do not change the call UI to introduce another vendor protocol.

Encrypted SIP configuration now includes station_brand. Fresh configurations default
to generic. Existing pre-brand configurations with a saved door API server migrate to
hikvision because all prior door control was Hikvision-only; unconfigured ones migrate
to generic. Migration encrypts/commits before publishing the new cached config, with
all prior account/video/ring/door enable settings retained. Selection is editable in
the setup spinner. Diagnostics expose only the nonsensitive brand ID.

Installed0.14.1/code19 on172.16.1.26. Confirmed saved Hikvision migration and SIP
registered. Existing door login/controls retained; no real door commands or physical
call/audio/video tests. Backup of prior encrypted prefs:
/data/local/wallpanel/before-door-brand.20261025T183159Z
See brand-install-result.json. Local regressions and production fake-door digest
tests pass, including generic-brand rejection with zero HTTP requests.
