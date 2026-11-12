# H:our MVP 소프트웨어 블루프린트 (v0.1, 2026-11-12)

구현 전 설계 문서다. API 이름, 권한, 열거값은 로컬 공식 문서 사본(`pleos-docs/`)에서 확인했다. "확인 필요"로 표시한 것은 Day 1에 에뮬레이터에서 검증한다.

---

## 0. 목표와 범위

**목표**: 공모전 2차 PT(12/3~4) 전까지 Pleos Connect 에뮬레이터 v2.1.0에서 실제로 동작하는 앱을 만든다. 1차 마감(11/15)까지는 시연 영상과 release APK까지 낸다.

| 포함 (MVP) | 제외 (로드맵으로 기획서에만 기재) |
|---|---|
| 자동 여정 기록: D단에서 시작해 P단에서 끝내고, 휴게소 정차는 같은 여정으로 병합 | 차 교체 시 캐릭터·아카이브 이사 (서버·계정 필요) |
| 감각 6가지(비, 별빛, 바람, 온기, 동행, 목소리)와 장소 기록 | 지역 제휴 애셋 실연동 (MVP는 오프라인 목업 1개) |
| 도착 선물: 여정 카드와 해금 연출, TTS 낭독 | 연말 결산 QR 공유, 휴대폰 앱 |
| 아카이브: 운행타입별 목록(출퇴근길·여행·데이트·일상), 여정 상세, "다시 가기" | 여러 차량·프로필 동기화 |
| 해금 규칙 12개, 캐릭터 1종, 치장 슬롯 3개와 방 1개 | 안개등, 외기온도 기반 규칙의 에뮬레이터 시연 (Virtual Console에 해당 신호 없음) |
| Gleo 함수 3개, 앱 안 음성 기록(STT) | |
| 주행 중 미니 화면(주행 규제 대응), CCP Back 처리, 세로·가로 레이아웃 | |

---

## 1. 기술 스택 (2026-11-12 확정)

