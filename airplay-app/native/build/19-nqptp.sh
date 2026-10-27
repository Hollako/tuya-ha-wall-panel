#!/bin/bash
# Build nqptp 1.2.9 (static)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

ABI=$1
if [ -z "$ABI" ]; then
    echo "Usage: $0 <abi>"
    exit 1
fi

setup_toolchain $ABI

NAME="nqptp-$NQPTP_VERSION"
ARCHIVE=$(download_source "nqptp" "$NQPTP_URL" "$NQPTP_SHA256")
BUILD_DIR="$BUILD_ROOT/build/$ABI/$NAME"

if [ -f "$STAGING_DIR/$ABI/bin/nqptp" ]; then
    echo "nqptp already built for $ABI"
    exit 0
fi

mkdir -p "$BUILD_DIR"
extract_source "$ARCHIVE" "$BUILD_DIR"
cd "$BUILD_DIR"

autoreconf -i

# Fix musl malloc compatibility
export ac_cv_func_malloc_0_nonnull=yes
export ac_cv_func_realloc_0_nonnull=yes

./configure \
    --host=$CROSS_TRIPLE \
    --prefix="$STAGING_DIR/$ABI" \
    --enable-static \
    --disable-shared \
    CFLAGS="-Os -fPIC" \
    LDFLAGS="-static"

make -j$(nproc)
make install

# Strip the binary
$STRIP "$STAGING_DIR/$ABI/bin/nqptp"

echo "✅ nqptp built for $ABI"
