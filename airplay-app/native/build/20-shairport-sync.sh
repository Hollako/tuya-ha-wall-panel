#!/bin/bash
# Build shairport-sync 5.6.1 (static, AirPlay 2)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="shairport-sync-$SHAIRPORT_SYNC_VERSION"
ARCHIVE=$(download_source "shairport-sync" "$SHAIRPORT_SYNC_URL" "$SHAIRPORT_SYNC_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/bin/shairport-sync" ]; then
    echo "shairport-sync already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

autoreconf -i

./configure \
    --host=$CROSS_TRIPLE \
    --prefix="$STAGING_DIR/$ABI" \
    --with-airplay-2 \
    --with-ssl=openssl \
    --with-pipe \
    --with-stdout \
    --with-tinysvcmdns \
    --with-metadata \
    --with-metadata-pipe \
    --with-dummy \
    --without-alsa \
    --without-pa \
    --without-pulseaudio \
    --without-avahi \
    --without-dbus \
    --without-mqtt \
    --without-soxr \
    --without-mpris \
    --without-convolution \
    CFLAGS="-Os -fPIC -I$STAGING_DIR/$ABI/include" \
    CPPFLAGS="-I$STAGING_DIR/$ABI/include" \
    LDFLAGS="-static -L$STAGING_DIR/$ABI/lib" \
    PKG_CONFIG_PATH="$STAGING_DIR/$ABI/lib/pkgconfig" \
    OPENSSL_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    OPENSSL_LIBS="-L$STAGING_DIR/$ABI/lib -lssl -lcrypto" \
    SODIUM_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    SODIUM_LIBS="-L$STAGING_DIR/$ABI/lib -lsodium" \
    LIBGCRYPT_CONFIG="$STAGING_DIR/$ABI/bin/libgcrypt-config" \
    LIBGCRYPT_LIBS="-L$STAGING_DIR/$ABI/lib -lgcrypt -lgpg-error" \
    LIBPLIST_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    LIBPLIST_LIBS="-L$STAGING_DIR/$ABI/lib -lplist-2.0 -lxml2" \
    LIBCONFIG_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    LIBCONFIG_LIBS="-L$STAGING_DIR/$ABI/lib -lconfig" \
    POPT_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    POPT_LIBS="-L$STAGING_DIR/$ABI/lib -lpopt" \
    FFMPEG_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    FFMPEG_LIBS="-L$STAGING_DIR/$ABI/lib -lavcodec -lavformat -lavutil -lswresample" \
    LIBXML2_CFLAGS="-I$STAGING_DIR/$ABI/include/libxml2" \
    LIBXML2_LIBS="-L$STAGING_DIR/$ABI/lib -lxml2" \
    UUID_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    UUID_LIBS="-L$STAGING_DIR/$ABI/lib -luuid" \
    GPG_ERROR_CFLAGS="-I$STAGING_DIR/$ABI/include" \
    GPG_ERROR_LIBS="-L$STAGING_DIR/$ABI/lib -lgpg-error"

make -j$(nproc) LIBS="-lgcrypt -lgpg-error -lsodium -lssl -lcrypto -lplist-2.0 -lxml2 -lconfig -lpopt -lavcodec -lavformat -lavutil -lswresample -luuid -lz -lm -lpthread"
make install

# Strip the binary
$STRIP "$STAGING_DIR/$ABI/bin/shairport-sync"

echo "✅ shairport-sync built for $ABI"
