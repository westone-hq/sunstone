# Sunstone 구조 계획 (v0.1, 2026-11-08)

현대차에 이미 있지만 안 쓰이는 기능을, 상황을 알아채거나 말 한마디로 한 번에 실행하고 끝나면 되돌리는 Pleos Connect 앱.
1차 루틴은 세 개다: **세차 모드**, **차일드락(아이 탑승)**, **시트 메모리(운전자 교대)**.

---

## 1. 전제 (확인된 것)

| 항목 | 상태 | 영향 |
|---|---|---|
| Vehicle State, NaviHelper | 사용 가능 | 감지(기어·문·좌석·창문·위치)는 바로 된다 |
| Vehicle Control, TTS, STT | Playground "준비 중", 신청 불가 | 제어는 권한 선언(normal)만으로 에뮬레이터 시연. 음성은 미리 만든 파일 + 화면/데모 입력 |
| Gleo | 공모전 SDK 목록에 없음 | schema.json은 넣어 두고, 같은 진입점을 화면 버튼·데모 브로드캐스트로도 부른다 |
| 현대 모드(세차·중립 주차 등) 호출 | API 없음 | 모드가 하는 동작을 앱이 직접 묶어서 실행한다 |
| 미러 접기, 기어·EPB, 차일드락 전용 API | 없음 | 해당 단계는 "음성 안내 + 확인"으로 처리 |
| 시트 | `selectSeatMemory(area, n)`(차량 메모리 슬롯 호출), `moveSeat*`(방향 FORWARD/BACKWARD/STOP), 각 축 위치 읽기 | 차량 슬롯이 있으면 슬롯 호출, 없으면 앱이 위치를 저장하고 목표값까지 이동 후 STOP |
| 에뮬레이터 실측(다른 팀) | 창문 위치 반영 O, 창문 잠금은 읽기값만 바뀜, 창문 0으로 되돌리기 실패, 시트 열선 수십 초 지연 | **첫 작업은 우리가 직접 API 실측**(4장 D0) |

---

## 2. 전체 구조

```
 입력                    두뇌                         실행                    차량
┌───────────────┐   ┌──────────────────┐   ┌────────────────────┐   ┌──────────────┐
│ 상황 감지        │──▶│ RoutineRunner     │──▶│ VehicleGateway      │──▶│ Pleos Vehicle │
│ (ContextDetector)│   │  상태 머신          │   │  capture→set→verify │   │ SDK / AAOS    │
│ 말 한마디         │──▶│  Snapshot(되돌리기) │   │  실패 시 안내로 전환    │   └──────────────┘
│ (화면·Gleo·데모)  │   │  SafetyGuard      │   │ VoiceOut / UI       │
└───────────────┘   └──────────────────┘   └────────────────────┘
        ▲                                              │
        └──────────── CarSignals (기어·속도·문·좌석·창문·위치) ◀┘
```

- **루틴은 데이터**다. 각 루틴 = 부르는 말 + 감지 조건 + 단계 목록 + 되돌리기 규칙. 엔진은 하나.
- **모든 제어는 "저장 → 실행 → 읽어서 확인"**. 확인이 안 되면 그 단계를 "음성 안내"로 바꾼다. 차량·에뮬레이터마다 되는 기능이 달라도 루틴이 멈추지 않는다.
- **VehicleGateway는 인터페이스**. 실제(Pleos) 구현과 가짜(Fake) 구현을 둔다. Fake로 화면·로직을 먼저 만들고, 시연 때 실제로 바꾼다.

---

## 3. 루틴 3개 설계

### 3.1 세차 모드 `WASH`

