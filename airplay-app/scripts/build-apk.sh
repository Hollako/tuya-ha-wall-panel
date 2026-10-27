#!/bin/sh
set -eu
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/root/wpar/tools/android-sdk
export PATH="$JAVA_HOME/bin:/root/wpar/tools/gradle-8.7/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir/../android"
gradle --no-daemon assembleDebug
