# A133 Android 11 adapter

Build 0.30.0 supports the tested sun50iw10p1 A133 variant with 32-bit ARM
userspace. ARM64 A133 hardware remains unverified and its native probe refuses
activation. The universal APK retains the existing ARM32/ARM64 G10 payloads.

## Hardware mapping

- Two physical relays: `/proc/vendor/relay_first` and `relay_second`, with verified
  ON/OFF readback. L3/L4 labels have no fitted terminals on the test unit; neither
  extra software channel can be commanded through this adapter.
- Knob LCD: `/dev/graphics/fb1`, 240×240 RGB565, horizontally mirrored, independent
  `/proc/vendor/lcd_ctrl` backlight. The existing fonts, menu, artwork, mute symbol
  and clock/weather screensaver are reused. No physical knob LED is exposed.
- Knob rotation: passive GPIO PB9/PB10 observation via kernel debug GPIO output,
  sampled every 2 ms. The DT controller/pins and LCD geometry must match first.
  The existing half-step decoder recovered exact 10/10 slow and 4/4 quick detents
  from physical captures. The earlier fast 10/10 request produced 14/15 and is
  recorded as inconclusive, not a passing test. Missing sampler/input support
  stops the worker instead of silently using the half-rate vendor rotary events.
- Presses: `sunxi-keyboard`, KEY_F3 click and KEY_F4 firmware hold. Double-press
  retains the existing 400 ms classifier. Vendor rotation key events are ignored
  while the direct observer is active. The kernel input driver stays bound.
- Main display: Allwinner `/dev/disp` screen 0, ioctls 0x102/0x103 for brightness
  and 0x104/0x105 for backlight only. Never blank fb0: it exposes the startup logo.
  Existing dim/off timers and consumed first-touch wake use the gslX680 input.
  Knob activity does not wake the main display. Normal cleanup restores the
  captured main brightness and knob framebuffer/backlight.
- Audio: existing Android/receiver playback, routing and software volume. Native
  amplifier power uses `outside_pa`; speaker selection uses `horn_mode`. The G10
  rk817 codec-gain register path is never used. Its hardware-gain HA control stays
  unavailable. Line Out mute uses software: the hardware mute node did not mute
  the connected external Line Out amplifier during qualification.

## Preparation and restoration

For a new authorized A133 installation use the setup kit with:

```
python bootstrap_panel.py --device PANEL_IP:5555 --profile a133
```

Select A133 in app setup before starting hardware. The installer uses the A133
payload and setup script hashes, probes wiring without writes, requires trusted
root and the existing permissive policy, and installs an authenticated root
worker service. It preserves app settings and snapshots package/home/startup
state before changing the explicit conflicting Tuya UI/controller packages.
It does not alter vendor kernel drivers, Bluetooth framework/player, receiver
services, WebView, verified boot or SELinux. G10 legacy startup cleanup is absent
from the A133 script. Failed preparation rolls back; Restore leaves app hardware
stopped and restores the backed-up vendor state.

The currently installed dedicated receiver must be migrated in a separate
backed-up receiver cutover. Preparation deliberately preserves it. Do not enable
two receiver engines against the same hardware. Existing Bluetooth grouping and
AirPlay timing helpers authorize a specific app UID; changing products requires
their owner authorization/settings migration too.

## Validation and remaining commissioning

Native fake-device tests cover DT/geometry mismatch, wrong-profile configuration,
absent buttons/LED, two-channel bounds, disabled/stale commands, backlight ioctls,
restore behavior and press/hold/half-step decoding. Fake Android preparation
tests cover apply/restore, scope, failed package-disable rollback and policy
refusal. The real A133 has passed only the new worker's read-only layout probe;
its earlier individual hardware tests are recorded in the hardware preset.

No unified APK installation, Tuya disabling, relay/audio test, call or reboot is
performed by the build. Full live unified-app knob actions, touchscreen wake,
receiver cutover, restoration and reboot behavior remain user commissioning
checks. Do not describe this build as production-qualified yet.
