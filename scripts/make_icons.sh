#!/usr/bin/env bash
# Generates every launcher icon density from assets/app_icon.png.
# Runs on the CI runner (which ships ImageMagick) before Gradle builds the APK.
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="assets/app_icon.png"
[ -f "$SRC" ] || { echo "missing $SRC"; exit 1; }

if command -v magick >/dev/null 2>&1; then
  IMG="magick"
elif command -v convert >/dev/null 2>&1; then
  IMG="convert"
else
  echo "ImageMagick not found"; exit 1
fi

density_sizes() {
  case "$1" in
    mdpi)   echo "48 108" ;;
    hdpi)   echo "72 162" ;;
    xhdpi)  echo "96 216" ;;
    xxhdpi) echo "144 324" ;;
    xxxhdpi) echo "192 432" ;;
  esac
}

# Adaptive icons show only the middle ~66/108 of the canvas, so the artwork has to be
# scaled down into that safe zone or it reads as "zoomed in" on modern launchers.
SAFE_PERCENT=60

for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  set -- $(density_sizes "$d")
  LEGACY=$1
  ADAPTIVE=$2
  SAFE=$((ADAPTIVE * SAFE_PERCENT / 100))
  OUT="app/src/main/res/mipmap-$d"
  mkdir -p "$OUT"

  # Legacy (pre-Android 8) square icon keeps the full artwork.
  "$IMG" "$SRC" -resize "${LEGACY}x${LEGACY}" "$OUT/ic_launcher.png"

  # Legacy circular icon: shrink a touch first so the round mask never clips the logo.
  "$IMG" "$SRC" -resize "$((LEGACY * 82 / 100))x$((LEGACY * 82 / 100))" \
    -gravity center -background none -extent "${LEGACY}x${LEGACY}" -alpha set \
    \( -size "${LEGACY}x${LEGACY}" xc:none -fill white \
       -draw "circle $((LEGACY/2)),$((LEGACY/2)) $((LEGACY/2)),0" \) \
    -compose DstIn -composite "$OUT/ic_launcher_round.png"

  # Adaptive icon: an ambient blurred backdrop plus the logo centred inside the safe zone.
  "$IMG" "$SRC" -resize "${ADAPTIVE}x${ADAPTIVE}^" -gravity center \
    -extent "${ADAPTIVE}x${ADAPTIVE}" -blur 0x18 "$OUT/ic_launcher_background.png"
  "$IMG" "$SRC" -resize "${SAFE}x${SAFE}" \
    -gravity center -background none -extent "${ADAPTIVE}x${ADAPTIVE}" \
    "$OUT/ic_launcher_foreground.png"
done

echo "launcher icons generated"