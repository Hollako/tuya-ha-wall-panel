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