| 구분 | 내용 |
|---|---|
| 말 | "세차할게", "세차 모드" / 끝: "세차 끝났어" |
| 감지 | 세차장·주유소 반경 100m(카카오 로컬 키워드 검색) + 정차 + 기어 N 또는 P → "세차하시나요? 준비해드릴게요" |
| 조건 | 정차(속도 0), 기어 P 또는 N |
| 앱이 실행 | ① 창문 4개 + 선루프(ROOF_TOP_1·2) 닫기 ② 와이퍼 AUTO→OFF ③ 내기 순환 ON ④ 문 잠금 |
| 안내만 | 미러 접기 버튼, 자동 세차기면 N 유지(스티어링 버튼 길게, 3분 안에 운전석 문 열지 않기) |
| 진행 중 | 세차 타이머, 와이퍼가 다시 켜지면 알림 |
| 되돌리기 | 세차장을 벗어나 D단 + 속도 10km/h 이상, 또는 "세차 끝" → 와이퍼·내외기를 **저장해 둔 값으로** 복구. 창문은 다시 열지 않음. 미러 펴기·브레이크 클리닝 한 줄 안내 |

### 3.2 차일드락(아이 탑승) `KIDS`

| 구분 | 내용 |
|---|---|
| 말 | "애 태웠어", "차일드락" / 끝: "애 내렸어" |
| 감지 | 뒷문 열림→닫힘 + 뒷좌석 점유 + (출발지·목적지가 어린이집·학교·학원 또는 평소 등하원 시간) |
| 앱이 실행 | ① 뒷창 잠금(setWindowLock ROW_2_*) ② 문 잠금 ③ 뒷유리 블라인드 올림 ④ 뒷좌석 공조 켜기 |
| 안내만 | 뒷문 차일드락(전용 API 없음): "뒷문 차일드락 켜져 있는지 확인해 주세요" |
| 진행 중 | 주행 중 뒷좌석 벨트가 풀리면 음성 알림(벨트·점유는 읽기 가능) |
| 되돌리기 | 정차 + 뒷문 열림 → 뒷좌석 비어짐 → 블라인드 내림·뒷좌석 공조 끔·뒷창 잠금 해제 여부 묻기 |

### 3.3 시트 메모리(운전자 교대) `SEAT_SWAP`

| 구분 | 내용 |
|---|---|
| 말 | "운전 바꿀게", "○○ 자세로" / 저장: "지금 자세 ○○로 저장해줘" / 끝: "원래대로" |
| 감지 | P단 + 운전석 문 열림→닫힘 + 운전석 점유가 비었다가 다시 참(휴게소 교대) → "운전자 바뀌었나요? 누구 자세로 맞출까요?" |
| 조건 | **P단에서만**. 기어가 바뀌면 즉시 모든 이동 STOP |
| 앱이 실행 | 프로필에 차량 슬롯 번호가 있으면 `selectSeatMemory`. 없으면 저장된 각 축 위치까지 `moveSeat*` 이동 → 목표 도달 시 STOP(허용 오차·축별 타임아웃) |
| 저장 | 읽을 수 있는 모든 축 위치(앞뒤·높이·등받이·쿠션 각도·깊이·헤드레스트 등) + 시트 열선·통풍 + 핸들 열선을 프로필로 저장 |
| 되돌리기 | 실행 직전 자세를 스냅샷으로 저장 → "원래대로" |

---

## 4. 일정

| 날짜 | 할 일 | 끝난 기준 |
|---|---|---|
| **D0** 11/8~9 | 에뮬레이터 설치, Pleos 샘플 API 탐색기로 필요한 API 전부 실측 | `docs/api-spike.md`에 API별 O/X/지연 기록 |
| D1 11/10 | 프로젝트 골격, VehicleGateway(Fake/Pleos), CarSignals, RoutineRunner | Fake로 세차 루틴 실행·되돌리기 단위 테스트 통과 |
| D2 11/11 | 세차 모드 실차 연동 + 화면 | 에뮬레이터에서 창문·와이퍼·내기 실제 변경 |
| D3 11/12 | 차일드락, 시트 메모리 | 세 루틴 모두 실행·되돌리기 |
| D4 11/13 | 상황 감지(카카오 장소, 문·좌석 이벤트), 데모 브로드캐스트, 음성 파일 | adb 한 줄로 각 장면 재현 |
| D5 11/14 | 화면 정리, 시연 영상, 기획서(접수양식) | 영상 1편 + 양식 초안 |
| 11/15 | 제출 | |

