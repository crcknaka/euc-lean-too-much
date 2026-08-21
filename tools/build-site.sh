#!/bin/bash
#
# Assemble the public site: the landing page at the root, the game under /play/.
#
# The game is kept behind a link rather than embedded in the landing page on purpose - the
# browser backend preloads every asset before the first frame, so opening the game directly
# would mean tens of seconds of loading bar before a visitor knows what the site even is.
#
# Output: build/site  (a plain static directory - `vercel deploy build/site` and nothing else)
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)
OUT="$ROOT/build/site"
GAME="$ROOT/web/build/dist/js/webapp"

echo "==> Building the web game"
./gradlew :web:gdx_teavm_web_js_build -q

if [ ! -f "$GAME/index.html" ]; then
  echo "!! game build missing at $GAME" >&2
  exit 1
fi

echo "==> Assembling $OUT"
rm -rf "$OUT"
mkdir -p "$OUT/play" "$OUT/fonts"

cp "$ROOT/landing/index.html" "$OUT/index.html"
cp "$ROOT/landing/vercel.json" "$OUT/vercel.json"
# Shared with the game rather than duplicated in the repo: same key art, same icon, same face
cp "$ROOT/assets/background.jpg" "$OUT/hero.jpg"
cp "$ROOT/assets/icon.jpg" "$OUT/icon.jpg"
cp "$ROOT/assets/fonts/Exo2-SemiBold.ttf" "$OUT/fonts/Exo2-SemiBold.ttf"

cp -R "$GAME/." "$OUT/play/"

echo "==> Mirroring the published APK"
# Served from this origin as well as from GitHub: Chrome on Android stalled at 100% on the
# GitHub download's redirect chain, and a same-origin link also lets the download attribute
# work. The release stays the single source of truth - this is a copy of it, not a second build.
if gh release download -R crcknaka/euc-lean-too-much -p EUC-Rider.apk -D "$OUT" --clobber > /dev/null 2>&1; then
  sed -i '' 's#https://github.com/crcknaka/euc-lean-too-much/releases/latest/download/EUC-Rider.apk#EUC-Rider.apk#' "$OUT/index.html"
  echo "    mirrored $(du -h "$OUT/EUC-Rider.apk" | cut -f1)"
else
  echo "    no published APK found - the download button stays on GitHub"
fi

echo "==> Done: $(du -sh "$OUT" | cut -f1) in $OUT"
echo "    deploy with: vercel deploy \"$OUT\" --prod"
