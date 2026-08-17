#!/bin/bash
#
# Publish the Android build as a GitHub release, which is where both the landing page's
# download button and the in-app updater look.
#
# Two files go up: the signed APK, and a small latest.json the game polls on startup. The
# /releases/latest/download/<name> URLs are stable, so neither the site nor a shipped build
# ever needs to know the version number.
#
# The APK is built here rather than in CI on purpose: the signing keystore lives on this
# machine only (android/keystore.properties, git-ignored) and does not travel to repository
# secrets. Releasing from a laptop keeps it that way.
#
#   ./tools/release-apk.sh              publish
#   ./tools/release-apk.sh --dry        show what would be published, touch nothing
#
# Bump versionCode and versionName in android/build.gradle.kts before releasing - the updater
# compares versionCode, so a build published without bumping it will not be offered to anyone.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)
REPO="crcknaka/euc-lean-too-much"
DRY=${1:-}

GRADLE=android/build.gradle.kts
VERSION_CODE=$(grep -E '^\s*versionCode\s*=' "$GRADLE" | head -1 | sed 's/[^0-9]//g')
VERSION_NAME=$(grep -E '^\s*versionName\s*=' "$GRADLE" | head -1 | sed 's/.*"\(.*\)".*/\1/')
TAG="v${VERSION_NAME}+${VERSION_CODE}"

[ -n "$VERSION_CODE" ] && [ -n "$VERSION_NAME" ] || { echo "!! could not read the version from $GRADLE" >&2; exit 1; }

if gh release view "$TAG" -R "$REPO" > /dev/null 2>&1; then
  echo "!! $TAG is already published - bump versionCode/versionName in $GRADLE first" >&2
  exit 1
fi

echo "==> Building signed release APK ($VERSION_NAME, code $VERSION_CODE)"
./gradlew :android:assembleRelease -q
APK=android/build/outputs/apk/release/android-release.apk
[ -f "$APK" ] || { echo "!! no APK at $APK" >&2; exit 1; }

# Named for the player, not for Gradle: this is what lands in their Downloads folder
STAGE="$ROOT/build/release"
rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$APK" "$STAGE/EUC-Rider.apk"

NOTES=${RELEASE_NOTES:-"See the commit history for what changed."}
cat > "$STAGE/latest.json" <<JSON
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "notes": $(printf '%s' "$NOTES" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'),
  "apk": "https://github.com/$REPO/releases/latest/download/EUC-Rider.apk"
}
JSON

echo "==> Prepared:"
ls -la "$STAGE"
cat "$STAGE/latest.json"

if [ "$DRY" = "--dry" ]; then
  echo "==> --dry: nothing published"
  exit 0
fi

echo "==> Publishing $TAG to $REPO"
gh release create "$TAG" \
  "$STAGE/EUC-Rider.apk" \
  "$STAGE/latest.json" \
  -R "$REPO" \
  --title "EUC Rider $VERSION_NAME" \
  --notes "$NOTES"

echo "==> Done"
echo "    APK:  https://github.com/$REPO/releases/latest/download/EUC-Rider.apk"
echo "    Meta: https://github.com/$REPO/releases/latest/download/latest.json"
