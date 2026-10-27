#!/bin/bash
# Build FFmpeg n8.1 (static, lite configuration)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="FFmpeg-$FFMPEG_VERSION"
ARCHIVE=$(download_source "FFmpeg" "$FFMPEG_URL" "$FFMPEG_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libavcodec.a" ]; then
    echo "FFmpeg already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

case $ABI in
    arm64-v8a)
        FFMPEG_ARCH=aarch64
        FFMPEG_CPU=armv8-a
        ;;
    armeabi-v7a)
        FFMPEG_ARCH=arm
        FFMPEG_CPU=armv7-a
        ;;
esac

./configure \
    --prefix="$STAGING_DIR/$ABI" \
    --enable-cross-compile \
    --cross-prefix="${CROSS_TRIPLE}-" \
    --arch=$FFMPEG_ARCH \
    --cpu=$FFMPEG_CPU \
    --target-os=linux \
    --enable-static \
    --disable-shared \
    --disable-programs \
    --disable-doc \
    --disable-avdevice \
    --disable-avfilter \
    --disable-network \
    --disable-everything \
    --enable-decoder=alac \
    --enable-decoder=aac \
    --enable-decoder=aac_latm \
    --enable-parser=aac \
    --enable-parser=aac_latm \
    --enable-parser=alac \
    --enable-demuxer=aac \
    --enable-protocol=file \
    --enable-protocol=pipe \
    --disable-muxers \
    --disable-encoders \
    --disable-filters \
    --disable-bsfs \
    --disable-indevs \
    --disable-outdevs \
    --extra-cflags="-Os -fPIC -I$STAGING_DIR/$ABI/include" \
    --extra-ldflags="-L$STAGING_DIR/$ABI/lib"

make -j$(nproc)
make install

echo "✅ FFmpeg built for $ABI"
