#!/usr/bin/env bash
#
# Records the README demo screencast and post-processes it into a small, sped-up MP4.
#
# Output: demo-out/pixel-art-demo.mp4  (NOT committed — upload it to GitHub, see README)
#
# Requirements handled automatically:
#   - ffmpeg: pulled in transiently via `npm i ffmpeg-static --no-save` (no system/sudo install)
#   - app server on http://localhost:8280: reused if already running (e.g. `npm run watch`),
#     otherwise a release build is produced and served statically for the duration of the run.
#
# Usage:  bash scripts/record-demo.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."

PORT=8280
TARGET_SECONDS=34   # final clip length; speed-up factor is derived from the raw duration
OUT_WIDTH=1280      # final video width (downscaled with lanczos for a crisp result)
# Crop box (w:h:x:y) removing the Cypress runner chrome (left command-log + top URL bar) from
# the run-mode video, leaving just the app. Measured for the ~2300x1362 video produced by the
# enlarged window in cypress/demo.config.ts. Re-measure (column/row brightness profile of a
# mid-frame) if the window size or runner layout changes.
CROP="1850:1298:450:64"
RAW="demo-out/raw/demo.cy.ts.mp4"
OUT="demo-out/pixel-art-demo.mp4"

mkdir -p demo-out
SERVER_PID=""
cleanup() { [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true; }
trap cleanup EXIT

# 0) Resolve a static ffmpeg binary (no system install / sudo) — needed by the plan generator
#    and the final encode.
echo "==> Ensuring ffmpeg (ffmpeg-static)"
node -e "require('ffmpeg-static')" 2>/dev/null || npm i ffmpeg-static --no-save
FFMPEG="$(node -e "process.stdout.write(require('ffmpeg-static'))")"

# 0b) Decode the example sprite into the drawing plan the demo replays.
echo "==> Generating octopus drawing plan"
node scripts/gen-octopus-plan.js

# 1) Ensure the app is being served on :8280 (reuse running dev server if present).
if curl -sf -o /dev/null "http://localhost:$PORT/index.html"; then
  echo "==> Using app already served on :$PORT"
else
  echo "==> Nothing on :$PORT — building a release bundle and serving it"
  npm run release
  npm run css || echo "    (css step skipped — reusing existing resources/public/css/ui.css)"
  npx --yes http-server resources/public -p "$PORT" -c-1 --silent >/dev/null 2>&1 &
  SERVER_PID=$!
  for _ in $(seq 1 30); do curl -sf -o /dev/null "http://localhost:$PORT/index.html" && break; sleep 1; done
fi

# 2) Record the demo spec to video (Cypress uses its own bundled recorder).
echo "==> Recording demo spec"
rm -f "$RAW"
npx cypress run --browser chrome --config-file cypress/demo.config.ts
[ -f "$RAW" ] || { echo "ERROR: expected recording at $RAW was not produced"; exit 1; }

# 3) Derive the speed-up factor so the final clip is ~TARGET_SECONDS (never slow it down).
DUR="$({ "$FFMPEG" -i "$RAW" 2>&1 || true; } | sed -n 's/.*Duration: \([0-9:.]*\).*/\1/p' | head -1)"
SPEED="$(LC_ALL=C awk -F: -v t="$TARGET_SECONDS" '{s=$1*3600+$2*60+$3; r=s/t; printf "%.4f", (r<1?1:r)}' <<<"$DUR")"
echo "==> Raw duration $DUR -> speed x$SPEED (target ${TARGET_SECONDS}s)"

# 5) Crop the chrome, speed up, trim, lanczos-downscale to OUT_WIDTH, and re-encode to H.264.
echo "==> Encoding $OUT"
"$FFMPEG" -y -i "$RAW" \
  -filter:v "crop=${CROP},setpts=PTS/${SPEED},fps=30,scale=${OUT_WIDTH}:-2:flags=lanczos" -an \
  -t "$TARGET_SECONDS" -c:v libx264 -preset slow -tune animation -pix_fmt yuv420p -crf 20 \
  -movflags +faststart \
  "$OUT"

echo
echo "==> Done: $OUT ($(du -h "$OUT" | cut -f1))"
echo "    Next: upload it to GitHub (drag into a new issue/PR comment or a release),"
echo "    copy the https://github.com/user-attachments/assets/... URL, and paste it into"
echo "    the <video src> in README.md."