D0 결과에 따라 루틴 단계가 "앱 실행"에서 "안내"로 옮겨질 수 있다. 구조는 그걸 전제로 만든다.

---

## 5. 폴더 구조

```
sunstone/
├─ docs/
│  ├─ PLAN.md                 ← 이 문서
│  └─ api-spike.md            ← D0 실측 결과
├─ tools/api-explorer/        ← Pleos 샘플 Vehicle 앱(실측용, 출품 X)
└─ app/src/
   ├─ main/java/com/westone/sunstone/
   │  ├─ car/        VehicleGateway(인터페이스), PleosVehicleGateway, FakeVehicleGateway,
   │  │              CarSignals(Flow: 기어·속도·문·좌석·벨트·창문·와이퍼), GearSpeedSource(AAOS)
   │  ├─ routine/    Routine, Step(Control/Guide/Wait), RoutineRunner(상태 머신),
   │  │              Snapshot, SafetyGuard
   │  │  └─ library/ WashRoutine, KidsRoutine, SeatSwapRoutine
   │  ├─ seat/       SeatProfileStore(DataStore), SeatMover(목표 위치까지 이동→STOP)
   │  ├─ detect/     ContextDetector(루틴별 감지 규칙), PlaceLookup(카카오 로컬), NaviSource
   │  ├─ voice/      PhraseMatcher(말→루틴), VoiceOut(음성 파일 재생), GleoReceiver
   │  └─ ui/         홈(루틴 카드 3개), 실행 화면(체크리스트·타이머), 시트 프로필 설정
   ├─ main/assets/   schema.json(Gleo), voice/*.mp3
   ├─ debug/java/…/demo/  DemoReceiver(adb로 장면 주입: 세차장 도착, 기어 N, 뒷문 열림, 말 입력)
   └─ test/          RoutineRunner·SeatMover·PhraseMatcher 단위 테스트
```

### 핵심 타입 초안

```kotlin
data class Routine(
    val id: RoutineId,                 // WASH, KIDS, SEAT_SWAP
    val phrases: List<String>,         // "세차할게" …
    val detect: DetectRule?,           // 상황 감지 조건
    val precondition: (CarState) -> Boolean,
    val steps: List<Step>,
    val undo: UndoRule,                // 언제, 무엇을 되돌리나
)

sealed interface Step {
    data class Control(val op: CarOp, val verify: Boolean = true, val fallbackGuide: VoiceLine) : Step
    data class Guide(val line: VoiceLine, val confirm: Confirm? = null) : Step   // 미러 접기 등
    data class Wait(val until: (CarState) -> Boolean, val timeoutMs: Long) : Step
}

// RoutineRunner 상태: Idle → Suggested → Running(stepIndex) → Active → Undoing → Idle
```

### Gleo 스키마 (함수 3개)

| 함수 | 파라미터 | 하는 일 |
|---|---|---|
| `RUN_ROUTINE` | routine: WASH / KIDS / SEAT_SWAP, profile?(string) | 루틴 실행 |
| `UNDO_ROUTINE` | routine | 되돌리기 |
| `SAVE_SEAT_PROFILE` | name(string) | 지금 자세 저장 |

화면 버튼, 데모 브로드캐스트, Gleo 인텐트가 모두 같은 `RoutineRunner.request(id, params)`로 들어온다.

---

## 6. 안전 규칙

- 제어는 **정차 중에만**. 시트 이동은 **P단에서만**, 기어가 바뀌면 즉시 STOP.
- 주행 중 화면은 상태 한 줄만 보이고 나머지는 음성으로 한다(Pleos 주행 제한 정책).
- 창문은 "닫기"만 자동으로 한다. 다시 여는 건 묻고 한다.
- 실행 전 값은 항상 저장해서, 되돌리기는 "기본값"이 아니라 "원래 값"으로 돌린다.
