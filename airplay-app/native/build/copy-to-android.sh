#!/bin/bash
# Copy built native binaries to Android jniLibs directory
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/00-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ANDROID_JNILIBS="$PROJECT_ROOT/android/app/src/main/jniLibs"

echo "=========================================="
echo "Copying native binaries to Android"
echo "=========================================="

# Create jniLibs directories
mkdir -p "$ANDROID_JNILIBS/arm64-v8a"
mkdir -p "$ANDROID_JNILIBS/armeabi-v7a"

# Copy binaries for each ABI
for ABI in arm64-v8a armeabi-v7a; do
    echo ""
    echo "Copying for $ABI..."
    
    # nqptp
    if [ -f "$STAGING_DIR/$ABI/bin/nqptp" ]; then
        cp "$STAGING_DIR/$ABI/bin/nqptp" "$ANDROID_JNILIBS/$ABI/libwpar_nqptp.so"
        chmod +x "$ANDROID_JNILIBS/$ABI/libwpar_nqptp.so"
        echo "  ✓ libwpar_nqptp.so"
    else
        echo "  ✗ nqptp not found"
    fi
    
    # shairport-sync
    if [ -f "$STAGING_DIR/$ABI/bin/shairport-sync" ]; then
        cp "$STAGING_DIR/$ABI/bin/shairport-sync" "$ANDROID_JNILIBS/$ABI/libwpar_shairport.so"
        chmod +x "$ANDROID_JNILIBS/$ABI/libwpar_shairport.so"
        echo "  ✓ libwpar_shairport.so"
    else
        echo "  ✗ shairport-sync not found"
    fi
done

echo ""
echo "=========================================="
echo "✅ Binaries copied to Android jniLibs"
echo "=========================================="
echo ""
echo "Files in jniLibs:"
find "$ANDROID_JNILIBS" -type f -exec ls -lh {} \;
