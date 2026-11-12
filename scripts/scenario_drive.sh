#!/usr/bin/env bash
# 에뮬레이터 주행 시나리오(약 2분): 출발 → 소나기 → 창문 → 순간 기록(Gleo) → 상향등 → 주차.
# 전제: 앱 설치, 기록 서비스 실행 중, adb forward tcp:8088 tcp:8088
set -uo pipefail
ADB=${ADB:-$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")}
VC=${VC:-http://127.0.0.1:8088}
PKG=com.hour.app
( while true; do curl -s -o /dev/null -X POST "$VC/browser/session?client=hour-cli"; sleep 1; done ) & HB=$!
trap 'kill $HB 2>/dev/null' EXIT
put() { curl -s -o /dev/null -X PUT "$VC$1"; echo "  $(date +%T) $1"; }
fix() { $ADB emu geo fix "$2" "$1" >/dev/null; }   # fix <lat> <lon>
step() { local i=$1 n=$2; python3 -c "f=$i/$n; print(37.4127+(37.4400-37.4127)*f, 127.0947+(127.1400-127.0947)*f)"; }

fix 37.4127 127.0947
put "/vehicle/gear?gear=Drive"; sleep 1
put "/vehicle/controls?speed=80&accelerator=30&brake=0"
for i in $(seq 1 40); do
  read -r lat lon < <(step "$i" 40); fix "$lat" "$lon"
  case $i in
    6)  put "/vehicle/body?front_wiper=High" ;;
    14) put "/vehicle/body?front_wiper=Off" ;;
    16) put "/vehicle/body?window_rl=60" ;;
    22) put "/vehicle/body?window_rl=0" ;;
    24) $ADB shell "am broadcast --user 10 -a SAVE_JOURNEY_MOMENT -p $PKG --es ACTION_PARAM '{\"note\":\"애들이 무지개 봤어\"}'" >/dev/null; echo "  $(date +%T) gleo moment" ;;
    28) put "/vehicle/lights?feature=high_beam&value=true" ;;
    34) put "/vehicle/lights?feature=high_beam&value=false" ;;
  esac
  sleep 3
done
put "/vehicle/controls?speed=0&accelerator=0&brake=100"; sleep 2
put "/vehicle/gear?gear=Park"
echo "scenario done"
