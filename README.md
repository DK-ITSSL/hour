# 동행록 (Donghaengrok)

**차가 기억하는 우리의 여정.** 운행할 때마다 차가 비·별빛·바람·온기·동행·목소리를 스스로 기록해 여정 카드로 돌려주는 Pleos Connect 차량용 앱입니다.

> 현대자동차그룹 차량용 앱 개발 공모전 출품작 · 개발 진행 중

## 현재 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| D1 | 프로젝트 골격, 차량 신호 어댑터, 에뮬레이터 실측 검증 | 완료 |
| D2 | 여정 상태 머신, 감각 계산, 운행타입 분류, 해금 엔진, 저장소 | 완료 |
| D3 | 화면(홈·여정 상세·도착 선물·옷장·주행 중 미니), 음성, Gleo 연동 | 예정 |

지금 빌드의 화면은 진단 화면입니다. 기록 서비스가 여정을 자동으로 기록·저장하고, 진단 화면에서 현재 여정·최근 여정·해금 결과를 확인할 수 있습니다. 실제 화면은 D3에서 붙입니다.

```bash
./gradlew :domain:test          # 도메인 단위·시나리오 테스트(22개)
scripts/scenario_drive.sh       # 에뮬레이터 주행 시나리오(앱의 기록 서비스 실행 후)
```

## 구조

```
app/     화면, 여정 기록 서비스, Gleo 수신부 (수동 의존성 주입)
car/     차량·음성 신호 게이트웨이. pleos(실제 SDK) / sim(가짜 신호) 두 가지 구현
domain/  순수 Kotlin 도메인(신호 모델, 여정 상태 머신, 감각 누적, 운행타입 분류, 제목·내러티브, 해금 엔진)
docs/    설계 문서(MVP-BLUEPRINT.md)
scripts/ 에뮬레이터 Virtual Console 제어(vc.sh), 주행 시나리오(scenario_drive.sh)
```

- 빌드 변형: `pleos`(Pleos Connect 에뮬레이터·실차용, 제출본) / `sim`(일반 안드로이드 기기에서 가짜 신호로 실행)
- 사용 SDK: Pleos Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1, AAOS Car API(기어·속도·외기온도·주행 규제)

## 테스트 환경

- Pleos Connect Emulator **v2.1.0** (Android 14 / API 34), AVD는 콜드 부팅·RAM 4GB
- 화면 규격: **2560×1440** (Connect-S 17")
- 빌드: JDK 17 이상, Gradle 8.10.2(wrapper), AGP 8.8.2, Kotlin 2.0.21, minSdk 28 / targetSdk 35

## 실행 방법

```bash
# 빌드
./gradlew :app:assemblePleosDebug

# 설치 (에뮬레이터의 활성 사용자는 10번)
adb install --user 10 -r app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
adb shell am start --user 10 -n com.donghaengrok.app/.MainActivity
```

앱의 "권한 요청" 버튼으로 위치·마이크·알림·차속 권한을 허용한 뒤 "기록 서비스 시작"을 누르면 차량 신호가 실시간 로그에 표시됩니다.

### 차량 신호 넣기 (Virtual Console)

```bash
adb forward tcp:8088 tcp:8088
scripts/vc.sh gear Drive
scripts/vc.sh speed 60
scripts/vc.sh wiper High
scripts/vc.sh highbeam true
scripts/vc.sh window rl 60
```

### Gleo 호출 재현

에뮬레이터에는 Gleo 음성 앱이 없어 같은 인텐트를 adb로 보냅니다.

```bash
adb shell "am broadcast --user 10 -a SAVE_JOURNEY_MOMENT -p com.donghaengrok.app --es ACTION_PARAM '{\"note\":\"애들이 무지개 봤어\"}'"
```

## APK

제출 시점에 `apk/팀명_동행록_release.apk` 또는 GitHub Release로 올립니다.
