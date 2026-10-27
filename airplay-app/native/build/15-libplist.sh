#!/bin/bash
# Build libplist 2.7.0 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="libplist-$LIBPLIST_VERSION"
ARCHIVE=$(download_source "libplist" "$LIBPLIST_URL" "$LIBPLIST_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libplist-2.0.a" ]; then
    echo "libplist already built for $ABI"
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
    --without-cython \
    CFLAGS="-Os -fPIC" \
    CPPFLAGS="-I$STAGING_DIR/$ABI/include" \
    LDFLAGS="-L$STAGING_DIR/$ABI/lib" \
    LIBXML2_CFLAGS="-I$STAGING_DIR/$ABI/include/libxml2" \
    LIBXML2_LIBS="-L$STAGING_DIR/$ABI/lib -lxml2"

make -j$(nproc)
make install

echo "✅ libplist built for $ABI"
