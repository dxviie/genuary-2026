#!/usr/bin/env bash
# Compresses the raw renders from `./gradlew renderSiteMedia` into the loops,
# stills and posters the website serves. Run from anywhere:
#
#   ./gradlew optimizeSiteMedia        # or: bash website/scripts/optimize-media.sh
#
# Requires ffmpeg on the PATH.
set -euo pipefail
cd "$(dirname "$0")/../.."

RAW=website/media-raw
OUT=website/static

encode() { # encode <raw.mp4> <out.mp4>
  ffmpeg -hide_banner -loglevel error -y -i "$1" \
    -c:v libx264 -crf 26 -preset slow -pix_fmt yuv420p -movflags +faststart -an "$2"
  echo "wrote $2"
}

still() { # still <raw.mp4> <seconds> <out.png> (scaled to max 800px wide for the galleries)
  ffmpeg -hide_banner -loglevel error -y -ss "$2" -i "$1" \
    -frames:v 1 -vf "scale='min(800,iw)':-2" "$3"
  echo "wrote $3"
}

poster() { # poster <raw.mp4> <seconds> <out.jpg> (full size, shown while the video loads)
  ffmpeg -hide_banner -loglevel error -y -ss "$2" -i "$1" -frames:v 1 -q:v 4 "$3"
  echo "wrote $3"
}

mkdir -p "$OUT/00" "$OUT/02" "$OUT/05" "$OUT/12" "$OUT/13"

encode "$RAW/00-ptpx.mp4"      "$OUT/00/loop.mp4"
poster "$RAW/00-ptpx.mp4" 3    "$OUT/00/poster.jpg"
still  "$RAW/00-ptpx.mp4" 3    "$OUT/00/still-1.png"
still  "$RAW/00-ptpx.mp4" 8    "$OUT/00/still-2.png"
still  "$RAW/00-ptpx.mp4" 13   "$OUT/00/still-3.png"

encode "$RAW/02-animation.mp4"    "$OUT/02/loop.mp4"
poster "$RAW/02-animation.mp4" 4  "$OUT/02/poster.jpg"
still  "$RAW/02-animation.mp4" 4  "$OUT/02/still-1.png"
still  "$RAW/02-animation.mp4" 9  "$OUT/02/still-2.png"
still  "$RAW/02-animation.mp4" 15 "$OUT/02/still-3.png"

encode "$RAW/05-no-font.mp4"      "$OUT/05/loop.mp4"
poster "$RAW/05-no-font.mp4" 9    "$OUT/05/poster.jpg"
still  "$RAW/05-no-font.mp4" 9    "$OUT/05/still-1.png"
still  "$RAW/05-no-font.mp4" 12.5 "$OUT/05/still-2.png"
still  "$RAW/05-no-font.mp4" 16   "$OUT/05/still-3.png"

encode "$RAW/12-boxes.mp4"     "$OUT/12/loop.mp4"
poster "$RAW/12-boxes.mp4" 6   "$OUT/12/poster.jpg"
still  "$RAW/12-boxes.mp4" 6   "$OUT/12/still-1.png"
still  "$RAW/12-boxes.mp4" 11  "$OUT/12/still-2.png"
still  "$RAW/12-boxes.mp4" 16  "$OUT/12/still-3.png"

encode "$RAW/13-hero.mp4"      "$OUT/13/hero.mp4"
poster "$RAW/13-hero.mp4" 5    "$OUT/13/hero-poster.jpg"
still  "$RAW/13-hero.mp4" 5    "$OUT/13/still-1.png"
still  "$RAW/13-hero.mp4" 10   "$OUT/13/still-2.png"

encode "$RAW/13-bauhaus.mp4"    "$OUT/13/bauhaus.mp4"
poster "$RAW/13-bauhaus.mp4" 1  "$OUT/13/bauhaus-poster.jpg"
still  "$RAW/13-bauhaus.mp4" 1  "$OUT/13/still-3.png"

echo "done."
