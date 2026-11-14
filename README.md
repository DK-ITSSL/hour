# H:our (아워)

**우리의 여정, 그 시간을 함께.** 운행할 때마다 차가 비·별빛·바람·온기·동행·목소리를 스스로 기록해 여정 카드로 돌려주는 Pleos Connect 차량용 앱입니다.

이름은 Hyundai의 **H**와 우리의 **our**를 이은 말입니다. 붙여 읽으면 시간을 뜻하는 **hour**가 됩니다. 현대자동차가 우리의 여정과 그 시간을 늘 함께한다는 뜻입니다. 읽을 때는 "아워"라고 읽습니다.

> 현대자동차그룹 차량용 앱 개발 공모전 출품작 · 1차 접수본 (2차 PT까지 개발을 이어 갑니다)

## 현재 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| D1 | 프로젝트 골격, 차량 신호 어댑터, 에뮬레이터 실측 검증 | 완료 |
| D2 | 여정 상태 머신, 감각 계산, 운행타입 분류, 해금 엔진, 저장소 | 완료 |
| D3 | 화면(홈·여정 상세·운행타입 확인·도착 선물·옷장·주행 중 미니), 도착 흐름(음성·알림·Gleo), 음성 순간 기록 | 완료 |
| D4 | 설정(집·회사 직접 지정, 체험용 샘플), 좁은 화면 레이아웃, 내비 경로 요청 연동, release 서명 APK | 완료 |
| 이후 | 캐릭터·기념품 디자인, 월간 결산, 다시 가기 화면, 온보딩, 실차 신호 편차 대응 | 2차 PT까지 |

시동을 걸면 기록 서비스가 여정을 자동으로 기록합니다. 도착해서 P단에 두면 "이번 운행은 어떤 길이었나요?"를 음성·알림·화면으로 묻고(Gleo로 답해도 됩니다), 운행타입이 정해지면 그 타입의 선물(캐릭터 애셋)이 열립니다.

- **홈**: 캐릭터 아워(가칭)와 방, 운행타입 탭(전체·출퇴근길·여행·데이트·일상), 여정 카드
- **여정 상세**: 제목·내러티브(낭독), 감각 막대, 경로 스케치, 다녀온 곳, 기억한 순간, 안심 운행
- **운행타입 확인 → 도착 선물**: 추천 타입이 먼저 선택되고 10초 동안 응답이 없으면 추천대로 기록, 이어서 카드가 뒤집히며 새 애셋이 나타남
- **옷장**: 슬롯별 애셋 장착, 잠긴 애셋은 실루엣과 힌트
- **주행 중 미니**(주행 규제 중): 기록 상태, 감각 표시등, "이 순간 기억"(음성) 버튼 하나
- **설정**: 집·회사(내비 즐겨찾기 자동, 또는 "지금 위치로" 직접 지정), 도착 안내·낭독, 기록 켜기·끄기, 체험용 샘플

넓은 화면(Connect-S 가로)은 2단, 좁은 화면(앱 창 너비 1100dp 미만: Connect-L 세로·분할 화면)은 한 줄 레이아웃으로 바뀝니다.

캐릭터와 애셋 그림은 임시 그림입니다. 실제 그림 파일을 넣는 방법은 [docs/ART-ASSETS.md](docs/ART-ASSETS.md)에 있습니다.

```bash
./gradlew :domain:test          # 도메인 단위·시나리오 테스트(24개)
scripts/scenario_drive.sh       # 에뮬레이터 주행 시나리오(앱의 기록 서비스 실행 후)
```

## 구조

```
app/     화면(홈·여정 상세·도착 선물·옷장·주행 중 미니), 여정 기록 서비스, 도착 흐름, Gleo 수신부 (수동 의존성 주입)
car/     차량·음성 신호 게이트웨이. pleos(실제 SDK) / sim(가짜 신호) 두 가지 구현
domain/  순수 Kotlin 도메인(신호 모델, 여정 상태 머신, 감각 누적, 운행타입 분류, 제목·내러티브, 해금 엔진)
docs/    설계 문서(MVP-BLUEPRINT.md), 그림 교체 가이드(ART-ASSETS.md)
scripts/ 에뮬레이터 Virtual Console 제어(vc.sh), 주행 시나리오(scenario_drive.sh), 제출용 APK(release_apk.sh)
```

- 빌드 변형: `pleos`(Pleos Connect 에뮬레이터·실차용, 제출본) / `sim`(일반 안드로이드 기기에서 가짜 신호로 실행)
- 사용 SDK: Pleos Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1, AAOS Car API(기어·속도·외기온도·주행 규제)

