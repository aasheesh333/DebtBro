#!/usr/bin/env sh
set -e
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

PROPS_FILE="$BASE_DIR/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$PROPS_FILE" ]; then
  echo "ERROR: $PROPS_FILE not found" >&2
  exit 2
fi

DIST_URL=$(awk -F= '/^distributionUrl=/ {sub(/^distributionUrl=/, ""); print}' "$PROPS_FILE")
DIST_URL=$(printf '%s' "$DIST_URL" | sed 's/\\:/:/g; s/\\//g; s/\\//')
ZIP_NAME=$(basename "$DIST_URL" .zip)

GRADLE_VERSION=$(printf '%s' "$ZIP_NAME" | sed 's/-bin$//;s/-all$//;s/-src$//')
DIST_DIR="$BASE_DIR/.gradle/wrapper/dists/$ZIP_NAME"
GRADLE_HOME="$DIST_DIR/$GRADLE_VERSION"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  ZIP="$DIST_DIR/$ZIP_NAME.zip"
  if [ ! -f "$ZIP" ]; then
    curl -L "$DIST_URL" -o "$ZIP"
  fi
  unzip -q "$ZIP" -d "$DIST_DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
