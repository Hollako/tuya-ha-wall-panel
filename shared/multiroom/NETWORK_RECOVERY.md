# Group network recovery

Unified 0.32.0 and Audio Receiver 1.9.0 share the same recovery code.

- Room IDs remain stable when DHCP changes an address. Discovery updates the endpoint;
  HTTP must confirm the advertised room ID before an active receiver follows it.
- Discovery is refreshed when the selected interface, IPv4 address, Android network,
  or process network binding changes. Unreachable saved rooms trigger another discovery
  pass at most once every 30 seconds.
- Only the affected room's native receiver is replaced. Leader capture/decoding,
  native synchronization, room timing, volume, mute, pause and intercom state are retained.
  A disconnected receiver process retries with 0.5 to 15 second backoff until stopped.
- The HTTP listener reopens after a network change without restarting the receiver
  service or its AirPlay pipeline. Existing HTTP connections finish independently.
- A stopped group remains stopped. This does not automatically resume playback after
  Android reboots, change a fixed interface preference, or bridge isolated VLANs.
- Existing API credentials stay pinned until a moved room proves possession of its
  existing token using a fresh nonce and HMAC-SHA256 over its permanent ID. Only then
  is the approved address updated. An mDNS announcement alone cannot redirect a token.
  Changing the token itself, or moving to older firmware without identity proofs,
  still needs explicit pairing.

Host verification: `python shared/multiroom/test_network_recovery.py` exercises stable-ID
address updates, retry bounds, rejected endpoints, and real listener rebind/shutdown races.
Physical network-change/playback checks are left to the user.
