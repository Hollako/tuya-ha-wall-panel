#!/bin/bash
# Build libgcrypt 1.10.3 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="libgcrypt-$LIBGCRYPT_VERSION"
ARCHIVE=$(download_source "libgcrypt" "$LIBGCRYPT_URL" "$LIBGCRYPT_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libgcrypt.a" ]; then
    echo "libgcrypt already built for $ABI"
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
    --disable-asm \
    --disable-nls \
    --with-libgpg-error-prefix="$STAGING_DIR/$ABI" \
    CFLAGS="-Os -fPIC" \
    CPPFLAGS="-I$STAGING_DIR/$ABI/include" \
    LDFLAGS="-L$STAGING_DIR/$ABI/lib"

make -j$(nproc)
make install

echo "✅ libgcrypt built for $ABI"
