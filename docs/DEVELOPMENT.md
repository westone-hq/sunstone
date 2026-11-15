# Sunstone 개발 내용

차에 이미 있지만 잘 쓰이지 않는 기능을, 필요한 순간에 운전자에게 먼저 권하고 말 한마디로 켜고 끄는 차량 루틴 앱이다.
무엇을 만드는지와 세부 규칙은 [SPEC.md](SPEC.md), 써 보는 방법은 [GUIDE.md](GUIDE.md)에 있다. 이 문서는 어떻게 만들었는지를 적는다.

---

## 1. 두 모듈

| 모듈 | 하는 일 |
|---|---|
| `routines/` | 무엇을, 언제 할지 정한다(루틴, 권하는 규칙, 대화, 말 알아듣기). 순수 Kotlin이라 안드로이드·Pleos 없이 테스트로 끝까지 돈다 |
| `app/` | 어떻게 할지 맡는다. Pleos SDK(차·내비·위치·음성), 바깥 API, 왼쪽 구역 카드, 시연 모드 |

두 모듈이 만나는 곳은 **창구(포트)** 몇 개뿐이다(3장). `routines/`는 창구만 알고, `app/`이 실제 SDK·API로 창구를 채운다.

지킨 원칙

- 차를 바꾸는 일은 "원래 값 저장 → 실행 → 다시 읽어 확인" 순서로 하고, 실패한 단계만 음성 안내로 바꾼 뒤 루틴은 계속 간다.
- 되돌리기는 저장한 원래 값으로 한다. 창문은 다시 열지 않고 문 잠금은 풀지 않는다.
- 차를 바꾸는 일은 정차 중에만, 시트는 P단에서만 한다.
- 실행 결정은 규칙으로 한다. 판단에 쓰는 숫자는 이름 붙인 상수로 두고 근거를 주석에 적었다.
- 프로토타입이라 조작하거나 우회한 곳은 주석 `미구현: 프로토타입이라 …`에 이유를 적었다.

---

## 2. 폴더

