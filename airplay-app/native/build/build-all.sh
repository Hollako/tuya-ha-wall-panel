#!/bin/bash
# Main build script for all native dependencies
# Builds for both arm64-v8a and armeabi-v7a
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ABIs to build
ABIS="arm64-v8a armeabi-v7a"

# Check if specific ABI requested
if [ -n "$1" ]; then
    ABIS="$1"
fi

echo "=========================================="
echo "Building native dependencies"
echo "ABIs: $ABIS"
echo "=========================================="

# Build each dependency for each ABI
for ABI in $ABIS; do
    echo ""
    echo "=========================================="
    echo "Building for ABI: $ABI"
    echo "=========================================="
    
    ./10-openssl.sh $ABI
    ./11-libsodium.sh $ABI
    ./12-libgpg-error.sh $ABI
    ./13-libgcrypt.sh $ABI
    ./14-libxml2.sh $ABI
    ./15-libplist.sh $ABI
    ./16-libconfig.sh $ABI
    ./17-popt.sh $ABI
    ./18-ffmpeg.sh $ABI
    ./19-nqptp.sh $ABI
    ./20-shairport-sync.sh $ABI
    
    echo ""
    echo "✅ All dependencies built for $ABI"
done

echo ""
echo "=========================================="
echo "✅ All builds complete!"
echo "=========================================="
echo ""
echo "Built binaries:"
for ABI in $ABIS; do
    echo ""
    echo "$ABI:"
    ls -lh /root/wpar/build/staging/$ABI/bin/ 2>/dev/null || echo "  (no binaries in bin/)"
    echo "  Libraries:"
    ls /root/wpar/build/staging/$ABI/lib/*.a 2>/dev/null | wc -l | xargs echo "   "
done
