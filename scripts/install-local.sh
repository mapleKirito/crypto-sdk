#!/bin/bash
# Crypto SDK Install Script for Linux/Mac
# Usage: ./install-local.sh [version] [sdk_dir]

set -e

SDK_VERSION=${1:-1.0.0}
SDK_DIR=${2:-$(dirname "$0")}

echo "========================================"
echo "Crypto SDK Installer for Linux/Mac"
echo "========================================"
echo "Version: $SDK_VERSION"
echo "Directory: $SDK_DIR"
echo ""

cd "$SDK_DIR"

echo "[INFO] Checking Java version..."
java -version 2>&1 | head -n 1

echo ""
echo "[INFO] Checking Maven version..."
mvn --version | head -n 1

echo ""
echo "[INFO] Building and installing Crypto SDK..."
echo ""

mvn clean install \
    -DskipTests \
    -Dmaven.javadoc.skip=true \
    -Dmaven.source.skip=true \
    -Dversion=$SDK_VERSION

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "Installation Successful!"
    echo "========================================"
    echo ""
    echo "To use in your business system, add this dependency to pom.xml:"
    echo ""
    echo "<dependency>"
    echo "    <groupId>com.ai.extender</groupId>"
    echo "    <artifactId>crypto-sdk</artifactId>"
    echo "    <version>$SDK_VERSION</version>"
    echo "</dependency>"
    echo ""
    echo "Or use system scope for direct JAR reference:"
    echo ""
    echo "<dependency>"
    echo "    <groupId>com.ai.extender</groupId>"
    echo "    <artifactId>crypto-sdk</artifactId>"
    echo "    <version>$SDK_VERSION</version>"
    echo "    <scope>system</scope>"
    echo "    <systemPath>\${project.basedir}/lib/crypto-sdk-$SDK_VERSION.jar</systemPath>"
    echo "</dependency>"
    echo ""
else
    echo ""
    echo "========================================"
    echo "Installation Failed!"
    echo "========================================"
    echo "Please check the error messages above."
    exit 1
fi
