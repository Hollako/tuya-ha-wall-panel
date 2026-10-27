#!/bin/bash
# Build OpenSSL 3.5.4 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="openssl-$OPENSSL_VERSION"
ARCHIVE=$(download_source "OpenSSL" "$OPENSSL_URL" "$OPENSSL_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/lib/libssl.a" ]; then
    echo "OpenSSL already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

# OpenSSL configure for cross-compilation
case $ABI in
    arm64-v8a)
        OPENSSL_TARGET=linux-aarch64
        ;;
    armeabi-v7a)
        OPENSSL_TARGET=linux-armv4
        ;;
esac

./Configure \
    $OPENSSL_TARGET \
    --prefix="$STAGING_DIR/$ABI" \
    --openssldir="$STAGING_DIR/$ABI/ssl" \
    no-shared \
    no-tests \
    no-ssl3 \
    no-dtls \
    -Os \
    -fPIC \
    $CFLAGS

make -j$(nproc)
make install_sw

echo "✅ OpenSSL built for $ABI"
