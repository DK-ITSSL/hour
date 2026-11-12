# H:our (아워)

**우리의 여정, 그 시간을 함께.** 운행할 때마다 차가 비·별빛·바람·온기·동행·목소리를 스스로 기록해 여정 카드로 돌려주는 Pleos Connect 차량용 앱입니다.

이름은 Hyundai의 **H**와 우리의 **our**를 이은 말입니다. 붙여 읽으면 시간을 뜻하는 **hour**가 됩니다. 현대자동차가 우리의 여정과 그 시간을 늘 함께한다는 뜻입니다. 읽을 때는 "아워"라고 읽습니다.

> 현대자동차그룹 차량용 앱 개발 공모전 출품작 · 개발 진행 중

## 현재 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| D1 | 프로젝트 골격, 차량 신호 어댑터, 에뮬레이터 실측 검증 | 완료 |
| D2 | 여정 상태 머신, 감각 계산, 운행타입 분류, 해금 엔진, 저장소 | 완료 |
| D3 | 화면(홈·여정 상세·운행타입 확인·도착 선물·옷장·주행 중 미니), 도착 흐름(음성·알림·Gleo), 음성 순간 기록 | 완료 |
| D4 | 경로 안내 이벤트 확인, release 서명 APK, 시연 영상, 제출 | 예정 |

시동을 걸면 기록 서비스가 여정을 자동으로 기록합니다. 도착해서 P단에 두면 "이번 운행은 어떤 길이었나요?"를 음성·알림·화면으로 묻고(Gleo로 답해도 됩니다), 운행타입이 정해지면 그 타입의 선물(캐릭터 애셋)이 열립니다.

- **홈**: 캐릭터 아워(가칭)와 방, 운행타입 탭(전체·출퇴근길·여행·데이트·일상), 여정 카드
- **여정 상세**: 제목·내러티브(낭독), 감각 막대, 경로 스케치, 다녀온 곳, 기억한 순간, 안심 운행
- **운행타입 확인 → 도착 선물**: 추천 타입이 먼저 선택되고 10초 동안 응답이 없으면 추천대로 기록, 이어서 카드가 뒤집히며 새 애셋이 나타남
- **옷장**: 슬롯별 애셋 장착, 잠긴 애셋은 실루엣과 힌트
- **주행 중 미니**(주행 규제 중): 기록 상태, 감각 표시등, "이 순간 기억"(음성) 버튼 하나

캐릭터와 애셋 그림은 임시 그림입니다. 실제 그림 파일을 넣는 방법은 [docs/ART-ASSETS.md](docs/ART-ASSETS.md)에 있습니다.

```bash
./gradlew :domain:test          # 도메인 단위·시나리오 테스트(22개)
scripts/scenario_drive.sh       # 에뮬레이터 주행 시나리오(앱의 기록 서비스 실행 후)
```

## 구조

```
app/     화면(홈·여정 상세·도착 선물·옷장·주행 중 미니), 여정 기록 서비스, 도착 흐름, Gleo 수신부 (수동 의존성 주입)
car/     차량·음성 신호 게이트웨이. pleos(실제 SDK) / sim(가짜 신호) 두 가지 구현
domain/  순수 Kotlin 도메인(신호 모델, 여정 상태 머신, 감각 누적, 운행타입 분류, 제목·내러티브, 해금 엔진)
docs/    설계 문서(MVP-BLUEPRINT.md), 그림 교체 가이드(ART-ASSETS.md)
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
adb shell am start --user 10 -n com.hour.app/.MainActivity
```

앱을 열면 기록 서비스가 켜집니다. 홈의 "권한 허용" 버튼으로 위치·차속·마이크·알림 권한을 허용하세요. 디버그 빌드에는 차량 신호 로그를 보는 "진단" 탭이 있습니다.

### 시연 준비 (디버그 빌드)

```bash
# 지난 며칠의 출퇴근·일상·데이트 샘플 여정 5건(영상에는 "시연용 샘플 데이터"로 표기)
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.SEED
# 강릉 여행 도착 직전 상태 → 운행타입 확인 → 도착 선물
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.PREVIEW_ARRIVAL
# 여정·선물·장착 초기화
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.RESET
```

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
adb shell "am broadcast --user 10 -a SAVE_JOURNEY_MOMENT -p com.hour.app --es ACTION_PARAM '{\"note\":\"애들이 무지개 봤어\"}'"
# 도착 후 운행타입 확정(trip · commute · date · daily)
adb shell "am broadcast --user 10 -a SET_TRIP_CATEGORY -p com.hour.app --es ACTION_PARAM '{\"category\":\"trip\"}'"
```

## APK

제출 시점에 `apk/팀명_Hour_release.apk` 또는 GitHub Release로 올립니다.
