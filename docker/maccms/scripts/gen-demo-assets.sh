#!/usr/bin/env bash
# generate demo media (posters + mp4) for maccms demo data
set -euo pipefail
cd "$(dirname "$0")/../assets/static"

echo "== posters =="
for i in $(seq 1 8); do
  curl -sL --max-time 30 -o "poster_${i}.jpg" "https://picsum.photos/seed/maccms${i}/600/900" || true
done

echo "== banners =="
for i in $(seq 1 4); do
  curl -sL --max-time 40 -o "banner_${i}.jpg" "https://picsum.photos/seed/maccmsbanner${i}/1280/720" || true
done

echo "== mp4 =="
for i in 1 2 3 4; do
  case $i in
    1) color=0x1a3a5c; freq=410 ;;
    2) color=0xf2c14e; freq=490 ;;
    3) color=0x345a3a; freq=570 ;;
    4) color=0x5c1a3a; freq=650 ;;
  esac
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "color=c=${color}:s=1280x720:r=30:d=8" \
    -f lavfi -i "sine=frequency=${freq}:duration=8" \
    -vf "drawtext=text='Demo ${i}':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2" \
    -c:v libx264 -crf 28 -preset fast -pix_fmt yuv420p \
    -c:a aac -b:a 96k -shortest "demo_${i}.mp4" || true
done

echo "== done =="
ls -la
