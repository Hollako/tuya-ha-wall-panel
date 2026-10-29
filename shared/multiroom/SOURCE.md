# Independent group engine

The bundled standalone group server/client processes are built from Snapcast
0.34.0, revision `ee0f896cd02c474bf18c6cd9f10b0b7c8b6dd009`:
https://github.com/snapcast/snapcast/tree/ee0f896cd02c474bf18c6cd9f10b0b7c8b6dd009

They are separate programs communicating over TCP, not linked into the Android
application or AirPlay engine. Snapcast is GPL-3.0-or-later; see COPYING. Build
instructions/custom CMake generation are in scripts/build_native.py. The exact
upstream source archive is retained under vendor/ for binary release packaging.
Any public binary release must provide the corresponding source and build recipe.

Group output now uses Google Oboe 1.9.3, revision
`b15f5e39c01a7ada306d959e5129620b145fb8b4` (Apache-2.0; see OBOE-LICENSE):
https://github.com/google/oboe/tree/b15f5e39c01a7ada306d959e5129620b145fb8b4
The group-only reproducible changes are in scripts/patch_group_timing.py and
native-src/group-output-timing.h. They use presentation timestamps when available,
with queue/HAL latency estimates otherwise, and enable TCP_NODELAY for timing
messages. The retained corresponding-source archive includes both source trees,
these changes and the build recipe.
The v2 group patch also adds bounded clock-request retries, stale-connection
callback guards and a private live room-timing control file. No changes are made
to AirPlay decoding, clock synchronization or output code.

Build prerequisites: Android NDK r27c, CMake 3.28, and Boost 1.83 headers. The pinned
Ubuntu header package is libboost1.83-dev_1.83.0-2.1ubuntu3.2_amd64.deb, SHA-256
519ecf2c64308527e15b6582955681d192a832250baa4bc424967aaf7d02d68f.
Boost headers are under the Boost Software License; Android system libraries and
the NDK C++ runtime retain their respective licenses.
