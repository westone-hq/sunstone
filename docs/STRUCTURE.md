# Sunstone 구조

현대차에 이미 있지만 안 쓰이는 기능을, 상황을 알아채거나 말 한마디로 한 번에 실행하고 끝나면
원래대로 돌리는 Pleos Connect 앱이다.

> 상태: 지금 들어 있는 루틴·문장·판단 숫자는 전부 **초안(미승인)** 이다.
> 기능은 박건준 승인을 받으며 하나씩 다시 만든다(`CONTRIBUTING.md`). 승인 전에는 늘리거나 다듬지 않는다.

---

## 1. 두 영역과 담당

| 영역 | 모듈 | 담당 | 한 줄 |
|---|---|---|---|
| ① 기능 구현 | `routines/` | 박희정 | 무엇을, 언제 할지 정한다. 순수 Kotlin, 차 없이 테스트한다 |
| ② 앱 연결 | `app/` | 박건준 | 어떻게 할지 맡는다. Pleos SDK·내비·바깥 API·음성·화면 |
| 시연·결정 | — | 박건준 | 시연 빌드, 시연 영상, 루틴·문장·숫자 최종 결정 |

두 영역이 만나는 곳은 **창구(포트)** 몇 개뿐이다(3장). 창구를 바꿀 때만 두 사람이 합의한다.

---

## 2. 폴더

```
sunstone/
├─ README.md
├─ CONTRIBUTING.md         담당, 기능 승인, 브랜치·PR
├─ CLAUDE.md               Claude Code 작업 안내 (두 사람 공통)
├─ .claude/settings.json   Claude Code 공통 설정
├─ docs/
│  ├─ STRUCTURE.md         이 문서
│  ├─ COMMENT_RULES.md     주석·코드·커밋 규칙 (작업 전마다 읽는다)
│  └─ api-spike.md         Pleos API 실측 기록
├─ apk/                    (예정) 심사용 release APK
├─ tools/api-explorer/     Pleos 공식 Vehicle 샘플 (실측용, 출품 제외)
│
├─ routines/               ① 기능 구현 ─ 박희정
│  └─ src/main/kotlin/com/westone/sunstone/routines/
│     ├─ model/            차 상태와 기본 값(기어·창문·문·좌석·장소)
│     ├─ port/             창구: CarPort, Speaker, SeatProfiles
│     ├─ engine/           루틴 정의 틀, 실행 엔진(저장→실행→확인→되돌리기→권하기)
│     ├─ library/          루틴들 (초안: 세차·차일드락·시트 메모리)
│     ├─ understand/       말 알아듣기 (말 → 할 일)
│     └─ fake/             가짜 차 (테스트·시연)
│     (예정) dialog/       대화 흐름: 길안내 → 주유 제안 → 세차 추천 → 도착 시 권하기
│     (예정) knowledge/    AI 참고 문서 읽기·찾기
│     (예정) script/       대사집 실행기
│
└─ app/                    ② 앱 연결 ─ 박건준
   └─ src/
      ├─ main/java/com/westone/sunstone/
      │  ├─ car/           PleosCarPort(차량 SDK·제어), Callbacks(SDK 콜백 기다리기),
      │  │                 AaosSpeedSource(속도), AaosEnergySource(주행 가능 거리·배터리·연료 종류),
      │  │                 FuelState(연료·배터리·차 종류 값 묶음)
      │  ├─ connect/       GleoEntry, AndroidSpeaker, PrefsSeatProfiles
      │  ├─ ui/            MainActivity, SunstoneScreen
      │  └─ SunstoneApp.kt AppGraph: 두 영역 조립, 모든 입력의 입구
      │  (예정) navi/      내비 SDK(경로·경유지·도착)
      │  (예정) info/      바깥 API(카카오·오피넷·기상청·에어코리아) + 고정 응답
      ├─ main/res/values/  strings.xml (앱 문구. 루틴 문장은 routines/)
      ├─ main/assets/      schema.json(Gleo), (예정) 참고 문서 카드·대사집·녹음본
      ├─ debug/            출시 빌드에는 없음
      │  ├─ DemoReceiver   adb 장면 주입·실측 명령 입구
      │  ├─ VirtualConsole 앱 안에서 콘솔 HTTP API 읽기·쓰기
      │  ├─ CarProbe       probe: 제어 대상별 바꾸기·되돌리기 실측
      │  ├─ CarAreas       areas: 차가 받는 area 목록
      │  ├─ CarSeatRead    seatread: AAOS 속성으로 시트 위치 읽기 시도
      │  ├─ SeatTimer      seattime: 시트 축 0→100 이동 시간
      │  ├─ SeatFit        seatfit: 시간 기반 시트 이동 오차
      │  ├─ SeatReadCheck  seatcheck: 시트 위치 지원 목록·리스너 재확인
      │  ├─ NaviProbe      navi: 내비 경로·경유지·남은 거리·통행료·도착
      │  ├─ LocationProbe  location: FusedLocation 위치·근접 알림
      │  └─ BeltProbe      belt: 좌석별 벨트·미착용 경고·벨트 경고등
      (예정) sim/, pleos/  빌드 2종: 시연 빌드(가짜 차·고정 응답) / Pleos 빌드(실제 SDK)
```

예정 폴더는 그 기능이 승인될 때 만든다. 빈 폴더를 미리 두지 않는다.

---

## 3. 창구 (두 영역의 약속)

| 창구 | 방향 | 지금 | 예정 |
|---|---|---|---|
| `CarPort` | 기능 → 차 | 상태 읽기, 값 읽기, 바꾸기 | 연료·주행 가능 거리 |
| `Speaker` | 기능 → 사람 | 말하기 | |
| `SeatProfiles` | 기능 → 저장소 | 자세 저장·조회 | |
| 내비 창구 | 기능 → 내비 | | 목적지 찾기, 경로, 경유지, 남은 거리·통행료, 도착 알림 |
| 정보 창구 | 기능 → 바깥 | | 주변 주유소(가격·세차), 날씨(7일 비), 미세먼지 |
| 기억 창구 | 기능 → 저장소 | | 방문 기록, 마지막 세차일 |

- 창구는 `routines/.../port/`에 두고, 구현은 `app/`이 한다.
- 창구 함수를 더하거나 바꾸려면 GitHub 이슈로 제안 → 상대 확인 → 박건준 결정.
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

| 무엇 | 명령 | 누가 |
|---|---|---|
| 기능 테스트 | `.\gradlew :routines:test` | 박희정 |
| 앱 설치 | `.\gradlew :app:installDebug` | 박건준 |
| 대사집 자동 테스트 | (예정) `:routines:test` 안에서 장면 S0~ 전부 | 박희정 |
| 실제 경로 확인 | 에뮬레이터 + Virtual Console | 박건준 |

(예정) 빌드 2종: **시연 빌드**(가짜 차·고정 응답, 아무 에뮬레이터·폰에서 동작) / **Pleos 빌드**(실제 SDK).

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
| 창구 | 두 영역이 만나는 인터페이스 | `*Port` |
| 대사집 | 시연·테스트용 장면별 대사 | (예정) `script` |
| 고정 응답 | 키·네트워크 없을 때 바깥 API 대신 쓰는 값 | (예정) |