```
sunstone/
├─ README.md
├─ docs/
│  ├─ SPEC.md              기능 명세(무엇을 만드는가, 세부 규칙)
│  ├─ GUIDE.md             사용 방법(설치, 시나리오 따라 하기, 안 될 때)
│  ├─ DEVELOPMENT.md       이 문서
│  ├─ SCREEN.md            화면 규격, 시나리오 장면별 상세(설계 기록)
│  └─ api-spike.md         Pleos API·바깥 API 실측 기록
├─ apk/                    심사용 release APK(westone_Sunstone_release.apk). assembleRelease가 복사한다
├─ tools/scene-move.ps1    시연 이동: geo fix 1초마다 + 콘솔 속도·기어 (시나리오 ①·②)
├─ tools/talk.ps1          터미널에서 말·장면(/ready1 /move1 /leave1)을 넣고 앱 대답 보기
├─ tools/configure-api.ps1 바깥 API 키(local.properties) → 기기 안 설정 파일. 키는 저장소·APK에 넣지 않는다
├─ tools/make-voice.ps1    대사집 문장 → 녹음본(app assets/voice, Windows 한국어 음성),
│                          운전자 대사 → 다른 목소리 녹음본(app assets/driver)
│
├─ routines/               기능 구현(순수 Kotlin)
│  └─ src/main/kotlin/com/westone/sunstone/routines/
│     ├─ model/            차 상태와 기본 값(기어·창문·문·좌석·장소)
│     ├─ port/             창구: CarPort, Speaker, SeatProfiles, NaviPort, InfoPort, MemoryPort
│     ├─ engine/           루틴 정의 틀, 실행 엔진(저장→실행→확인→되돌리기→권하기)
│     ├─ library/          루틴들 (초안: 세차·차일드락·시트 메모리)
│     ├─ understand/       말 알아듣기 (대사집 먼저 → 규칙: 길안내·질문·대답·루틴)
│     ├─ speech/           문장 틀 (세차·길안내·기능 카드 답, 조사 붙이기)
│     ├─ dialog/           대화 흐름: 길안내 → 주유·세차 한 번 묻기 → 주유소 경유지·세차 예약(TripDialog, WashBooking),
│     │                    묻는 질문·토스트(Asking: 길안내 대화와 루틴 권하기가 함께 쓴다),
│     │                    지금 길안내 목적지(CurrentRoute: 차일드락이 하원길·도착을 본다),
│     │                    시트 맞추기 대화(SeatDialog: 말로 조금씩 맞추기, 이름으로 기억)
│     ├─ knowledge/        AI 참고 문서: 기능 카드(지금은 세차 모드 하나), 별칭 검색 상위 3개
│     ├─ script/           대사집(ScriptBook: 시나리오 ① S1~S9, ② S10~S18, ③ S19~S25). 자동 테스트·scene·[다음 장면]이 함께 쓴다
│     └─ fake/             가짜 차·내비·바깥 정보 (테스트·시연)
│
└─ app/                    앱 연결(Pleos SDK, 바깥 API, 화면, 시연 모드)
   └─ src/
      ├─ main/java/com/westone/sunstone/
      │  ├─ car/           PleosCarPort(차량 SDK·제어), Callbacks(SDK 콜백 기다리기),
      │  │                 AaosSpeedSource(속도), AaosEnergySource(주행 가능 거리·배터리·연료 종류),
      │  │                 FuelState(연료·배터리·차 종류 값 묶음)
      │  ├─ connect/       GleoEntry, PrefsSeatProfiles, PrefsMemory(마지막 세차일·차일드락 날),
      │  │                 AndroidSpeaker(말하기 창구) ← VoiceLibrary(문장 → 녹음본),
      │  │                 VoicePlayer(Pleos TTS·녹음본을 순서대로 내기·끊기), PleosEar(Pleos STT로 듣기),
      │  │                 KeepAwakeService(Pleos 차에서 앱을 깨어 있게 하는 알림)
      │  ├─ navi/          PleosNavi(내비 경로·경유지·남은 거리·도착), Proximity(근접 알림),
      │  │                 PlaceWatcher(시연 장소 도착 → 장소), NaviModels(좌표·경로 값),
      │  │                 PleosNaviPort(기능 쪽 내비 창구를 PleosNavi로 채움)
      │  ├─ info/          바깥 정보. 시연 중에는 FixedInfo(준비 값), 아니면 LiveInfo(실제 값):
      │  │                 OpinetStations(근처 주유소 가격·세차 시설 여부), KakaoPlaces(목적지 후보·주유소·
      │  │                 좌표 변환), OpenMeteo(시간별 예보), WeatherLocation(지금 위치),
      │  │                 WeatherConversation("오늘 비 와?"에 답하기), ApiKeys(기기 안 키 파일), ApiHttp
      │  ├─ ui/            MainActivity(화면 없는 입구: 부르는 카드를 띄우고 닫힌다),
      │  │                 SunstoneScreen(왼쪽 구역 창 허가가 없을 때만: 빈 바탕에 같은 카드),
      │  │                 SceneControls(시연 모드 창구. 구현은 demo/ScenePlayer)
      │  │  └─ panel/      왼쪽 구역 화면: PanelModels(CardUi·RowUi·DemoUi·ListenUi·Plate 값),
      │  │                 PanelPresenter(부르는 카드·Asking·실행 상태 → 화면 값, 고른 버튼 1초,
      │  │                 켜짐 표시 3초, 듣는 중 8초·토스트),
      │  │                 PanelViews·PanelTheme(Compose 카드·듣는 중 줄·마이크 칸, 색·Asta Sans),
      │  │                 LeftZoneOverlay(왼쪽 구역 창 둘: 마이크 칸, 카드. 카드는 뜨는 동안 미디어 줄 위까지)
      │  ├─ demo/          시연 모드(프로토타입이라 release에도 둔다): ScenePlayer([다음 장면]·scene S3,
      │  │                 콘솔 연결 신호 1초마다), DriverVoice(운전자 대사 녹음본),
      │  │                 VirtualConsole(앱 안에서 콘솔 HTTP API 읽기·쓰기)
      │  └─ SunstoneApp.kt AppGraph: 두 영역 조립, 모든 입력의 입구(hear·handle), 시연 모드
      ├─ main/res/values/  strings.xml (앱 문구. 루틴 문장·질문 패널 글자는 routines/)
      ├─ main/res/         font/asta_sans.ttf(Asta Sans, OFL), drawable/ic_panel_*(패널 아이콘),
      │                    mipmap-anydpi-v26/ic_launcher(앱 아이콘), xml/network_security_config(콘솔만 http)
      ├─ main/assets/      schema.json(Gleo), places.json(시연 장소 고정 목록), licenses/(글꼴 라이선스),
      │                    voice/(대사집 녹음본 <문장 MD5>.wav, tools/make-voice.ps1이 만든다),
      │                    driver/(운전자 대사 녹음본 <문장 MD5>.wav, 앱과 다른 목소리. 시연 `DriverVoice`가 낸다)
      ├─ debug/            release 빌드에는 없음(adb 데모 명령과 실측)
      │  ├─ DemoReceiver   adb 장면 주입·실측 명령 입구(scene, washed 포함)
      │  ├─ CarProbe       probe: 제어 대상별 바꾸기·되돌리기 실측
      │  ├─ CarAreas       areas: 차가 받는 area 목록
      │  ├─ CarSeatRead    seatread: AAOS 속성으로 시트 위치 읽기 시도
      │  ├─ SeatTimer      seattime: 시트 축 0→100 이동 시간
      │  ├─ SeatFit        seatfit: 시간 기반 시트 이동 오차
      │  ├─ SeatReadCheck  seatcheck: 시트 위치 지원 목록·리스너 재확인
      │  ├─ NaviProbe      navi: 내비 경로·경유지·남은 거리·통행료·도착
      │  ├─ LocationProbe  location: FusedLocation 위치·근접 알림
      │  ├─ BeltProbe      beltprobe: 좌석별 벨트·미착용 경고·벨트 경고등
      │  ├─ ChildLockProbe childlock: 뒷문 차일드락(AAOS 속성) 읽기·쓰기
      │  └─ OverlayProbe   overlay: 왼쪽 구역 오버레이 창·키 수신 실측
```

