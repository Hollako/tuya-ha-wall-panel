#!/bin/bash
# Build zlib (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

ZLIB_VERSION="1.3.1"
ZLIB_URL="https://github.com/madler/zlib/releases/download/v${ZLIB_VERSION}/zlib-${ZLIB_VERSION}.tar.gz"
NAME="zlib-$ZLIB_VERSION"
ARCHIVE=$(download_source "zlib" "$ZLIB_URL" "")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libz.a" ]; then
    echo "zlib already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

CC=$CC CFLAGS="-Os -fPIC" ./configure \
    --prefix="$STAGING_DIR/$ABI" \
    --static

make -j$(nproc)
make install

echo "✅ zlib built for $ABI"