## 테스트 환경

- Pleos Connect Emulator **v2.1.0** (Android 14 / API 34), AVD는 콜드 부팅·RAM 4GB
- 화면 규격: **2560×1440** (Connect-S 17")
- 빌드: JDK 17 이상, Gradle 8.10.2(wrapper), AGP 8.8.2, Kotlin 2.0.21, minSdk 28 / targetSdk 35

## 실행 방법

### 제출용 APK로 바로 실행

```bash
# 에뮬레이터의 활성 사용자는 10번
adb install --user 10 -r apk/ITSSL_Hour_release.apk
adb shell am start --user 10 -n com.hour.app/.MainActivity
```

설치한 뒤 [바로 둘러보기](#바로-둘러보기-심사체험용-release-빌드-포함)의 체험용 샘플로 주행 없이 화면을 볼 수 있습니다.

### 소스에서 빌드

```bash
# 빌드
./gradlew :app:assemblePleosDebug

# 설치 (에뮬레이터의 활성 사용자는 10번)
adb install --user 10 -r app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
adb shell am start --user 10 -n com.hour.app/.MainActivity
```

앱을 열면 기록 서비스가 켜집니다. 홈의 "권한 허용" 버튼으로 위치·차속·마이크·알림 권한을 허용하세요. 디버그 빌드에는 차량 신호 로그를 보는 "진단" 탭이 있습니다.

### 바로 둘러보기 (심사·체험용, release 빌드 포함)

실제로 운행하지 않아도 **설정 → 체험용 샘플**에서 화면을 둘러볼 수 있습니다.

1. **샘플 여정 넣기**: 지난 며칠의 출퇴근길·일상·데이트 여정 5건(카드에 "샘플" 표시)
2. **도착 장면 보기**: 강릉 가족 여행 도착 → "이번 운행은 어떤 길이었나요?"(10초 뒤 추천 적용) → 도착 선물(여행 애셋 5개) → 옷장에서 입히기
3. **샘플 지우기**: 샘플 여정과 샘플로 받은 선물만 지웁니다(실제 기록은 그대로)

운행타입 추천(회사 → 출퇴근길, 집에서 100km 이상 → 여행)은 집·회사 위치를 기준으로 합니다. 에뮬레이터 내비에는 즐겨찾기가 비어 있으므로 **설정 → 집·회사 → 지금 위치로**로 정하세요. 집을 모르면 출발 지점을 기준으로 봅니다.

### 시연 준비 (디버그 빌드)

```bash
# 지난 며칠의 출퇴근·일상·데이트 샘플 여정 5건(영상에는 "시연용 샘플 데이터"로 표기)
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.SEED
# 강릉 여행 도착 직전 상태 → 운행타입 확인 → 도착 선물
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.PREVIEW_ARRIVAL
# 여정·선물·장착 초기화
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.RESET
# 내비 경로 안내 요청(도착 이벤트 확인용, 기본 목적지 성남시청)
adb shell am start --user 10 -n com.hour.app/.MainActivity -a com.hour.app.debug.ROUTE
```

도착 판정은 내비 목적지 도착 이벤트(`onDestinationArrived`)가 오면 바로, 오지 않으면 2km 이상 달린 뒤 P단 3분으로 합니다. Pleos Connect 에뮬레이터 v2.1.0의 내비는 지도·경로 탐색이 동작하지 않아(경로 요청 후 안내가 시작되지 않음) 에뮬레이터에서는 P단 3분 규칙으로 동작합니다.

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

제출용 release APK: [`apk/ITSSL_Hour_release.apk`](apk/ITSSL_Hour_release.apk)

- 패키지 `com.hour.app`, 버전 0.1.0, `pleos` 빌드 변형의 release(디버그 불가, 진단 탭과 디버그 인텐트 제외)
- 인터넷 권한 없음. 자체 키로 서명(APK Signature Scheme v3)

같은 APK를 다시 만들려면:

```bash
# local.properties(깃 제외)에 release.storeFile·storePassword·keyAlias·keyPassword를 적은 뒤
scripts/release_apk.sh 팀명
adb install --user 10 -r apk/팀명_Hour_release.apk
```

서명 키 파일은 저장소 밖에 보관합니다. 키가 없으면 release 빌드는 디버그 키로 서명되고(로컬 확인용), 제출 스크립트는 APK를 만들지 않습니다.