---

## 3. 창구 (두 모듈의 약속)

| 창구 | 방향 | 지금 | 더할 것 |
|---|---|---|---|
| `CarPort` | 기능 → 차 | 상태 읽기(연료 % 포함), 값 읽기, 바꾸기 | 주행 가능 거리 |
| `Speaker` | 기능 → 사람 | 말하기 | |
| `SeatProfiles` | 기능 → 저장소 | 자세 저장·조회 | |
| `NaviPort` | 기능 → 내비 | 목적지 찾기(시연 장소 목록), 경로 시작, 경유지 1개, 통행료 | 남은 거리, 도착 알림 |
| `InfoPort` | 기능 → 바깥 | 주변 주유소(가격·세차), 비가 오는 가장 가까운 날. 시연 중에는 준비 값, 아니면 오피넷·카카오·Open-Meteo | 미세먼지 |
| `MemoryPort` | 기능 → 저장소 | 마지막 세차일, 마지막 차일드락 날 | 방문 기록 |
| `Asking` | 기능 → 화면 | 대답을 기다리는 질문(`Question`: 종류·제목·설명·굵은 낱말·근거 칸 `Fact`·할 일 칸 `QuestionRow`·고를 것 `Choice`), 토스트 문장. 고를 것은 버튼 글자와 대답 값(`Reply`: YES·ALL·ONLY·NO)이고, 첫 번째가 채운 버튼이다. `Choice.waitMs`가 있으면 그 시간 뒤 루틴 실행기가 고른 것으로 본다(세차 입구 5초). 말로 해도 버튼으로 해도 같은 `Reply`가 되고 `Question.pick()`이 버튼을 찾는다. 닫기는 늘 거절. 대답을 받아 처리하는 중(경유지 넣기)이면 `working`이 켜진다. 길안내 대화와 루틴 권하기가 함께 올리고 대답이 오면 내린다. 거절한 종류는 새 길안내 전까지 다시 묻지 않는다 | |

- 창구는 `routines/.../port/`에 두고, 구현은 `app/`이 한다.
- 기능 쪽 테스트는 창구의 가짜 구현으로 돈다. 앱 쪽은 실제 SDK·API로 같은 창구를 채운다.

---

## 4. 흐름

