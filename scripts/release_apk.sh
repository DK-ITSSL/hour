#!/usr/bin/env bash
# 제출용 release APK를 만들어 apk/팀명_Hour_release.apk로 복사한다.
# 사용: scripts/release_apk.sh <팀명>
# 전제: local.properties에 release.storeFile·storePassword·keyAlias·keyPassword(키 파일은 저장소 밖)
set -euo pipefail
TEAM=${1:?"사용법: scripts/release_apk.sh <팀명>"}
cd "$(dirname "$0")/.."
store=$(grep -E '^release\.storeFile=' local.properties 2>/dev/null | cut -d= -f2- || true)
if [ -z "$store" ] || [ ! -f "$store" ]; then
  echo "release 서명 키가 없습니다(local.properties의 release.storeFile). 디버그 키로 서명한 APK는 제출하지 않습니다." >&2
  exit 1
fi
./gradlew --console=plain -q :app:assemblePleosRelease
mkdir -p apk
out="apk/${TEAM}_Hour_release.apk"
cp app/build/outputs/apk/pleos/release/app-pleos-release.apk "$out"
echo "만듦: $out"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
APKSIGNER=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner
"$APKSIGNER" verify --print-certs "$out" | grep -E "certificate (DN|SHA-256)" || true
