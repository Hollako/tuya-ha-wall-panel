#!/bin/bash
# Build libconfig 1.7.3 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="libconfig-$LIBCONFIG_VERSION"
ARCHIVE=$(download_source "libconfig" "$LIBCONFIG_URL" "$LIBCONFIG_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libconfig.a" ]; then
    echo "libconfig already built for $ABI"
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
    --disable-cxx \
    CFLAGS="-Os -fPIC"

make -j$(nproc)
make install

echo "✅ libconfig built for $ABI"
