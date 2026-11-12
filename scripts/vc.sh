#!/usr/bin/env bash
# Pleos Connect 에뮬레이터 Virtual Console HTTP 제어 (D1에서 확인한 엔드포인트)
# 사용 예: scripts/vc.sh gear Drive | speed 60 | wiper High | highbeam true | window rl 60
#          scripts/vc.sh seat passenger false | seatheat driver 2 | defrost true | odo 1234
# 전제: adb forward tcp:8088 tcp:8088
set -euo pipefail
VC=${VC:-http://127.0.0.1:8088}
hb() { curl -s -o /dev/null -X POST "$VC/browser/session?client=hour-cli"; }
put() { hb; curl -s -o /dev/null -w "%{http_code} PUT $1\n" -X PUT "$VC$1"; }
case "${1:-}" in
  gear)     put "/vehicle/gear?gear=$2" ;;                                   # Park|Reverse|Neutral|Drive
  speed)    put "/vehicle/controls?speed=$2&accelerator=${3:-10}&brake=0" ;;
  stop)     put "/vehicle/controls?speed=0&accelerator=0&brake=100" ;;
  wiper)    put "/vehicle/body?front_wiper=$2" ;;                            # Off|Auto|Low|High
  highbeam) put "/vehicle/lights?feature=high_beam&value=$2" ;;              # true|false
  headlamp) put "/vehicle/lights?feature=headlamp_mode&value=$2" ;;          # Off|Auto|Position|LowBeam
  window)   put "/vehicle/body?window_$2=$3" ;;                              # fl|fr|rl|rr 0..100
  seat)     put "/vehicle/seat?kind=occupied&area=$2&value=$3" ;;            # driver|passenger|rear_left|rear_right
  seatheat) put "/vehicle/hvac?$2_seat=$3" ;;                                # driver|passenger -3..3
  defrost)  put "/vehicle/hvac?front_defroster=$2" ;;
  odo)      put "/vehicle/odometer?traveled_distance_km=$2" ;;
  state)    curl -s "$VC/vehicle/state" ;;
  *) echo "usage: $0 gear|speed|stop|wiper|highbeam|headlamp|window|seat|seatheat|defrost|odo|state ..."; exit 1 ;;
esac
