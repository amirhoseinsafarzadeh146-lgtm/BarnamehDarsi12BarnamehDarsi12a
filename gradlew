#!/usr/bin/env sh
# Lightweight bootstrapper for environments where the standard Gradle Wrapper JAR is unavailable.
set -eu
GRADLE_VERSION="8.9"
DIST_NAME="gradle-${GRADLE_VERSION}-bin"
BASE="${GRADLE_USER_HOME:-${HOME:-/data/local/tmp}/.gradle}"
INSTALL_DIR="$BASE/manual-wrapper/$DIST_NAME"
GRADLE_BIN="$INSTALL_DIR/gradle-${GRADLE_VERSION}/bin/gradle"
ZIP="$BASE/manual-wrapper/$DIST_NAME.zip"
mkdir -p "$BASE/manual-wrapper"
if [ ! -x "$GRADLE_BIN" ]; then
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/${DIST_NAME}.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/${DIST_NAME}.zip"
  else
    echo "ERROR: curl or wget is required to download Gradle." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -oq "$ZIP" -d "$INSTALL_DIR"
  else
    echo "ERROR: unzip is required to extract Gradle." >&2
    exit 1
  fi
fi
exec "$GRADLE_BIN" "$@"
