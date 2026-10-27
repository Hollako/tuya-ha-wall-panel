#!/bin/bash
# Generate a release keystore for signing the APK
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p signing

if [ -f signing/wpar-release.keystore ]; then
    echo "Keystore already exists: signing/wpar-release.keystore"
    echo "To regenerate, delete the existing keystore first."
    exit 0
fi

echo "Generating release keystore..."
echo ""

# Prompt for password
read -s -p "Enter keystore password (min 6 characters): " PASSWORD
echo ""
if [ ${#PASSWORD} -lt 6 ]; then
    echo "Password must be at least 6 characters"
    exit 1
fi

read -s -p "Confirm password: " PASSWORD2
echo ""
if [ "$PASSWORD" != "$PASSWORD2" ]; then
    echo "Passwords do not match"
    exit 1
fi

# Generate keystore
keytool -genkeypair \
    -v \
    -keystore signing/wpar-release.keystore \
    -alias wpar-release \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=Wall Panel Audio Receiver, OU=Home, O=Local, L=Local, ST=Local, C=US"

# Save password to properties file
cat > signing/keystore.properties <<EOF
storeFile=wpar-release.keystore
storePassword=$PASSWORD
keyAlias=wpar-release
keyPassword=$PASSWORD
EOF

chmod 600 signing/keystore.properties

echo ""
echo "✅ Keystore generated successfully!"
echo ""
echo "Files created:"
echo "  - signing/wpar-release.keystore (private key - KEEP SECURE)"
echo "  - signing/keystore.properties (passwords - KEEP SECURE)"
echo ""
echo "⚠️  IMPORTANT:"
echo "  - Back up the keystore file! You need it for all future updates."
echo "  - Do not commit these files to git (they're in .gitignore)"
echo "  - If you lose the keystore, you cannot update the app"
echo ""
echo "Next step: Build the release APK"
echo "  cd android && gradle assembleRelease"
