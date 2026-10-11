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

for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  set -- $(density_sizes "$d")
  LEGACY=$1
  ADAPTIVE=$2
  OUT="app/src/main/res/mipmap-$d"
  mkdir -p "$OUT"

  # Legacy (pre-Android 8) square + circular icons.
  "$IMG" "$SRC" -resize "${LEGACY}x${LEGACY}" "$OUT/ic_launcher.png"
  "$IMG" "$SRC" -resize "${LEGACY}x${LEGACY}" -alpha set \
    \( -size "${LEGACY}x${LEGACY}" xc:none -fill white \
       -draw "circle $((LEGACY/2)),$((LEGACY/2)) $((LEGACY/2)),0" \) \
    -compose DstIn -composite "$OUT/ic_launcher_round.png"

  # Adaptive icon: full-bleed artwork as the background, empty foreground on top.
  "$IMG" "$SRC" -resize "${ADAPTIVE}x${ADAPTIVE}" "$OUT/ic_launcher_background.png"
  "$IMG" -size "${ADAPTIVE}x${ADAPTIVE}" xc:none "$OUT/ic_launcher_foreground.png"
done

echo "launcher icons generated"
