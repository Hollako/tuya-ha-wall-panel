#!/bin/bash
# Build libsodium 1.0.20 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="libsodium-$LIBSODIUM_VERSION"
ARCHIVE=$(download_source "libsodium" "$LIBSODIUM_URL" "$LIBSODIUM_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libsodium.a" ]; then
    echo "libsodium already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

./configure \
    --host=$CROSS_TRIPLE \
    --prefix="$STAGING_DIR/$ABI" \
    --enable-static \
    --disable-shared \
    --disable-ssp \
    --disable-asm \
    CFLAGS="-Os -fPIC"

make -j$(nproc)
make install

echo "✅ libsodium built for $ABI"
