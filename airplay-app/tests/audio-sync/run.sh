set -eu
cd '/mnt/c/Users/L/Documents/ChatGPT/TV ADB Access/airplay-app'
mkdir -p build/audio-sync-tests
/usr/lib/jvm/java-17-openjdk-amd64/bin/javac -d build/audio-sync-tests ../shared/audio-receiver/src/com/wallpanel/audioreceiver/PresentationClock.java tests/audio-sync/PresentationClockTest.java
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp build/audio-sync-tests com.wallpanel.audioreceiver.PresentationClockTest
python3 tests/audio-sync/test_native_backend.py
