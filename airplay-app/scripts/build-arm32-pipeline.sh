#!/bin/sh
set -eu
cd '/mnt/c/Users/L/Documents/ChatGPT/TV ADB Access/airplay-app'
python3 scripts/build-arm32-dependencies.py
python3 scripts/build-isolated-native.py armeabi-v7a
