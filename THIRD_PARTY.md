# Third-party components

Project-created source and original icon artwork are distinct from third-party
dependencies. No project-wide open-source license has been selected yet; making
the repository public does not change the licenses of bundled dependencies.

## Android libraries

Pinned unmodified build inputs are under `unified-app/vendor`:

- Android JAIN SIP RI (`android-jain-sip-ri` 1.3.0-91), upstream:
  https://github.com/usnistgov/jsip. Preserve its upstream notices and license.
- SLF4J 1.7.36, including its Android binding and log4j bridge, upstream:
  https://www.slf4j.org/license.html. Preserve artifact-specific licenses.
- AndroidX Media3 1.4.1 and supporting AndroidX, Guava and Kotlin libraries.
  See `unified-app/assets/licenses/media3/NOTICE.txt`, `APACHE-2.0.txt` and
  `DEPENDENCIES.json` for the pinned dependency set and notices.

`unified-app/build.py` extracts packaged dependency LICENSE/NOTICE and Maven
metadata into APK assets. Dependency hashes are validated before compilation.
No reverse-engineered Tuya APKs, proprietary firmware or vendor signing keys are
included in this repository.

## AirPlay receiver native components

The receiver uses Shairport Sync 5.6.1 and nqptp 1.2.9, with the same native
payloads in the unified and dedicated products. The release asset
`receiver-native-source-and-relink-1.2.0.tar.gz` contains matching dependency source
archives, patched/configured receiver trees, build recipes and relinkable objects
and static libraries for both ABIs. Preserve the included original notices.

| Component | Linked version | Upstream license / notice |
| --- | --- | --- |
| Shairport Sync | 5.6.1 | MIT-style; individual file notices also apply |
| nqptp | 1.2.9 | GPL-2.0; matching modified source is included |
| FFmpeg | n8.1 | LGPL-2.1-or-later configuration; source/configuration included |
| OpenSSL | 3.0.13 | Apache-2.0 |
| libsodium | 1.0.19 | ISC |
| libconfig | 1.7.3 | LGPL-2.1 |
| popt | 1.19 | MIT-style notices |
| libxml2 | 2.11.7 | MIT |
| libplist | 2.3.0 | LGPL-2.1 library; source tooling has separate notices |
| libgpg-error | 1.47 | LGPL-2.1 library; source tooling has separate notices |
| libgcrypt | 1.10.3 | LGPL-2.1 |
| libuuid (util-linux) | 2.39.3 | Modified BSD for libuuid |
| zlib | 1.3.1 | zlib |
| Musl / compiler runtime | ABI-specific toolchain | Preserve runtime-specific notices and exceptions |

Versioned source URLs are recorded in `airplay-app/native/versions.env`; the
release archive records checksums of its source archives and binaries. These
components retain their own licenses; the table does not replace their texts.