### 1.1 Pleos가 정해 주는 것
- **언어·빌드**: Kotlin 2.0.x, AGP 8.8.2, Gradle 8.10.2, JDK 17, 버전 카탈로그(`gradle/libs.versions.toml`). 공식 Vehicle 샘플과 같은 조합이다.
- **SDK 레벨**: compileSdk/targetSdk 35, **minSdk 28**. 샘플은 23~33이지만 App Market 요건이 28 이상이다.
- **Pleos SDK** (Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`, group `ai.pleos.playground`): Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1. 샘플은 Vehicle 2.3.1을 쓰므로, 2.3.2에서 문제가 생기면 2.3.1로 내린다.
- **AAOS Car API**: `useLibrary("android.car")`(compileOnly 성격). 기어·속도·외기온도(`CarPropertyManager`)와 주행 규제(`CarUxRestrictionsManager`)에 쓴다. `pleos` 빌드에서만 호출한다.
- **Google 서비스 없음(non-GAS)**: Firebase, FCM, Crashlytics, Google Maps, Play 위치 서비스, GMS 기반 ML Kit는 쓰지 않는다.
- **INTERNET 권한 없음**: 모든 데이터를 기기 안에서만 처리한다.

### 1.2 우리가 고른 것

| 결정 | 선택 | 이유 |
|---|---|---|
| UI | Jetpack Compose + Material3 (Pleos 토큰 테마) | 공식 샘플 3종 모두 Compose다. 창 크기 대응이 쉽다. CCP 로터리는 MVP에서 끈다(`ROTARY_SUPPORTED=false`) |
| 화면 패턴 | MVVM + 단방향 상태(StateFlow) | 샘플과 같다. 핵심 로직은 `:domain`에 둔다 |
| 모듈 | **3개: `:app`, `:domain`, `:car`** | §3 |
| 의존성 주입 | **수동(`AppContainer`)** | 의존 객체가 적다. 서비스·리시버 연결이 단순하다. 커지면 Hilt로 옮긴다 |
| 빌드 변형 | **`pleos` / `sim` 두 가지(flavor)** | §3.2 |
| 차량 데이터 계층 | 게이트웨이 인터페이스 + `callbackFlow` 래핑 + 단일 스레드 이벤트 루프 | §2 |
| 백그라운드 | 포그라운드 서비스(`specialUse`) | 운행 내내 신호를 받아야 한다 |
| 저장 | Room(KSP) + DataStore | |
| 직렬화 | kotlinx.serialization | 내비 SDK 모델이 `@Serializable`이다. 해금 규칙 JSON과 Gleo `ACTION_PARAM` 파싱에도 쓴다 |
| 비동기 | Coroutines/Flow | |
| 화면 이동 | Navigation-Compose | 화면 7개 정도 |
| 그래픽 | Compose 애니메이션 + Canvas (캐릭터 레이어 합성, 경로 스케치) | **Lottie는 보류**. 디자인 파이프라인이 정해지면 다시 검토한다 |
| 지도 | Canvas 경로 스케치 (타일 없음) | 구글 지도 불가, OSM 타일은 인터넷 필요 |
| 음성 | Pleos TTS·STT (`Mode.ON_DEVICE`) | Google 서비스가 없는 환경이라 기본 TTS 엔진을 보장할 수 없다 |
| 테스트 | JUnit5 + kotlinx-coroutines-test + Turbine (도메인 중심) | UI 테스트는 MVP 이후 |
| 로그 | Timber(debug) + 기기 내 순환 로그 파일 | release에서는 위치·속도를 로그에 남기지 않는다 |
| 코드 축소(R8) | 1차 제출은 끔 | 샘플도 꺼 두었다. 2차 전에 keep 규칙을 갖추고 검토한다 |
| 디자인 토큰 | `pleos-docs/figma/tokens-*.md`에서 Kotlin 상수로 변환 | 글꼴은 시스템 기본(Asta Sans 번들·라이선스 미확인) |

---

## 2. 아키텍처

```
┌──────────────────────── UI (Compose, P단 중심) ───────────────────────┐
│ Onboarding · Home(캐릭터/운행타입) · JourneyDetail · ArrivalGift · Wardrobe │
│ DrivingMini(주행 중, DO) · Settings                                    │
└───────────────▲───────────────────────────────────────▲──────────────┘
                │ StateFlow (ViewModel)                  │ Room Flow
┌───────────────┴──────────────┐          ┌──────────────┴─────────────┐
│ JourneyRecorderService (FGS) │──쓰기───▶│ Data: Room + DataStore      │
│  SignalBus(Channel, 단일 스레드)│          │ Repositories                │
│  → Domain 엔진 호출            │          └────────────────────────────┘
└───────▲──────────────────────┘
        │ SignalEvent (정규화된 sealed class, timestamp 포함)
┌───────┴──────────────── Signal Gateways (포트) ────────────────────────┐
│ VehicleSignals · DriveState · NaviEvents · LocationFeed · Voice · Gleo │
│  ├─ Pleos* 어댑터 (실제 SDK)                                           │
│  └─ Fake*/Replay 어댑터 (단위 테스트 · 디버그 시연 재생)                │
└────────────────────────────────────────────────────────────────────────┘
Domain (:domain, 순수 Kotlin/JVM):
  JourneyStateMachine · SenseAccumulators · DistanceTracker · SunCalculator
  RegionParser · TripCategoryClassifier · TitleGenerator · UnlockEngine
```

**설계 원칙**
1. **SDK 콜백은 전부 `SignalEvent`로 정규화한다.** 단일 이벤트 루프(Channel) 하나에서 순서대로 처리하므로 동시성 버그가 생기지 않는다.
2. **도메인은 SDK를 모른다.** 그래서 JVM 단위 테스트가 가능하고, 같은 엔진으로 테스트와 디버그용 시연 재생을 모두 돌릴 수 있다.
3. **이벤트 구독만 하고 폴링하지 않는다.** 예외는 아래 두 가지뿐이다. 주행 중 CPU 5% 미만 기준을 지키기 위해서다.
   - 와이퍼 스위치 단계: 리스너가 없어서, 와이퍼가 켜져 있는 동안만 60초마다 조회한다.
   - 위치: SDK가 콜백으로 보내준다.

---

## 3. 모듈·패키지 구조

### 3.1 모듈 3개

```
hour/
├─ settings.gradle.kts, gradle/libs.versions.toml
├─ domain/   (Kotlin JVM 라이브러리, Android·SDK 의존 없음)
│  ├─ signal/   SignalEvent.kt (Gear, Speed, Wiper, Light, Window, Seat, Belt, Hvac, Odometer,
│  │            Location, NaviAddress, NaviArrived, NaviTbt, NaviSpeedLimit, NaviCaution, Moment …)
│  ├─ journey/  JourneyStateMachine.kt, JourneyDraft.kt, MergePolicy.kt
│  ├─ sense/    Rain/Starlight/Wind/Warmth/Companion/Place/SafetyAccumulator.kt
│  ├─ geo/      DistanceTracker.kt, SunCalculator.kt, RegionParser.kt
│  ├─ story/    TitleGenerator.kt, NarrativeBuilder.kt, TripCategoryClassifier.kt
│  └─ unlock/   UnlockRule.kt, UnlockEngine.kt, RuleSafetyPolicy.kt
├─ car/      (Android 라이브러리, 차량·음성 신호 게이트웨이) — flavor: pleos / sim
│  ├─ src/main/    인터페이스: VehicleSignals, DriveState, NaviEvents, LocationFeed, Voice,
│  │               SenseAvailability, SignalError (domain의 SignalEvent를 내보냄)
│  ├─ src/pleos/   VehicleSession, PleosVehicleSignals, CarPropertyDriveState, PleosNaviEvents,
│  │               PleosLocationFeed, PleosVoice, SenseAvailabilityProbe, UxRestrictionSource
│  └─ src/sim/     FakeSignals(수동 조작), ScenarioReplay(JSON 타임라인 재생), FakeVoice
└─ app/      (Android 앱)
   ├─ di/        AppContainer.kt (수동 주입: Repo, Gateways, Engines 생성·보관)
   ├─ recorder/  JourneyRecorderService.kt, SignalBus.kt, CheckpointWriter.kt, BootReceiver.kt
   ├─ gleo/      GleoActionReceiver.kt, GleoActionActivity.kt, GleoResponder.kt
   ├─ data/      db/(Entities, DAOs, AppDatabase), repo/(Journey/Unlock/Wardrobe/Anchor), prefs/
   ├─ ui/        theme/(Pleos 토큰), onboarding/, home/, journey/, category/, gift/, wardrobe/,
   │             driving/, settings/
   ├─ src/sim/   SimControlPanel(디버그 패널: 기어·와이퍼·창문 등 수동 주입), SeedData
   └─ assets/    unlock_rules.json, characters/(레이어 PNG/벡터), schema.json
```

- 의존 방향은 `:app → :car → :domain`, `:app → :domain`이다. `:domain`은 아무것도 모른다.
- 패키지 ID: `com.hour.app`. 2026-11-12에 앱 이름을 "동행록" → "차곡" → "H:our"로 바꾸며 함께 바꿨다. 콜론은 패키지·파일·저장소 이름에 쓸 수 없어 그곳에서는 `hour`/`Hour`로 쓴다. Gleo 스키마와 App Market 등록에 쓰이므로 이제 고정한다.

### 3.2 빌드 변형 (flavor dimension `env`)

| flavor | 용도 | 신호 출처 | 설치 대상 | applicationId |
|---|---|---|---|---|
| `pleos` | 실제 동작, **제출용** (`pleosRelease`) | Pleos SDK + AAOS | Pleos Connect 에뮬레이터 v2.1.0, 실차 | `com.hour.app` |
| `sim` | UI 개발, 시연 재생, 도메인 통합 확인 | 가짜 신호(디버그 패널, JSON 시나리오 재생) | 일반 안드로이드 에뮬레이터·태블릿 | `com.hour.app.sim` |

- `:car`와 `:app`에 같은 flavor 이름을 선언해 Gradle이 자동으로 짝을 맞춘다. Pleos SDK 의존성은 `pleosImplementation`으로만 넣는다. 그래서 `sim` 빌드에는 SDK와 AAOS 호출 코드가 들어가지 않는다.
- `sim`에서도 주행 규제는 가짜 `UxRestrictionSource`로 흉내 내서 주행 중 미니 화면을 검증한다.
- 제출 APK는 `pleosRelease`를 자체 키스토어로 서명하고, 파일명은 `팀명_Hour_release.apk`로 한다.
- 시연용 시드 데이터와 디버그 패널은 `sim` 빌드와 `pleosDebug`에만 넣는다. `pleosRelease`에는 포함하지 않는다.

---

## 4. 신호 매핑 (확인한 API 기준)

| 감각·용도 | API | 권한 | Virtual Console 주입 | 비고 |
|---|---|---|---|---|
| 기어 (여정 시작·끝) | AAOS `CarPropertyManager` `VehiclePropertyIds.GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` | ✅ Motion > Gear | Vehicle SDK 2.3.2에 `Gear` API가 있지만 **deprecated**("use the Google GEAR_SELECTION property"). AAOS를 쓴다 |
| 주행거리 | `Odometer.registerDistanceDriven(DistanceDrivenListener)` (km) | `pleos.car.permission.CAR_DRIVE` | ✅ Odometer | 위치 궤적이 없을 때 거리를 보정하는 데 쓴다 |
| 비 | `Wiper.registerWiperState(WindowArea(FRONT_WINDSHIELD), WiperStateListener)` (값: OFF/ON/OTHER/SERVICE), 강도는 `getCurrentWiperSwitch`로 조회 | `CAR_WINDOWS` | ✅ Wiper (Off/Auto/Low/High) | WiperSwitch는 리스너가 없다. 상태가 바뀔 때 조회하고, 켜져 있는 동안 60초마다 조회한다 |
| 별빛·밤 | `Light.registerHighBeamLightState`, `registerLowBeamLightState` (`LightStateListener`: OFF/ON/DAYTIME_RUNNING) | `CAR_LIGHTS` | ✅ High Beam, Exterior Lights Mode | 안개등 `registerFrontFogLightState`는 선택 기능이다 (VC에 없음) |
| 바람 | `Window.registerWindowPosition(WindowArea(ROW_1_LEFT …ROW_2_RIGHT, ROOF_TOP_1), WindowPositionListener)`, 범위는 `checkWindowPositionCapabilities` | `CAR_WINDOWS` | ✅ Window Position 0–100% | 에뮬레이터 범위 0..10, **0=닫힘·10=완전 열림**(D1 확인) |
| 동행 | `Seat.registerSeatOccupancy(SeatArea(...), SeatOccupancyListener)`, 보조로 `registerSeatBeltBuckledState` | `CAR_SEATS` | ✅ Seat Occupancy | 실차는 뒷좌석 미지원 트림이 많다. 벨트 상태와 Gleo 태그로 보완한다 |
| 안심(속도) | AAOS `CarPropertyManager` `PERF_VEHICLE_SPEED` (주행 중에만 2Hz 구독) | `android.car.permission.CAR_SPEED` (**런타임 권한**. 승인 창 문구가 "알 수 없는 작업 실행"으로 표시됨) | ✅ (ACCEL/BRAKE) | 급가속·급감속은 속도 변화율로 계산한다. 브레이크 페달 API는 눌림 여부(Boolean)만 준다 |
| 안심(제한속도) | `NaviHelper.onCurrentRoadSpeedLimit(Int)` (매초), `onCameraAlert(CameraAlert.isOverSectionAvrSpeed)`, `onCautionPointInfo(SCHOOL_ZONE)` | `NAVI_ROUTE` | 내비 모의주행에서 오는지 **확인 필요** | 내비 정보가 없으면 "판정 불가"로 둔다 |
| 안심(벨트) | `Seat.registerSeatBeltBuckledState` + 착석 | `CAR_SEATS` | **확인 필요** | 착석한 모든 좌석이 운행 내내 체결됐는지 |
| 온기 | `Seat.registerHvacSeatTemperature(SeatArea(ROW_1_LEFT), …)`, `HVAC.registerHvacDefrosterState(WindowArea(FRONT_WINDSHIELD), …)` | `CAR_SEATS`, `CAR_CLIMATE` | ✅ Seat Temp, Defroster | 열선은 양수, 통풍은 음수로 추정 (확인 필요) |
| 외기온도 (선택) | AAOS `ENV_OUTSIDE_TEMPERATURE` | `android.car.permission.CAR_EXTERIOR_ENVIRONMENT` | ❌ | 없으면 첫 눈길 규칙을 비활성화한다 |
| 위치·궤적 | `FusedLocation.registerFusedLocationCallback(FusedLocationListener)` | `FUSED_LOCATION` (**dangerous**, 런타임 요청) | `adb emu geo fix` 또는 GPX | `onChanged(null)`은 무시한다 |
| 집·회사 | `NaviHelper.getBookmarkInfo()`를 호출하면 `onBookmarkInfo(List<BookmarkInfo>)`로 결과가 온다. `category == BookmarkType.HOME / OFFICE`인 항목의 위도·경도·이름·주소를 캐시한다. `onBookMarkChanged()`가 오면 다시 조회한다 | `NAVI_ROUTE` | ✅ (내비에 즐겨찾기 등록) | 운행타입 분류(§7.1)의 기준점이다. 콜백이 보장되지 않으므로 5초 타임아웃을 두고 캐시값을 쓴다. 미등록이면 앱 설정에서 "현재 위치를 집/회사로" 지정 |
| 장소 | `onCurrentLocationInfo(CurrentLocationInfo.address)`, `getDestinationInfo()`, `onDestinationArrived(DestinationArrivedInfo)`, `onWayPointArrived` | `NAVI_ROUTE` | ✅ (내비) | **경로 안내가 없을 때 주소 콜백이 오는지 확인 필요**. 안 오면 시·군·구를 알 수 없다고 처리한다 |
| 터널·휴게소·톨게이트 | `onTBTInfo(List<TBTInfo(type, distance, description)>)`의 `TURN_TUNNEL`, `TURN_ENTER_SERVICEAREA`, `TURN_RESTAREA`, `TURN_TOLL` | `NAVI_ROUTE` | 경로 안내 중에만 | 남은 거리가 0 근처를 지나가면 "통과"로 판정한다 |
| 다시 가기 | `NaviHelper.requestRoute(RouteInfo)` | `NAVI_ROUTE_SEARCH` | ✅ | 결과는 `onRoutingCompleted`와 타임아웃으로 판단한다 |
| 주행 규제 | `CarUxRestrictionsManager.registerListener` | (없음) | `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off` | |
| 음성 출력 | `TextToSpeech(ctx, Mode.ON_DEVICE)` → `initialize` 성공 후 `addEventListener`·`setLocale(ko-KR)` → `speak(text, true)` | `pleos.car.permission.TTS_SERVICE` (일반) | ✅ | 등록 불필요. 오디오 포커스 매트릭스와 150~200ms 선행 묵음을 지킨다 |
| 음성 입력 | `SpeechToText(ctx, Mode.ON_DEVICE)` + `request()` | `pleos.car.permission.STT_SERVICE` (일반) + `RECORD_AUDIO` (런타임) | ✅ (호스트 마이크) | 버튼을 누를 때만 마이크를 켠다 |
| Gleo | `assets/schema.json`과 Intent 수신 | (없음) | ❌ 음성 → **adb broadcast로 대체** | §10 참고 |

**Manifest 권한 (최소 집합)**: `pleos.car.permission.CAR_WINDOWS`, `CAR_LIGHTS`, `CAR_SEATS`, `CAR_CLIMATE`, `CAR_DRIVE`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `FUSED_LOCATION`, `TTS_SERVICE`, `STT_SERVICE`, `android.car.permission.CAR_POWERTRAIN`, `CAR_SPEED`(안심 보상용), (선택) `CAR_EXTERIOR_ENVIRONMENT`, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.

- 제어 권한(`CONTROL_*`)은 쓰지 않는다. 앱마켓 승인 리스크가 낮다.
- 샘플 앱의 권한 목록을 통째로 복사하지 않는다. 쓰지 않는 권한을 선언하면 심사에서 불통과될 수 있다.

---

## 5. 기록 서비스와 생명주기

### 서비스 상태

| 상태 | 붙잡는 것 | 자원 사용 |
|---|---|---|
| IDLE (P단, 여정 없음) | 기어 리스너 1개만 | 최소 |
| RECORDING (D/R/N) | 모든 감각 리스너, 위치 콜백, 내비 리스너 | 이벤트 처리만 |
| STOPPED (여정 중 P단) | 기어·문·위치 리스너만, 병합 타이머 | 최소 |

### 포그라운드 서비스
- 타입은 **`specialUse`**로 하고, 용도 설명 property("vehicle journey recording")를 단다.
- `location` 타입을 쓰지 않는 이유: Pleos 위치 권한은 커스텀 권한(`FUSED_LOCATION`)이라 안드로이드 표준 위치 권한(`ACCESS_*_LOCATION`)이 없다. 그래서 `location` 타입은 예외가 날 수 있다 (확인 필요).
- 알림에는 "기록 중 · 21km"처럼 한 줄만 보여준다.

### 시작 경로
1. **자동(최선 노력)**: `BOOT_COMPLETED`/`USER_UNLOCKED`를 받으면 서비스를 IDLE로 시작한다.
2. **앱 실행 시**: 서비스가 없으면 시작한다.
3. **Gleo "기록 시작"이나 앱 안 버튼**: 1·2가 실패했을 때의 대비책이다.

> 시동을 끄면 저전력(Suspend) 상태가 된다. 다시 켰을 때 서비스가 복귀하는지는 문서에 없다(**실차 검증 사항**). 그래서 기획서에는 "자동 기록 목표, 안 되면 원터치 시작"으로 솔직하게 쓴다.

### 저장
- 30초마다 체크포인트를 쓰고, 상태가 바뀌는 순간에도 즉시 쓴다.
- 시동을 끄거나 프로세스가 죽은 뒤에는 마지막 체크포인트에서 복구한다.
- 시동 끔 이벤트는 오디오 정책에 따라 TTS를 멈추고 포커스를 반납한다.

---

## 6. 여정 상태 머신 (`JourneyStateMachine`)

```
          gear≠P                          거리 ≥ 0.3km
 IDLE ───────────────▶ PROVISIONAL ─────────────────────▶ DRIVING
  ▲                       │ P & 거리<0.3km (주차장 이동)     │  gear=P
  │                       └──────▶ 폐기 → IDLE              ▼
  │                                                    STOPPED ── gear≠P & (≤30분 & ≤500m) ──▶ DRIVING (같은 여정, stop 기록)
  │                                                       │
  └──────────── ENDED ◀── 도착(onDestinationArrived) 후 P 3분 / P 30분 경과 / 복구 시 30분 초과
```

- **도착 선물**: 아래 셋 중 하나가 되면 여정을 임시로 마감하고 선물을 띄운다.
  1. `onDestinationArrived` 후 P단
  2. 2km 이상 주행한 뒤 P단으로 3분
  3. 사용자가 앱을 엶
- 30분 안에 다시 출발하면 같은 여정으로 이어지고, 다음 선물에는 **새로 생긴 해금만** 보여준다.
- **최종 마감**은 P단으로 30분이 지났을 때다. 이때 제목, 내러티브, 해금 판정을 확정한다.
- **테스트 시나리오**: 주차장 왕복, 휴게소 정차 후 재출발, 시동 끔 후 1시간 뒤 재출발, 후진만 하고 끝, 프로세스 재시작.

---

## 7. 감각 계산 (`domain/sense`)

공통 입력은 `(timestamp, cumulativeKm)`이다. 출력은 구간 목록(`SenseSegment`)과 요약 값이다.

| 감각 | 판정 | 요약 값 | 제목에 쓰는 표현 |
|---|---|---|---|
| 비 | WiperState=ON인 구간. 강도는 Switch로 정한다: MIST·INTERMITTENT=1, CONTINUOUS_1~2=2, CONTINUOUS_3~5=3, AUTO=2 | 빗길 분·km, 최고 강도, 시작 시각 | "비 내리던", "소나기 속" |
| 별빛 | HighBeam=ON인 구간 | 별빛 길 km·분 | "별빛 길을 달린" |
| 밤·노을 | `SunCalculator`(위치·날짜)로 일몰 ±20분 주행이면 노을, 일몰 후면 밤 | 노을 주행 여부, 밤 주행 km | "노을 무렵", "밤길" |
| 바람 | 창문 열림 비율(최소~최대 범위 기준) 0.2 이상인 구간. 선루프는 따로 센다 | 바람 맞은 km, 연 창문(좌석 위치) | "창문을 열고" |
| 온기 | 열선·통풍 분, 제상 분, 외기온도 최저·최고(있을 때) | 태그: 추운 날, 더운 날, 김 서린 날 | "영하 4도 아침" |
| 동행 | 출발 시점과 주행 중 최대 착석 수(벨트로 보조) | 앞좌석만/뒷좌석 포함 → 운행타입 추천 근거(§7.1) | "가족과", "둘이서" |
| 장소 | 출발·도착 주소, 목적지 이름, 지나온 시·도와 시·군·구, 터널 수, 휴게소, 처음 간 지역 | | "강릉 경포해변 가는 길" |
| 목소리 | Gleo나 STT로 받은 문장과 그 순간의 맥락(위치, 비, 별빛, 동행, 창문) | 순간 목록 | 카드에 인용 |

- **거리(`DistanceTracker`)**: FusedLocation 궤적의 하버사인 거리를 쓴다. 5m 미만 이동과 시속 250km를 넘는 점프는 버린다. 궤적이 없으면 주행거리계 값으로 보정한다.
- **궤적 저장**: 30m 또는 15초 간격으로 줄여서 저장한다.
- **주소 파싱(`RegionParser`)**: "경기도 성남시 수정구 …" 형태에서 시·도와 시·군·구를 뽑는다. 광역시와 특별자치도 표기 변형은 테이블로 처리한다.
### 7.1 운행타입 분류 (`TripCategoryClassifier`)

운행타입은 4가지다: **출퇴근길(COMMUTE) · 여행(TRIP) · 데이트(DATE) · 일상(DAILY)**. 타입마다 해금 애셋이 다르다(§9). 앱은 추천만 하고 **사용자가 확정**한다.

**추천 규칙 (위에서부터 먼저 맞는 것이 1순위)**

| 순위 | 조건 | 추천 | 화면에 보이는 이유 |
|---|---|---|---|
| 1 | 출발지나 도착지가 회사 반경 300m 안 | 출퇴근길 | "회사에서 출발했어요" / "회사에 도착했어요" |
| 2 | 이번 운행 중 집에서 가장 멀리 간 지점이 직선거리 100km 이상 | 여행 | "집에서 132km 떨어진 곳까지 왔어요" |
| 3 | 운전석과 조수석만 착석, 뒷좌석은 비어 있음, 회사와 무관 | 데이트 | "앞좌석에 두 분이 함께했어요" |
| 4 | 그 외 | 일상 | — |

**판정 세부**
- 회사 판정: 출발 위치는 여정 시작 시 첫 유효 위치, 도착 위치는 `onDestinationArrived` 좌표 또는 마지막 P단 위치를 쓴다. 내비 목적지 이름·좌표가 회사 즐겨찾기와 같아도 회사로 본다.
- 100km 판정: 운행 중 위치마다 집과의 하버사인 거리를 계산하고 최댓값을 쓴다. 여행 이틀째 숙소 근처를 달리는 경우도 집에서 멀면 여행으로 추천된다.
- 착석 판정: `SeatOccupancy`를 기본으로 하고, 뒷좌석 벨트 착용도 착석으로 본다. 뒷좌석 신호를 지원하지 않는 차량은 "뒷좌석 알 수 없음"으로 두고 데이트를 추천하되, 이유 문구는 "앞좌석 두 분" 그대로 쓴다. 확정은 사용자가 한다.
- 집·회사가 등록되지 않았으면 해당 규칙을 건너뛴다. 집이 없으면 여행 추천이 불가능하므로 온보딩에서 등록을 권한다.

**묻는 시점**
- 기록이 저장되는 순간에 묻는다. 기본값은 **도착 후 P단**, 도착 선물 바로 앞이다. 이때는 회사 도착 여부, 최대 거리, 동승 여부가 모두 확정돼 있다.
- 주행 중에는 화면으로 묻지 않는다.
- 응답 방법은 세 가지다:
  1. 화면 칩 4개: 추천 항목이 먼저 선택돼 있고 이유가 함께 보인다.
  2. TTS "출퇴근길로 기록할까요?"에 STT로 예/아니오, 또는 다른 타입 이름으로 답한다.
  3. Gleo `SET_TRIP_CATEGORY`
- 10초 동안 응답이 없거나 화면을 벗어나면 추천값을 적용하고 "추천 적용"으로 표시한다. 아카이브에서 나중에 바꿀 수 있다.
- **해금은 확정된 타입 기준으로만 판정한다.** 추천이 틀렸을 때 엉뚱한 애셋이 열리는 것을 막기 위해서다. "추천 적용" 상태도 확정으로 간주하되, 타입을 바꾸면 해금을 다시 계산한다(이미 연 애셋은 유지).

---

## 8. 제목·내러티브 생성 (`domain/story`)

- **템플릿 기반으로 결정적으로 만든다.** LLM은 쓰지 않는다. LLM SDK는 제공이 종료됐고, 오프라인에서도 돌아야 하기 때문이다.
- **제목 우선순위**: 날씨 → 시간대 → 동행 → 목적지. 예: "비 내리던 노을 무렵, 가족과 강릉 경포해변 가는 길"
- **내러티브**: 3~5문장. 예: "출발한 지 21분쯤 비가 내리기 시작해 34분 동안 와이퍼가 바빴어요. 뒷좌석 창문으로 5.2km 바람을 맞았고, 마지막 8km는 별빛 길이었어요."
- **TTS 낭독**: 같은 문장을 읽는다.

---

## 9. 해금 엔진 (`domain/unlock`)

- **규칙 정의**: `assets/unlock_rules.json`에 선언형으로 둔다.

```json
{ "id": "trip_raincoat", "name": "노란 우비", "slot": "OUTFIT",
  "category": "TRIP",
  "condition": { "type": "FIRST_SENSE", "sense": "RAIN" },
  "hint": "여행길에서 처음 비를 만나면 열려요" }
```
- `category`는 COMMUTE / TRIP / DATE / DAILY / ANY 중 하나다. 확정된 운행타입이 일치할 때만 평가한다(§7.1).

- **조건 타입**:
  - 감각 관련: `FIRST_SENSE`(감각), `SENSE_KM_TOTAL_GTE`(감각, km), `SUNSET_DRIVE`, `FIRST_SNOW`(외기온도 0℃ 이하이면서 와이퍼 ON)
  - 지역 관련: `NEW_PROVINCE`, `PROVINCE_COUNT_GTE`, `REGION_STAY`(지역 코드, P단 체류 분) — 지역 제휴 목업용
  - 누적 관련: `CATEGORY_COUNT_GTE`(운행타입, n), `FIRST_CATEGORY`(운행타입), `COMMUTE_DAYS_GTE`, `MOMENTS_GTE`, `TUNNELS_TOTAL_GTE`, `REST_AREAS_GTE`
- **안전 원칙(`RuleSafetyPolicy`)**: 위험 행동의 **발생**(속도, 급제동, 주행 시간, 야간 주행 누적)을 보상 조건으로 쓸 수 없게 막는다. 위험 행동이 **없었던 것**을 보상하는 안심 보상(§9.1)만 허용한다. 단위 테스트로 위반을 잡는다.
- **판정 시점**: 도착 선물(임시 마감)과 최종 마감 때 평가한다. 결과는 `unlock` 테이블에 기록하고, 이미 연 것은 다시 열지 않는다.

**애셋 체계: 감각 × 운행타입 + 타입별 이정표**

같은 감각이라도 운행타입에 따라 다른 애셋이 열린다. "비 오는 출근길"과 "비 오는 여행길"은 다른 기억이기 때문이다. (초안이며 이름·디자인은 바꿀 수 있다)

| 감각 \ 타입 | 출퇴근길 | 여행 | 데이트 | 일상 |
|---|---|---|---|---|
| 첫 비 | 투명 우산 (소품) | 노란 우비 (옷) | 커플 우산 (소품) | 장화 (옷) |
| 첫 별빛 길 | 퇴근길 별 배지 (소품) | 별 망토 (옷) | 별자리 목걸이 (소품) | 손전등 (소품) |
| 바람 (창문 연 운행) | 느슨한 넥타이 (옷) | 밀짚모자 (모자) | 실크 스카프 (소품) | 바람개비 (방) |

| 타입 | 이정표 애셋 | 조건 |
|---|---|---|
| 출퇴근길 | 사원증 (소품) | 출퇴근길 5일 |
| 여행 | 여권 (소품) | 첫 여행 |
| 여행 | 시·도 배지 (방) | 새 시·도 방문 |
| 여행 | 강원 바다 우비 (옷) | 강원도에서 P단 10분 체류 (지역 제휴 목업) |
| 데이트 | 꽃다발 (방) | 첫 데이트 |
| 일상 | 장바구니 (소품) | 일상 10회 |
| 공통 | 일기장 (방) | 순간 기록 3개 |

**MVP 구현 범위 (11개, 시연에 필요한 것 우선)**
- 여행: 노란 우비, 별 망토, 여권, 강원 바다 우비
- 출퇴근길: 투명 우산, 사원증
- 데이트: 커플 우산, 꽃다발
- 일상: 장화, 장바구니
- 공통: 일기장

나머지 8개(별빛·바람 변형, 시·도 배지)는 2차 PT 전까지 추가한다. 외기온도 기반 "첫 눈길"은 에뮬레이터에서 시연할 수 없어 보류한다.

### 9.1 안심 보상 (위험 행동이 없었던 여정을 기념)

목적은 점수를 매기는 게 아니라 **"동승자와 무사히 함께 돌아온 여정"을 기념**하는 것이다. 안전운전 점수·육성 앱은 이미 많이 출품됐으므로, 점수·순위·실시간 경고는 두지 않는다.

**원칙**
1. **실시간 개입 없음.** 주행 중에는 경고도 칭찬도 하지 않는다. 도착 후에만 보여준다.
2. **보수적 판정.** 신호가 없으면 "판정 불가"로 두고, 안전한 것으로 간주하지 않는다. 오차를 감안해 기준은 너그럽게 잡는다.
3. **벌점·손실 없음.** 연속 기록이 끊겨도 이미 연 애셋은 그대로다. "다시 시작해요" 같은 부드러운 문구만 쓴다.
4. **더 많이 운전하게 만들지 않음.** 횟수보다 기간 기준(예: 2주)을 쓰고, 최소 거리(5km)를 둔다.
5. **데이터 최소 보관.** 속도 궤적은 저장하지 않는다. 여정별 집계(과속 횟수, 급감속 횟수, 벨트 체결 여부)만 남긴다.

**판정 기준 (`SafetyAccumulator`)**

| 항목 | 판정 | 판정 불가 조건 |
|---|---|---|
| 과속 없음 | 내비 제한속도 +10% 초과가 10초 이상 이어지면 과속 1회. 구간단속 평균속도 초과(`isOverSectionAvrSpeed`)도 1회 | 내비 제한속도 수신 시간이 운행의 70% 미만 |
| 급감속·급가속 없음 | 속도 변화율이 초당 10.8km/h(약 0.3g) 이상이면 1회 | 속도 신호 없음 |
| 모두 안전벨트 | 착석한 모든 좌석이 운행 내내 체결 | 벨트·착석 신호 미지원 |
| 쉬어가기 | 연속 주행 2시간이 되기 전에 P단 15분 이상 휴식 | 2시간 미만 운행은 해당 없음 |
| 어린이보호구역 준수 | 보호구역 주의 구간 통과 중 제한속도 이하 | 경로 안내가 없음 |

방향지시등 미사용은 차선 정보가 없어 판정할 수 없으므로 넣지 않는다.

**애셋 초안 (운행타입별)**

| 타입 | 애셋 | 조건 |
|---|---|---|
| 여행 | 무사 귀환 부적 (소품) | 여행을 마치고 집에 도착하기까지 과속·급감속 없음 |
| 출퇴근길 | 넥타이핀 (소품) | 2주 동안 출퇴근길이 모두 과속·급감속 없음 |
| 데이트 | 에스코트 장갑 (옷) | 조수석이 편안했던 데이트: 급감속·급가속 없음 3회 |
| 일상 | 동네 지킴이 배지 (방) | 어린이보호구역을 제한속도 이하로 통과 10회 |
| 공통 | 안전벨트 배지 (방) | 모두 안전벨트를 맨 여정 10회 |
| 공통 | 쉼표 베개 (소품) | 장거리에서 2시간 전에 쉬어가기 3회 |

**규칙 타입 추가**: `CLEAN_JOURNEY`(운행타입, 항목[], 최소 km), `CLEAN_PERIOD_DAYS`(운행타입, 항목[], 일수), `ALL_BELTED_COUNT`, `REST_BEFORE_2H_COUNT`, `SCHOOL_ZONE_COMPLIANT_COUNT`. 판정 불가인 여정은 세지 않는다.

**MVP 포함 후보**: 무사 귀환 부적, 에스코트 장갑. Virtual Console의 ACCEL/BRAKE로 속도와 급감속을 재현할 수 있다. 과속 판정은 에뮬레이터에서 제한속도가 오는지 확인한 뒤 결정한다.

---

## 10. Gleo Interface (`assets/schema.json`)

| 함수 | component | 파라미터 | 동작 | 응답 |
|---|---|---|---|---|
| `SAVE_JOURNEY_MOMENT` | `service` | `note: string` (필수) | 현재 맥락을 붙여 순간을 저장한다. 여정이 없으면 임시 여정을 만든다 | success, 메시지 "I saved this moment to your journey.", mode `voice` |
| `SET_TRIP_CATEGORY` | `service` | `category: enum[commute, trip, date, daily]` (필수) | 현재 또는 방금 끝난 여정의 운행타입을 확정한다(§7.1). 해금을 다시 계산한다 | success, "I recorded this drive as a date." |
| `SHOW_TODAY_JOURNEY` | `activity` | (없음) | P단이면 오늘 여정 카드를 연다. 주행 중이면 열지 않는다 | 주행 중: fail, "You can view it after parking." |

- **스키마 규칙**: description은 영어로 1~2문장, 예시는 넣지 않는다.
- **Manifest**: 함수 이름을 action으로 하는 intent-filter를 단다(`category.DEFAULT`). `service` 방식은 BroadcastReceiver로, `activity` 방식은 Activity로 받는다.
- **파라미터 수신**: `ACTION_PARAM` JSON(`actionParameters`)과 개별 extra **둘 다** 받는다. 예제 문서에 두 방식이 섞여 있기 때문이다.
- **입력 검증**: 길이 200자 제한, 빈 문자열이면 `asking`으로 되묻는다.
- **응답**: 7초 안에 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`를 브로드캐스트한다. extras는 `packageName`과 `response` JSON(`functionName`, `status`, `message`, `mode`, `data`)이다.
- **에뮬레이터 시연**:
  ```bash
  adb shell "am broadcast --user 10 -a SAVE_JOURNEY_MOMENT -p com.hour.app --es ACTION_PARAM '{\"actionParameters\":{\"note\":\"애들이 무지개 봤어\"}}'"
  ```
- **보안**: 아무 앱이나 브로드캐스트를 보낼 수 있다. 입력 길이·형식을 검증하고, 메모 저장 외의 부수 효과는 두지 않는다.

---

## 11. 화면 설계

**공통**
- 세로(1창)와 가로(2창) 레이아웃을 모두 만든다. 토큰 기준은 Connect-S 세로 840 / 가로 1700 / 높이 1184, Connect-L 628 / 1274 / 855다.
- Window size class로 분기하고, 레터박스와 호환성 모드는 금지한다.
- **CCP**: `com.hmg.rotary.ROTARY_SUPPORTED=false`로 선언하고 터치로만 조작을 완결한다.
- **최상위 화면 Back**: `moveTaskToBack(true)`. `finish()`는 금지다.

| 화면 | 언제 | 내용 | 규제 |
|---|---|---|---|
| 온보딩 | 첫 실행 (P단) | 기록 동의, 위치·마이크 권한, 집·회사(내비 즐겨찾기에서 자동으로 채움) | 주행 중 진입 불가 |
| 홈 | P단 | 캐릭터와 방(장착한 애셋), 최근 여정 카드, 운행타입 탭(전체·출퇴근길·여행·데이트·일상), 누적 감각 요약 | — |
| 여정 상세 | P단 | 제목, 내러티브, 감각 타임라인(구간 막대), 경로 스케치(지도 타일 없이 Canvas로 그림), 장소·순간 목록, "다시 가기" | — |
| 운행타입 확인 | 도착 후 P단, 도착 선물 직전 | "이번 운행은 어떤 길이었나요?" 칩 4개(출퇴근길·여행·데이트·일상), 추천 항목이 먼저 선택돼 있고 이유 한 줄. TTS로도 묻는다. 10초 동안 응답이 없으면 추천값 적용 | 주행 중에는 띄우지 않음 |
| 도착 선물 | P단 + 마감 트리거 | 카드 뒤집기, 해금 연출, "낭독 듣기" | 애니메이션은 P단에서만 |
| 옷장 | P단 | 슬롯 3개와 방, 해금한 애셋, 잠긴 애셋은 실루엣과 힌트 | — |
| 주행 중 미니 | D단 (`distractionOptimized=true`) | "기록 중 · 34분 · 21km", 감각 아이콘 상태, 큰 버튼 "이 순간 기억"(STT) 하나 | 애니메이션·목록·키보드 없음. 규제 플래그를 받아서 반영 |
| 설정 | P단 | 낭독 켜기·끄기, 기록 일시정지, 여정 삭제, 집·회사 재설정 | `NO_SETUP`이면 주행 중 접근 차단 |

**디자인**
- Figma 토큰을 쓴다: 색은 basic·alpha, 텍스트 스타일은 Title 40/36/32와 Body 30/28/26, 모서리와 간격은 공통 스케일.
- 캐릭터는 레이어 합성 방식이다. 기본 몸 위에 모자, 옷, 소품을 겹치고, 방에는 배경과 진열 소품을 둔다.

---

## 12. 데이터 모델 (Room)

```
journey(id PK, startedAt, endedAt, state,
        category[COMMUTE|TRIP|DATE|DAILY], suggestedCategory, categorySource[USER|GLEO|AUTO_DEFAULT],
        suggestionReason, maxKmFromHome, touchedOffice, frontOnlyOccupancy,
        companions, startAddress, endAddress, destName, distanceKm, durationSec,
        title, narrative, senseSummaryJson, safetySummaryJson, giftShownAt)
anchor(type PK[HOME|OFFICE], lat, lon, name, address, source[NAVI_BOOKMARK|MANUAL], updatedAt)
sense_segment(id PK, journeyId FK, sense, startAt, endAt, startKm, endKm, level, metaJson)
track_point(journeyId FK, t, lat, lon)                       -- 30m/15s 간격으로 줄여 저장
place_visit(id PK, journeyId FK, kind[START|END|STOP|WAYPOINT|REST_AREA|TUNNEL|TOLL],
            name, address, province, district, lat, lon, arrivedAt, dwellSec)
moment(id PK, journeyId FK, at, text, source[GLEO|STT], lat, lon, address, contextJson)
region_seen(code PK, province, district, firstVisitedAt, journeyId)
unlock(ruleId PK, unlockedAt, journeyId, seen)
wardrobe(slot PK, ruleId)
checkpoint(journeyId PK, at, draftJson)
```

- DataStore에는 동의 여부, 집·회사 좌표, 낭독 설정, 서비스 마지막 상태를 둔다.
- 데이터 양은 운행 1회에 수백 행 수준이다.

---

## 13. 오디오 정책

- **용도**: TTS는 `USAGE_ASSISTANT`로 출력하고, 일시 포커스(덕킹 허용)를 요청한다. 끝나면 즉시 포커스를 반납한다.
- **내비 우선**: TBT 남은 거리가 300m 안이면 앱 음성을 보류한다. 내비 음성이 우선이다.
- **주행 중 발화**: Gleo나 STT 기록을 확인하는 짧은 한마디만 한다. 해금 알림은 도착 후에만 한다.
- **시동 끔**: 즉시 멈추고 포커스를 반납한다. 다시 켰을 때 자동으로 재생하지 않는다.

---

## 14. 성능·안정성 목표

| 항목 | 기준 (공식) | 목표 |
|---|---|---|
| 주행 중 CPU | 비내비 앱 5% 미만 | 3% 미만 (리스너 기반, 위치 1Hz) |
| 정차 중 CPU | 25% 이하 | 애니메이션 포함 15% 미만 |
| RAM | 400MB 이하 | 200MB 미만 |
| 실행 | 10초 이하, 터치 응답 0.25초 | 콜드 스타트 2초 미만 |

- **지원 여부 확인**: 서비스 시작 때 `SenseAvailabilityProbe`가 `check*Capabilities`를 일괄로 조회한다. 결과(`SenseAvailability`)에 따라 지원하지 않는 감각은 UI와 규칙에서 숨긴다.
- **예외 처리**: 모든 `onFailed`를 처리하고 `VehicleException.errorCode`로 로그를 남긴다. 실패해도 앱은 멈추지 않는다.
- **NaviHelper**: 콜백 전달이 보장되지 않는다. 타임아웃을 두고 상태 기반으로 처리한다.

---

## 15. 개인정보·보안·심사

- **데이터**: INTERNET 권한이 없다. 위치·음성 메모는 기기 안에만 있고 내보내기 기능도 없다.
- **사용자 권리**: 여정 삭제와 전체 삭제를 제공한다. 동의 화면에 처리 방침을 쓴다.
- **release 빌드**: `debuggable=false`, 자체 키스토어로 서명(키는 저장소에 넣지 않음), 버전은 제출할 때마다 올린다.
- **블라인드 심사**: 저장소, 커밋 작성자, README, 영상 어디에도 실명·계정이 드러나지 않게 한다.
- **APK 파일명**: `팀명_Hour_release.apk`, 위치는 `apk/`.

---

## 16. 테스트 전략

1. **도메인 단위 테스트 (JVM)**: 상태 머신 시나리오 10개, 감각 누적기, 제목 생성, 해금 규칙 12개와 안전 원칙, `SunCalculator`(알려진 일몰 시각과 비교), `RegionParser`(주소 표본).
2. **시나리오 재생**: JSON 타임라인을 Fake 게이트웨이로 흘려 넣고 기대한 요약과 해금이 나오는지 본다. 같은 장치를 디버그 빌드의 "시연 재생"으로도 쓴다.
3. **에뮬레이터 수동 E2E**: Virtual Console 조작, `adb emu geo fix`, Gleo adb 브로드캐스트, 주행 규제 on/off.
4. **성능**: 시연 중 `adb shell top`과 `dumpsys cpuinfo`로 CPU와 메모리를 확인한다.

---

## 17. 시연 시나리오 (2분 영상)

1. **(사전)** 디버그 시드로 지난 여정 5건을 넣는다. 영상에는 "시연용 샘플 데이터"라고 표기한다. 홈에 캐릭터와 운행타입 탭을 보여준다.
2. **P단**: 출발 카드 "동행 3명 · 강릉 경포해변". 내비에 목적지를 넣는다.
3. **D단**: 주행 중 미니 화면이 뜬다.
   - Virtual Console에서 와이퍼를 Low → High로 바꾼다 (소나기).
   - adb로 Gleo `SAVE_JOURNEY_MOMENT` "애들이 무지개 봤어"를 보낸다 → "기억했어요".
4. Virtual Console에서 뒷좌석 창문을 60%로 3분 동안 연다. 그다음 상향등을 켠다 (해 질 녘 위치·시각).
5. **도착 후 P단**: 운행타입 확인이 뜬다. "집에서 132km 떨어진 곳까지 왔어요. 여행으로 기록할까요?"(여행이 먼저 선택됨) → 확정.
   이어서 도착 선물이 뜬다. 카드 → 여행 애셋(노란 우비, 별 망토, 여권, 강원 바다 우비) 해금 → TTS 낭독.
   (보조 장면: 회사 즐겨찾기 근처에서 P단 → "출퇴근길" 추천이 뜨는 것을 짧게 보여준다)
6. 여정 상세의 감각 타임라인, "다시 가기" 버튼, 옷장에서 우비 장착을 보여준다.

---

## 18. 일정 (11/12~11/15)

| 날 | 작업 | 끝났다는 기준 |
|---|---|---|
| D1 (11/12) | 프로젝트 골격(샘플 앱 기반), 게이트웨이 어댑터, 서비스 IDLE↔RECORDING, **검증 체크리스트 10개** | 에뮬레이터에서 기어·와이퍼·창문 이벤트가 로그로 찍힘 |
| D2 (11/13) | 도메인 전체(상태 머신, 감각, 제목, 해금)와 테스트, Room | 시나리오 재생 테스트 통과 |
| D3 (11/14) | UI(홈, 상세, 도착 선물, 옷장, 주행 중 미니), TTS·STT, Gleo 수신부 | 시연 시나리오가 처음부터 끝까지 동작 |
| D4 (11/15) | 시연 영상, README, release 서명 APK, 블라인드 점검, 접수 | 메일 발송 (마감 23:59) |

**밀리면 줄일 순서**: 안개등 → 외기온도 → 선루프 → 다시 가기 → STT 버튼(Gleo adb 시연만 유지) → 가로 레이아웃 고도화.

---

## 19. Day 1 검증 체크리스트 (설계 가정)

1. Pleos `CAR_*` 일반 권한이 에뮬레이터에서 자동으로 허용되는지, `FUSED_LOCATION` 런타임 요청 흐름
2. `specialUse` 포그라운드 서비스가 user 10에서 시작되는지, `BOOT_COMPLETED`/`USER_UNLOCKED` 수신 여부
3. AAOS `GEAR_SELECTION`·`PERF_VEHICLE_SPEED` 조회에 필요한 권한 이름 공간 확인: AAOS 표준 `android.car.permission.CAR_POWERTRAIN/CAR_SPEED`인지, 공식 샘플 manifest에 있는 `pleos.car.permission.CAR_POWERTRAIN/CAR_SPEED`인지(또는 둘 다). Virtual Console 기어 연동
4. 창문 위치 값의 방향(0=닫힘?)과 범위, Virtual Console 0–100%와의 대응
5. 착석 지원 영역(SeatArea 목록), 벨트 상태 주입 가능 여부
6. 경로 안내가 없을 때 `onCurrentLocationInfo` 주소 콜백이 오는지, `onTBTInfo` 터널 타입이 오는지
7. 와이퍼 Low/High/Auto가 WiperSwitch 어떤 값으로 들어오는지
8. TTS·STT ON_DEVICE 한국어가 에뮬레이터에서 동작하는지
9. Gleo adb 브로드캐스트 수신과 `APP_ACTION_RESPONSE` 송신
10. 주행 규제 on/off(adb)에 따른 DO 화면 동작, CCP Back(`moveTaskToBack`)
11. `PERF_VEHICLE_SPEED` 권한(`CAR_SPEED`) 승인 방식, 내비 모의주행에서 `onCurrentRoadSpeedLimit`·벨트 상태가 오는지

가정이 틀리면 대안이 정해져 있다.
- 4·5번이 틀리면: 해당 감각을 숨긴다.
- 6번이 틀리면: 시·군·구를 알 수 없다고 처리하거나 오프라인 행정구역 데이터를 쓴다.
- 2번이 틀리면: 앱을 열 때 시작하는 방식으로 바꾼다.


### D1 검증 결과 (2026-11-12, 에뮬레이터 v2.1.0, user 10)

| # | 항목 | 결과 | 확인 내용 / 설계 반영 |
|---|---|---|---|
| 1 | 권한 | ✅ | `pleos.car.permission.CAR_*`·`NAVI_*`·`TTS/STT_SERVICE`와 AAOS `CAR_POWERTRAIN`·`CAR_EXTERIOR_ENVIRONMENT`는 설치 시 자동 허용(일반). **런타임**: `FUSED_LOCATION`("위치 정보" 창), `android.car.permission.CAR_SPEED`(**"알 수 없는 작업 실행" 창** → 온보딩에서 미리 설명 필요), `RECORD_AUDIO`, `POST_NOTIFICATIONS` |
| 2 | 자동 시작 | ✅ (에뮬레이터) | 재부팅 후 user 10에서 `BOOT_COMPLETED` 수신 → `specialUse` FGS 백그라운드 시작 허용("Background started FGS: Allowed"). 실차 Suspend→Resume은 부팅이 아니므로 **실차 검증 사항으로 남김** |
| 3 | 기어·속도 권한 | ✅ | AAOS 표준 `android.car.permission.*`이 맞다. 샘플의 `pleos.car.permission.CAR_SPEED/CAR_POWERTRAIN`은 **시스템에 정의돼 있지 않음**(선언해도 무의미, 제거). `GEAR_SELECTION`(PARK=4, DRIVE=8)·`PERF_VEHICLE_SPEED`·`ENV_OUTSIDE_TEMPERATURE`(25℃ 고정) 수신 |
| 4 | 창문 | ✅ | 범위 0..10, 0=닫힘. Virtual Console 60% → 6, 100% → 10 |
| 5 | 착석·벨트 | ⚠️ | 착석·벨트 모두 5개 영역(1열 좌우, 2열 좌·중·우) 지원. 그러나 **에뮬레이터 착석값이 Virtual Console 상태와 일치하지 않음**(초기값이 전부 occupied, 임의로 바뀜). 시연에서는 동행 인원을 Gleo 태그·운행타입 확인으로 보완하고, 착석 신호는 보조로만 쓴다 |
| 6 | 내비 | ◐ | 경로 안내 없이도 `onCurrentLocationInfo` 주소("수정구 시흥동") 수신 ✅, `onCurrentRoadSpeedLimit` 매초 수신(정차 중 0) ✅, 즐겨찾기 HOME/OFFICE 미등록 시 이름·주소 빈 값 + 좌표 0,0 → 미등록으로 처리 ✅. **TBT(터널·휴게소)·주의 구간·도착 이벤트는 경로 안내가 필요해 D2에 `requestRoute`로 확인** |
| 7 | 와이퍼 | ✅ | 지원 스위치: OFF / CONTINUOUS_LEVEL_1 / CONTINUOUS_LEVEL_3 / AUTO. VC Low→C1, High→C3, Auto→AUTO. 단계 변경은 상태(ON) 변화가 없어 리스너가 안 옴 → 와이퍼 ON 동안 **5초 주기 조회**로 변경 |
| 8 | TTS·STT | ✅ | 기기 내 모드로 한국어 TTS 재생(onStart→onDone), STT 초기화·대기 정상. **INTERNET 권한을 제거해도 동작**. TTS는 `initialize` 성공 후에 리스너를 붙여야 함(먼저 붙이면 SDK 내부 예외) |
| 9 | Gleo | ✅ | adb 브로드캐스트로 `ACTION_PARAM` 평문 JSON, `actionParameters` 래퍼, 개별 extra 세 형식 모두 파싱. 빈 값 → `asking`. `SET_TRIP_CATEGORY`, `SHOW_TODAY_JOURNEY`(activity) 응답 확인. 에뮬레이터에 Gleo 음성 앱은 없음 |
| 10 | 주행 규제·Back | ✅ | 차량이 움직이면 플랫폼이 자동으로 DO=true, active=80을 내려줌 → 앱이 주행 중 미니 화면으로 전환(스크린샷 확인). Back 키 → `moveTaskToBack(true)=true`, 시스템 처리 로그 확인 |
| 11 | 안심 보상 신호 | ◐ | 속도 수신 ✅(요청 주기와 무관하게 초당 5~10회 → 어댑터에서 1km/h 변화 또는 1초 단위로 줄임). 제한속도는 정차 중 0만 확인. 벨트 상태는 VC에 조작 항목이 없어 변화 미확인 |

**추가로 알게 된 것**
- **Virtual Console HTTP API**: 브라우저 없이 `PUT /vehicle/gear?gear=Drive`, `/vehicle/controls?speed=..`, `/vehicle/body?front_wiper=High&window_rl=60`, `/vehicle/lights?feature=high_beam&value=true`, `/vehicle/seat?kind=occupied&area=passenger&value=false`, `/vehicle/hvac?driver_seat=2&front_defroster=true`, `/vehicle/odometer?traveled_distance_km=..`로 신호를 넣을 수 있다. 단, `POST /browser/session?client=<id>` 하트비트를 1초마다 보내야 한다(없으면 503). → `scripts/vc.sh`로 정리, 시연 녹화와 E2E에 사용
- **FusedLocation과 내비 위치가 다르다**: FusedLocation은 에뮬레이터 GPS(기본값 구글 본사 좌표, `adb emu geo fix`로 변경), 내비는 자체 위치(성남시 수정구)를 쓴다. 시연 전 두 위치를 맞춰야 한다
- **중복 이벤트**: 리스너 등록 직후 초기값이 2~3번 오고 주행거리계는 같은 값을 4초마다 다시 보낸다 → 어댑터에서 값이 같으면 버리도록 처리
- TTS/STT SDK가 `INTERNET` 권한을 병합하므로 `tools:node="remove"`로 제거했다. 기기 내 모드만 쓰는 한 문제 없음


---

## 20. D2 결과 (2026-11-12)

**구현**
- `:domain`: 여정 상태 머신, 거리(위치·주행거리계·속도 적분 중 최댓값), 일출·일몰, 시·도 추정, 감각 누적기 6종 + 안심 판정, 운행타입 추천, 제목·내러티브, 해금 엔진(조건 15종)과 안전 원칙, 체크포인트 직렬화
- 임시 해금 규칙 13개는 `domain/src/main/resources/unlock_rules.json`에 있다. 게임 디자인이 확정되면 이 파일만 바꾼다.
- `:app`: Room(여정 요약 JSON + 목록 컬럼, 해금, 집·회사, 체크포인트), 기록 서비스 이벤트 루프(단일 스레드, 30초 틱·체크포인트, 알림에 "기록 중 · N분 · Nkm"), Gleo `SET_TRIP_CATEGORY` → 운행타입 확정 → 해금 재평가
- 테스트 22개 통과: 강릉 여행(비·바람·별빛·순간, 경기→강원, 여행 추천), 출퇴근·데이트 추천, 주차장 이동 폐기, 휴게소 정차 병합, 체크포인트 복원 후 마감, 과속·급감속, 일몰 시각, 주소·좌표 시·도, 해금·안전 원칙

**에뮬레이터 통합 확인** (`scripts/scenario_drive.sh`)
- 출발 → 비(High) → 뒷창문 → Gleo 순간 기록 → 상향등 → 주차 → 기록 마감 → 요약·내러티브·해금 저장까지 한 번에 동작
- 운행타입 자동 적용(일상) 후 Gleo로 "여행" 확정 → 여행 애셋 3개 해금

**발견한 것 / 다음에 정할 것**
- **운행타입 확인 시점이 중요하다.** 지금은 확인 화면이 없어 마감 시 추천값(일상)을 자동 적용했고, 나중에 여행으로 바꿔도 먼저 열린 '장화'는 남는다(이미 연 애셋 유지 원칙). D3에서 도착(P) 시 확인 화면을 띄우면 대부분 해소된다. 바꾼 경우 자동 적용으로 열린 애셋을 회수할지는 게임 디자인 결정 사항이다.
- **에뮬레이터 인공물**: Virtual Console은 속도를 0→80으로 즉시 바꾸므로 급가속·급감속이 잡히고, `adb emu geo fix` 이동 속도가 설정 속도와 달라 구간 거리(바람 km 등)가 부풀 수 있다. 실차에서는 신호가 서로 맞으므로 영향이 작다. 시연 스크립트는 위치 이동 속도를 차속에 맞추는 쪽으로 다듬는다.
- 착석 신호가 에뮬레이터에서 전부 '착석'이라 동행 5명·데이트 추천 불가 → 시연은 운행타입 확인 화면(또는 Gleo)으로 보완
- **남은 D1 항목**: 경로 안내 중 TBT(터널·휴게소)·도착 이벤트 확인은 D3에서 `requestRoute`로 진행한다(도착 선물 트리거와 함께).

---

## 21. D3 결과 (2026-11-12)

**구현**
- 화면: 홈(캐릭터·방, 운행타입 탭, 여정 카드, 권한·기록 안내), 여정 상세(내러티브·낭독, 감각 막대, 경로 스케치, 다녀온 곳, 기억한 순간, 안심 운행), 운행타입 확인 시트, 도착 선물, 옷장(슬롯별 장착, 잠긴 애셋 실루엣·힌트, 새 선물 표시), 주행 중 미니(DO). 진단 화면은 디버그 빌드에만 둔다.
- **도착 흐름(`ArrivalFlow`)**: 기록 서비스의 도착 선물 준비(GiftReady) → TTS 질문("도착했어요. 집에서 165km 떨어진 곳까지 왔어요. 이번 여정을 여행으로 기록할까요?") + 알림(운행타입 버튼 3개, 추천이 첫 번째) + 화면 시트(추천 선택, 10초 무응답이면 추천 적용). 화면·알림·Gleo `SET_TRIP_CATEGORY` 어느 쪽으로 답해도 같은 경로로 확정 → 해금 평가 → 도착 선물(카드 뒤집기, 애셋 순차 등장, 낭독).
- **해금 시점 변경**: 운행타입이 확정되기 전에는 해금을 평가하지 않는다. D2에서 발견한 "자동 추천값으로 다른 타입의 애셋이 먼저 열림" 문제가 해소됐다. 끝까지 답이 없으면 최종 마감(P 30분) 때 추천값(AUTO_DEFAULT)으로 평가하고, 다음에 앱을 열면 선물로 보여준다.
- 캐릭터 '아워'(가칭): 자명종 시계 모양 동반자. 임시 그림은 코드(Canvas)로 그리고, `assets/art/`에 같은 이름의 그림 파일을 넣으면 자동 교체된다([ART-ASSETS.md](ART-ASSETS.md)).
- "이 순간 기억": 주행 중 미니의 큰 버튼 → STT 최종 결과를 순간 기록(MomentSource.STT)으로 저장, "기억했어요"로 확인. `VoiceIO.transcripts` 흐름을 추가했다. TTS·STT 호출은 메인 스레드로 보낸다.
- 시연 준비(디버그 전용): 샘플 여정 5건, 강릉 도착 미리보기, 초기화. 진단 화면 버튼과 adb 인텐트(`com.hour.app.debug.SEED` / `PREVIEW_ARRIVAL` / `RESET`) 둘 다.
- 내러티브의 주행 시간을 "2시간 38분"처럼 표기한다.

**에뮬레이터 확인**
- 홈(빈 상태·샘플 5건), 운행타입 확인 시트(추천 '여행' 선택·카운트다운·TTS 질문·알림 버튼 3개), 확정 → 도착 선물(여행 애셋 5개: 노란 우비·별 망토·여권·강원 바다 우비·무사 귀환 부적), 옷장 장착(우비·여권·꽃다발 선반), 여정 상세, 주행 중 미니(D단에서 DO=true로 전환)를 화면으로 확인했다.
- **실제 주행 흐름**(`scripts/scenario_drive.sh`): 출발 → 비·창문·상향등 → 주차 → 3분 뒤 도착 판정 → TTS 질문 + 시트(추천 '일상') → 10초 무응답으로 추천 적용(AUTO_DEFAULT) → 도착 선물(장화) + 선물 낭독까지 한 번에 동작했다. 기록 서비스 스레드에서 부른 TTS도 정상 출력됐다.
- **Gleo로 답하기**: 확인 시트가 떠 있을 때 `SET_TRIP_CATEGORY(trip)` → 그 여정이 여행으로 확정되고 선물 화면이 열린다. 처음에는 "가장 최근에 시작한 여정"을 확정해서 다른 여정이 바뀌는 문제가 있었고, 확인을 기다리는 여정을 먼저 고르도록 고쳤다.

**남은 것 (D4)**
- `requestRoute`로 경로 안내 중 TBT(터널·휴게소)·도착 이벤트 확인 → 도착 즉시 선물(3분 대기 없이)
- 설정(낭독 끄기·기록 일시정지), 온보딩, Connect-L(좁은 화면) 레이아웃 점검
- release 서명 APK, 시연 영상, README 마무리, 블라인드 점검
