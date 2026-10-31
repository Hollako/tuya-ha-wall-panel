# One Bluetooth music sender per panel

**Status: not reliably enforced on the tested vendor firmware.** The user could
still connect concurrent senders after deployment. Further work is deferred;
the host-policy checks below do not establish real-device enforcement.

The user chose to retain the currently connected sender until it disconnects.
Multiple devices may remain paired. A second phone, tablet or laptop must not
start a parallel Bluetooth music stream or replace the current connection.

`MusicPhonePolicy` applies to Bluetooth device identities, regardless of device
type. It owns the first connecting/connected music profile; pause and stop of a
song do not release that ownership. Only that device's music disconnection,
profile loss or radio-off releases it. Stale disconnections from rejected devices
cannot clear the current owner. Outgoing requests reserve the slot atomically.

`BluetoothProfiles` seeds the owner from the established music profile on binding
and observes native A2DP sink connection changes, so sender-initiated connections
cannot bypass the app's manual-connect checks. A newcomer is disconnected from
the music profile while the current owner remains connected. Playback metadata
from the rejected sender is ignored by the app observer. Manual and helper-side
connect actions share the same admission check. No pairing is removed, no radio
is toggled, and no automatic takeover or playback request is added.

32 host policy checks cover phone/laptop/tablet senders, disconnecting ownership,
failed reservations, stale events and concurrent requests. Existing incoming
connection assist guards pass. Real two-device playback validation is left to the
user after installation. Root capture, decoder and synchronization code unchanged.