```
입력(화면 버튼 · 말 · Gleo · 상황 감지 · adb 데모)
  → AppGraph.handle()                        [앱 연결]
  → 말 알아듣기 → 결정 규칙 → 실행 엔진/대화 흐름  [기능 구현]
  → 창구(CarPort · Speaker · 내비 · 정보)       [약속]
  → Pleos SDK · 바깥 API · TTS · 화면          [앱 연결]
```

- 차를 바꾸는 결정은 기능 쪽 결정 규칙에서만 한다. LLM을 붙여도 말 알아듣기·문장 다듬기에만 쓴다.
- 안 되는 기능은 그 단계만 음성 안내로 바뀌고 루틴은 계속 간다.

---

## 5. 빌드와 테스트

JDK 17~21이 필요하다(`JAVA_HOME`). `local.properties`에 넣을 값은 [README](../README.md)의 "테스트 환경과 필요 설정"에 있다.

| 무엇 | 명령 |
|---|---|
| 기능 테스트(차 없이 규칙·대사집 전체) | `./gradlew :routines:test` |
| 앱 단위 테스트(바깥 API 응답 흉내) | `./gradlew :app:testDebugUnitTest` |
| debug 설치(adb 데모 명령 포함) | `./gradlew :app:installDebug` |
| 제출 APK | `./gradlew :app:assembleRelease` → `apk/westone_Sunstone_release.apk` |

- `ScriptTest`가 대사집 시나리오 ① S1~S9, ② S10~S18, ③ S19~S25를 장면마다 준비 → 대사 → 앱이 한 말·질문까지 견준다.
- `TripScenarioTest`가 [SPEC.md](SPEC.md) 4장의 갈래(최근 세차, 비 예보 1·3·4·8일 뒤, 날씨 없음, 먼저 세차 + 비, 거절한 날)를 본다.
- 실제 차 값이 필요한 확인은 Pleos 에뮬레이터와 Virtual Console로 했고 결과는 [api-spike.md](api-spike.md)에 적었다.

### debug 빌드의 데모 명령

에뮬레이터가 만들지 못하는 상황을 adb로 넣는다. release 빌드에는 없다.

```
adb shell "am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd 'say 세차할게'"
```

| 명령 | 하는 일 |
|---|---|
| `say <말>` | 운전자 말 한마디를 넣는다 |
| `scene S3`, `scene restart` | 시연 장면으로 건너뛰기, 처음으로 |
| `fuel 18`, `washed 36` | 연료 %, 마지막 세차를 며칠 전으로 |
| `weather live\|dry\|rain\|rain4\|unknown` | 시연 밖 날씨를 실제 값이나 넣은 값으로 |
| `place carwash`, `speed 30`, `belt …` | 장소, 속도, 벨트 |

### 바깥 API 키

실제 검색(시연 밖)에만 쓴다. `local.properties`에 `sunstone.kakao.restKey`, `sunstone.opinet.key`를 넣고 debug 앱을 한 번 실행한 뒤
`tools/configure-api.ps1`을 돌리면 기기 안 설정 파일로 들어간다. 키는 저장소와 APK에 넣지 않는다. 날씨(Open-Meteo)는 키가 없다.

---

## 6. 용어

같은 개념은 코드·문서·화면에서 같은 이름을 쓴다.

| 용어 | 뜻 | 코드 이름 |
|---|---|---|
| 루틴 | 미리 만든 기능 묶음 하나 (세차 모드 등) | `Routine` |
| 단계 | 루틴 안의 할 일 하나. 제어 또는 안내 | `Step.Control`, `Step.Guide` |
| 제어 | 앱이 차를 직접 바꾸는 것 | `Command`, `CarPort.apply()` |
| 안내 | 앱이 할 수 없어 말로 알리는 것 | `Step.Guide`, `fallback` |
| 되돌리기 | 저장해 둔 원래 값으로 돌리는 것 | `undo` |
| 권하기 | 상황을 보고 앱이 먼저 묻는 것 | `Suggestion` |
| 창구 | 두 모듈이 만나는 인터페이스 | `*Port` |
| 대사집 | 시연·테스트용 장면별 대사 | `ScriptBook`, `Scene` |
| 시연 준비 값 | 시연 모드가 도는 동안 바깥 API 대신 쓰는 값 | `FixedInfo`(앱), `FakeInfo`(테스트) |
