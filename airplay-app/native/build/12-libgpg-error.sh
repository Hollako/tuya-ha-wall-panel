#!/bin/bash
# Build libgpg-error 1.51 (static, required by libgcrypt)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="libgpg-error-$LIBGPG_ERROR_VERSION"
ARCHIVE=$(download_source "libgpg-error" "$LIBGPG_ERROR_URL" "$LIBGPG_ERROR_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libgpg-error.a" ]; then
    echo "libgpg-error already built for $ABI"
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
    --disable-nls \
    --disable-languages \
    CFLAGS="-Os -fPIC"

make -j$(nproc)
make install

echo "✅ libgpg-error built for $ABI"
