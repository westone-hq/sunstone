package com.westone.sunstone.routines.script

import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.VoiceAction

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: ScriptBook
 * 목적: 시연 대사집. 장면 목록과, 말을 대사집에서 먼저 찾는 일을 맡는다.
 *       - `scenarioOne` — 시나리오 ① 출장 가는 길(S1~S9)
 *       - `scenarioTwo` — 시나리오 ② 아이 등원길(S10~S18)
 *       - `lookup()` — 대표 문장·변형과 띄어쓰기·문장부호를 빼고 똑같으면 그
 *         장면의 요청을 돌려준다
 * 근거: 인용 — docs/SCREEN.md 6장 시나리오 ① 다시 짠 흐름 9단계, 4장 ② 표.
 *       기대 말은 그 문장 그대로
 */
object ScriptBook {

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 12:30:00 · 박희정
     * 변수: scenarioOne
     * 목적: 시나리오 ① 출장 가는 길: 길안내 → 한 번의 질문(주유·세차) → 주유 →
     *       세차. S1~S9가 docs/SCREEN.md 6장 9단계에 하나씩 맞는다.
     *       준비: P단, 연료 18%(조작), 마지막 세차 36일 전(기억, 조작), 서울,
     *       뒷자리 빔(시나리오 ② 뒤에 해도 차일드락이 끼어들지 않게)
     * 근거: 인용 — docs/SCREEN.md 6장 시나리오 ① 다시 짠 흐름
     * 미구현: 프로토타입이라 S6의 주유소 안 두 번째 정차(세차 입구)는 장면
     *       준비로 넣는다(`WashBooking` 참고)
     */
    val scenarioOne: List<Scene> = listOf(
        Scene(
            id = "S1",
            title = "출발 전",
            prep = listOf(
                Prep(
                    gear = Gear.PARK, speedKmh = 0f, place = Place.NONE,
                    fuelPercent = 18, washedDaysAgo = 36, resetTrip = true,
                    rearDoorOpen = false, rearOccupied = false, rearBelt = false,
                ),
            ),
            driver = Utterance(
                text = "파라다이스 호텔 부산까지 길안내 해 줘",
                variants = listOf("파라다이스 호텔 부산으로 안내해 줘", "파라다이스 호텔 가자", "파라다이스 호텔 부산까지 가 줘"),
                intent = VoiceAction.Navigate("파라다이스 호텔 부산"),
            ),
            expectSaid = listOf("파라다이스 호텔 부산으로 안내할게요."),
            expectDestination = "파라다이스 호텔 부산",
        ),
        Scene(
            id = "S2",
            title = "한 번의 질문",
            expectSaid = listOf(
                "연료가 18%예요. 가는 길에 말죽거리주유소가 있어요. " +
                    "세차 안 하신 지 36일 됐고 일주일간 비 소식이 없어서 세차도 추천드려요.",
            ),
            expectQuestion = QuestionKind.FUEL_STOP,
        ),
        Scene(
            id = "S3",
            title = "대답",
            driver = Utterance(
                text = "응, 세차도 할게",
                variants = listOf("세차도 해 줘", "응 세차까지 할게", "그래 둘 다 해 줘"),
                intent = VoiceAction.Answer(Reply.ALL),
            ),
            expectSaid = emptyList(),
            expectWaypoints = listOf("말죽거리주유소"),
            expectNotices = listOf("경유지에 넣고 세차를 예약했어요"),
        ),
        Scene(
            id = "S4",
            title = "주행",
            prep = listOf(Prep(gear = Gear.DRIVE, speedKmh = 60f)),
            expectSaid = emptyList(),
        ),
        Scene(
            id = "S5",
            title = "주유소 첫 정차",
            prep = listOf(
                Prep(place = Place.GAS_STATION, speedKmh = 0f),
                Prep(gear = Gear.PARK),
            ),
            expectSaid = emptyList(),
            expectNotices = listOf("세차 입구에서 맞춰 드릴게요"),
        ),
        Scene(
            id = "S6",
            title = "세차 입구 정차",
            prep = listOf(
                Prep(gear = Gear.DRIVE, speedKmh = 5f),
                Prep(speedKmh = 0f, gear = Gear.PARK),
            ),
            expectSaid = listOf("곧 세차 모드 켤게요."),
            expectQuestion = QuestionKind.WASH_MODE,
        ),
        Scene(
            id = "S7",
            title = "맞추는 중",
            afterMs = 5_000,
            expectSaid = listOf("세차 모드 맞췄어요. 사이드미러는 직접 접어 주세요."),
            expectCar = mapOf(
                Target.WindowPosition(Window.FRONT_LEFT) to 0,
                Target.WindowPosition(Window.REAR_RIGHT) to 0,
                Target.Wiper to WiperMode.OFF,
                Target.Recirculation to true,
                Target.DoorLock to true,
            ),
        ),
        Scene(
            id = "S8",
            title = "세차하는 동안",
            afterMs = 3_000,
            expectSaid = emptyList(),
        ),
        Scene(
            id = "S9",
            title = "출발",
            prep = listOf(Prep(gear = Gear.DRIVE)),
            expectSaid = listOf("사이드미러 펴졌는지 확인해 주세요."),
            expectNotices = listOf("세차 모드를 끝내고 원래대로 돌려놨어요"),
            expectCar = mapOf(
                Target.Recirculation to false,
                Target.Wiper to WiperMode.AUTO,
                Target.WindowPosition(Window.FRONT_LEFT) to 0,
                Target.DoorLock to true,
            ),
        ),
    )

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 14:00:00 · 박희정
     * 변수: scenarioTwo
     * 목적: 시나리오 ② 아이 등원길: 차일드락. 아이를 태우고 유치원 길안내 →
     *       아이가 탔는지 물으며 잠금 → 유치원 도착해 풀기 → 하원길 "아침처럼"
     *       → 집에 도착해 풀기. docs/SCREEN.md 4장 ②-1~②-10의 순서를 2026-11-13
     *       박건준 요청대로 바꿨다(탑승이 아니라 유치원 길안내 때 묻는다).
     *       준비: P단, 집, 뒷자리에 아이가 이미 앉아 있음, 연료 넉넉(80%, 주유를
     *       묻지 않게), 마지막 차일드락은 어제(같은 날 다시 해도 처음처럼)
     * 근거: 인용 — docs/SCREEN.md 4장 ② 표, 2026-11-13 박건준 요청 "유치원
     *       간다고 하면 아이가 타고 있는지 물어보면서 차일드락". 맞춘 뒤 문
     *       잠금·공조도 하는 것은 2026-11-13 박희정이 더 넣기로 정했다(미정)
     * 미구현: 프로토타입이라 뒷자리 탑승·문은 콘솔로, 벨트는 데모 입력으로
     *       넣는다(`ScenePlayer.prepare()`)
     */
    val scenarioTwo: List<Scene> = listOf(
        Scene(
            id = "S10",
            title = "유치원 길안내",
            prep = listOf(
                Prep(
                    gear = Gear.PARK, speedKmh = 0f, place = Place.HOME, fuelPercent = 80, resetTrip = true,
                    rearDoorOpen = false, rearOccupied = true, rearBelt = true, childLockDaysAgo = 1,
                ),
            ),
            driver = Utterance(
                text = "OO유치원 가자",
                variants = listOf("유치원 가자", "OO유치원으로 안내해 줘", "OO유치원까지 길안내 해 줘"),
                intent = VoiceAction.Navigate("OO유치원"),
            ),
            expectSaid = listOf("OO유치원으로 안내할게요.", "아이 타고 있나요? 차일드락 맞춰 드릴까요?"),
            expectDestination = "OO유치원",
            expectQuestion = QuestionKind.CHILD_LOCK,
        ),
        Scene(
            id = "S11",
            title = "차일드락",
            driver = Utterance(
                text = "응, 잠가 줘",
                variants = listOf("잠가 줘", "응 잠가줘", "그래 잠가 줘"),
                intent = VoiceAction.Answer(Reply.YES),
            ),
            expectSaid = listOf("뒷창문 잠갔어요. 뒷문 차일드락은 문 안쪽 스위치로 걸어 주세요."),
            expectCar = mapOf(
                Target.WindowLock(Window.REAR_LEFT) to true,
                Target.WindowLock(Window.REAR_RIGHT) to true,
                Target.DoorLock to true,
                Target.Climate(Seat.REAR_LEFT) to true,
            ),
        ),
        Scene(
            id = "S12",
            title = "주행",
            prep = listOf(Prep(gear = Gear.DRIVE, speedKmh = 40f, place = Place.NONE)),
            expectSaid = emptyList(),
        ),
        Scene(
            id = "S13",
            title = "벨트 풀림",
            prep = listOf(Prep(rearBelt = false)),
            expectSaid = listOf("뒷자리 안전벨트가 풀렸어요."),
            expectNotices = listOf("뒷자리 안전벨트가 풀렸어요."),
        ),
        Scene(
            id = "S14",
            title = "유치원 도착",
            prep = listOf(
                Prep(place = Place.KIDS, speedKmh = 0f),
                Prep(gear = Gear.PARK),
            ),
            expectSaid = listOf("도착했어요. 뒷창문 잠금 풀었어요."),
            expectNotices = listOf("뒷창문 잠금을 풀었어요"),
            expectCar = mapOf(
                Target.WindowLock(Window.REAR_LEFT) to false,
                Target.WindowLock(Window.REAR_RIGHT) to false,
                Target.Climate(Seat.REAR_LEFT) to false,
                Target.DoorLock to true,
            ),
        ),
        Scene(
            id = "S15",
            title = "뒷자리 비움",
            prep = listOf(
                Prep(rearDoorOpen = true),
                Prep(rearOccupied = false, rearBelt = false),
                Prep(rearDoorOpen = false),
            ),
            expectSaid = emptyList(),
        ),
        Scene(
            id = "S16",
            title = "하원길",
            prep = listOf(
                Prep(rearDoorOpen = true),
                Prep(rearOccupied = true, rearBelt = true),
                Prep(rearDoorOpen = false),
            ),
            driver = Utterance(
                text = "집에 가자",
                variants = listOf("집으로 가자", "집으로 안내해 줘", "집까지 길안내 해 줘"),
                intent = VoiceAction.Navigate("집"),
            ),
            expectSaid = listOf("집으로 안내할게요.", "아침처럼 뒷창문 잠가 드릴까요?"),
            expectDestination = "집",
            expectQuestion = QuestionKind.CHILD_LOCK_AGAIN,
        ),
        Scene(
            id = "S17",
            title = "다시 잠금",
            driver = Utterance(
                text = "응, 잠가 줘",
                variants = listOf("잠가 줘", "응 잠가줘", "그래 잠가 줘"),
                intent = VoiceAction.Answer(Reply.YES),
            ),
            expectSaid = listOf("뒷창문 잠갔어요."),
            expectCar = mapOf(
                Target.WindowLock(Window.REAR_LEFT) to true,
                Target.WindowLock(Window.REAR_RIGHT) to true,
            ),
        ),
        Scene(
            id = "S18",
            title = "집 도착",
            prep = listOf(
                Prep(gear = Gear.DRIVE, speedKmh = 40f, place = Place.NONE),
                Prep(place = Place.HOME, speedKmh = 0f),
                Prep(gear = Gear.PARK),
            ),
            expectSaid = listOf("도착했어요. 뒷창문 잠금 풀었어요."),
            expectNotices = listOf("뒷창문 잠금을 풀었어요"),
            expectCar = mapOf(
                Target.WindowLock(Window.REAR_LEFT) to false,
                Target.WindowLock(Window.REAR_RIGHT) to false,
            ),
        ),
    )

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: all
     * 목적: 모든 시나리오 장면, 시연 순서대로. 시연 모드와 adb `scene`이 쓴다.
     */
    val all: List<Scene> = scenarioOne + scenarioTwo

    /** 모든 장면의 말(대표 문장·변형)을 띄어쓰기·문장부호를 뺀 글자로 찾는 표 */
    private val byText: Map<String, VoiceAction> = all.mapNotNull { it.driver }
        .flatMap { u -> (listOf(u.text) + u.variants).map { compact(it) to u.intent } }
        .toMap()

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: lookup
     * 목적: 말이 대사집 문장(대표·변형)과 같으면 그 장면의 요청을 돌려준다.
     * 인자: text — 운전자가 한 말
     * 반환: 대사집 요청. 대사집에 없으면 null(규칙으로 넘어간다)
     */
    fun lookup(text: String): VoiceAction? = byText[compact(text)]

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: compact
     * 목적: 비교하려고 띄어쓰기와 문장부호를 뺀다.
     */
    private fun compact(text: String) = text.replace(Regex("""[\s.,!?~]"""), "")
}
