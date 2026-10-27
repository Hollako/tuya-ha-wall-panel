#!/bin/bash
# Build libuuid from util-linux (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

# util-linux version that includes libuuid
UTIL_LINUX_VERSION="2.39.3"
UTIL_LINUX_URL="https://mirrors.edge.kernel.org/pub/linux/utils/util-linux/v2.39/util-linux-${UTIL_LINUX_VERSION}.tar.xz"

NAME="util-linux-$UTIL_LINUX_VERSION"
ARCHIVE=$(download_source "util-linux" "$UTIL_LINUX_URL" "")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libuuid.a" ]; then
    echo "libuuid already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

# Configure only libuuid component
./configure \
    --host=$CROSS_TRIPLE \
    --prefix="$STAGING_DIR/$ABI" \
    --enable-static \
    --disable-shared \
    --without-ncurses \
    --without-ncursesw \
    --without-tinfo \
    --without-slang \
    --without-acl \
    --without-udev \
    --without-systemd \
    --without-systemdsystemunitdir \
    --without-python \
    --without-readline \
    --disable-all-programs \
    --enable-libuuid \
    CFLAGS="-Os -fPIC" \
    LDFLAGS="-static"

# Build only libuuid
make -j$(nproc)
make install

# Strip the library
$STRIP --strip-unneeded "$STAGING_DIR/$ABI/lib/libuuid.a" 2>/dev/null || true

echo "✅ libuuid built for $ABI"
