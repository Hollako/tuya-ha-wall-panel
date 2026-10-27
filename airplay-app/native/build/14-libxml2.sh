#!/bin/bash
# Build libxml2 2.12.10 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="libxml2-$LIBXML2_VERSION"
ARCHIVE=$(download_source "libxml2" "$LIBXML2_URL" "$LIBXML2_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libxml2.a" ]; then
    echo "libxml2 already built for $ABI"
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
    --without-python \
    --without-icu \
    --without-lzma \
    --without-zlib \
    CFLAGS="-Os -fPIC"

make -j$(nproc)
make install

echo "✅ libxml2 built for $ABI"
