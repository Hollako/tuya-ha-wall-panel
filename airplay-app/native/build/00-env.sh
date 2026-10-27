#!/bin/bash
# Common environment setup for cross-compilation
set -e

# Source versions (fix path: versions.env is in parent directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../versions.env"

# Build directories
BUILD_ROOT="${BUILD_ROOT:-/root/wpar/build}"
SOURCES_DIR="$BUILD_ROOT/sources"
STAGING_DIR="$BUILD_ROOT/staging"

# Create directories
mkdir -p "$SOURCES_DIR" "$STAGING_DIR"

# Supported ABIs
ABIS="arm64-v8a armeabi-v7a"

setup_toolchain() {
    local ABI=$1
    local TOOLCHAIN_DIR="$BUILD_ROOT/toolchains"
    
    case $ABI in
        arm64-v8a)
            export CROSS_TRIPLE=aarch64-linux-musl
            export TOOLCHAIN_NAME=aarch64-linux-musl-cross
            ;;
        armeabi-v7a)
            export CROSS_TRIPLE=armv7l-linux-musleabihf
            export TOOLCHAIN_NAME=armv7l-linux-musleabihf-cross
            ;;
        *)
            echo "Unsupported ABI: $ABI"
            exit 1
            ;;
    esac
    
    # Verify toolchain exists
    if [ ! -d "$TOOLCHAIN_DIR/$TOOLCHAIN_NAME" ]; then
        echo "ERROR: Toolchain not found at $TOOLCHAIN_DIR/$TOOLCHAIN_NAME" >&2
        echo "Please build the toolchain first with musl-cross-make" >&2
        exit 1
    fi
    
    export TOOLCHAIN="$TOOLCHAIN_DIR/$TOOLCHAIN_NAME"
    export PATH="$TOOLCHAIN/bin:$PATH"
    export CC="$TOOLCHAIN/bin/${CROSS_TRIPLE}-gcc"
    export CXX="$TOOLCHAIN/bin/${CROSS_TRIPLE}-g++"
    export AR="$TOOLCHAIN/bin/${CROSS_TRIPLE}-ar"
    export RANLIB="$TOOLCHAIN/bin/${CROSS_TRIPLE}-ranlib"
    export STRIP="$TOOLCHAIN/bin/${CROSS_TRIPLE}-strip"
    export PKG_CONFIG_SYSROOT_DIR="$STAGING_DIR/$ABI"
    export PKG_CONFIG_PATH="$STAGING_DIR/$ABI/lib/pkgconfig"
    
    # Create staging directory
    mkdir -p "$STAGING_DIR/$ABI"/{bin,lib,include}
}

download_source() {
    local name=$1
    local url=$2
    local expected_sha256=$3
    local filename=$(basename "$url")
    
    if [ ! -f "$SOURCES_DIR/$filename" ]; then
        echo "Downloading $name..." >&2
        wget -q -O "$SOURCES_DIR/$filename" "$url" || {
            echo "ERROR: Failed to download $url" >&2
            exit 1
        }
    fi
    
    # Verify SHA256 if provided
    if [ -n "$expected_sha256" ] && [ "$expected_sha256" != "PLACEHOLDER" ]; then
        local actual_sha256=$(sha256sum "$SOURCES_DIR/$filename" | cut -d' ' -f1)
        if [ "$actual_sha256" != "$expected_sha256" ]; then
            echo "SHA256 mismatch for $filename!" >&2
            echo "Expected: $expected_sha256" >&2
            echo "Actual: $actual_sha256" >&2
            exit 1
        fi
    fi
    
    echo "$SOURCES_DIR/$filename"
}

extract_source() {
    local archive=$1
    local dest_dir=$2
    local strip_components=${3:-1}
    
    mkdir -p "$dest_dir"
    
    case "$archive" in
        *.tar.gz|*.tgz)
            tar -xzf "$archive" -C "$dest_dir" --strip-components=$strip_components
            ;;
        *.tar.bz2|*.tbz2)
            tar -xjf "$archive" -C "$dest_dir" --strip-components=$strip_components
            ;;
        *.tar.xz)
            tar -xJf "$archive" -C "$dest_dir" --strip-components=$strip_components
            ;;
        *)
            echo "Unknown archive format: $archive"
            exit 1
            ;;
    esac
}
