#!/usr/bin/env sh
set -e
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_VERSION="8.7"
DIST_DIR="$BASE_DIR/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  fi
  unzip -q "$ZIP" -d "$DIST_DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
