# Build on this existing host

This is an incremental build using existing WSL Ubuntu caches under /root/wpar. It does not claim a fully tested clean-host build.

From Windows, run WSL as root (the staging directories are root-owned):

~~~powershell
wsl -d Ubuntu -u root -- python3 "/mnt/c/Users/L/Documents/ChatGPT/TV ADB Access/airplay-app/scripts/build-isolated-native.py"
wsl -d Ubuntu -u root -- sh "/mnt/c/Users/L/Documents/ChatGPT/TV ADB Access/airplay-app/scripts/build-apk.sh"
~~~

Native build uses the existing aarch64-linux-musl GCC toolchain and dependency staging. It copies configured nqptp/Shairport sources into this working copy's build/native-work, injects the nqptp privilege drop, links both shm adapters and applies both mDNS patches. Outputs are stripped ARM64 static executables in build/native.

Shairport is copied to android/app/src/main/jniLibs/arm64-v8a/libwpar_shairport.so. nqptp is intentionally deployed separately as a root-owned executable; it is not app-launched from the APK.

APK: android/app/build/outputs/apk/debug/app-debug.apk. Built successfully with existing debug signing identity. Build logs: build-native.log and build-apk.log. Do not regenerate signing identities during an update.

Pinned source/dependency versions originate in native/versions.env. Original native/build scripts remain historical and are not the tested entry point for the Android shared-memory and privilege patches.

## ARM32 and dual-ABI build

Verified on A133 Android11. Fetch/verify/activate the pinned Bootlin toolchain using scripts/fetch-arm32-toolchain.py and activate-arm32-toolchain.py, then run scripts/build-arm32-pipeline.sh in WSL as root. These reuse the pinned source archives under /root/wpar/build/sources, build isolated armeabi-v7a dependencies and apply the same shared-memory, privilege-drop and mDNS patches. ARM32 output is build/native/armeabi-v7a/. ARM64 output remains build/native/.

The current Gradle configuration includes both ABIs and builds debug version1.0.2. Provision the matching helper/nqptp for the actual device ABI; the APK alone cannot replace root-owned executables.

## 1.1.0 synchronization backend

Rebuild both ABIs with scripts/build-isolated-native.py before scripts/build-apk.sh. The script copies native/wpar-android-output.c into the compiled pipe slot and patches RTP latency anchor reads to use live feedback. The registered backend name is android, not pipe. Only the APK-bundled Shairport executable is needed for this update; do not deploy rebuilt root helper/nqptp copies as part of it. Existing device PTP service and provisioning remain unchanged.
