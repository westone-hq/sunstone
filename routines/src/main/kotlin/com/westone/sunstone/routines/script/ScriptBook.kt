package com.westone.sunstone.routines.script

import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.VoiceAction

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-12 18:32:00 · 박건준
 * 클래스: ScriptBook
 * 목적: 시연 대사집. 장면 목록과, 말을 대사집에서 먼저 찾는 일을 맡는다.
 *       - `scenarioOne` — 시나리오 ① 출장 가는 길(S1~S10)
 *       - `lookup()` — 대표 문장·변형과 띄어쓰기·문장부호를 빼고 똑같으면 그
 *         장면의 요청을 돌려준다
 * 근거: 인용 — docs/SCREEN.md 3장 ①-2~①-11 표. 기대 말은 표 문장 그대로.
 *       한 장면에 질문 하나, 패널 하나가 되게 6장면을 10장면으로 나눴다(①-1
 *       말 걸기는 화면만이라 뺐다)
 */
object ScriptBook {

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-12 18:32:00 · 박건준
     * 변수: scenarioOne
     * 목적: 시나리오 ① 출장 가는 길: 길안내 → 주유 → 세차. S1~S10이
     *       docs/SCREEN.md 3장 ①-2~①-11에 하나씩 맞는다.
     *       준비: P단, 연료 18%(조작), 마지막 세차 36일 전(기억, 조작), 서울
     * 근거: 인용 — docs/SCREEN.md 3장 ①-2~①-11
     * 미구현: 프로토타입이라 S8의 주유소 안 두 번째 정차(세차 입구)는 장면
     *       준비로 넣는다(`WashBooking` 참고)
     */
    val scenarioOne: List<Scene> = listOf(
        Scene(
            id = "S1",
            title = "길안내 시작",
            prep = listOf(
                Prep(
                    gear = Gear.PARK, speedKmh = 0f, place = Place.NONE,
                    fuelPercent = 18, washedDaysAgo = 36, resetTrip = true,
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
            title = "주유 제안",
            expectSaid = listOf("연료가 18%예요. 출발 전에 주유하고 가실래요?"),
            expectQuestion = QuestionKind.FUEL_STOP,
        ),
        Scene(
            id = "S3",
            title = "주유소 경유",
            driver = Utterance(
                text = "그래",
                variants = listOf("응 넣어 줘", "좋아", "네 부탁해"),
                intent = VoiceAction.Answer(yes = true),
            ),
            expectSaid = listOf("말죽거리주유소를 경유지로 넣었어요."),
            expectWaypoints = listOf("말죽거리주유소"),
            expectNotices = listOf("경유지에 넣었어요"),
        ),
        Scene(
            id = "S4",
            title = "세차 추천",
            expectSaid = listOf("세차 안 하신 지 36일 됐어요. 일주일간 비 소식이 없는데, 거기서 세차도 하실래요?"),
            expectQuestion = QuestionKind.WASH_RECOMMEND,
        ),
        Scene(
            id = "S5",
            title = "세차 예약",
            driver = Utterance(
                text = "그래 그러자",
                variants = listOf("응 그러자", "좋아 그렇게 해", "그래 그렇게 해"),
                intent = VoiceAction.Answer(yes = true),
            ),
            expectSaid = listOf("네, 세차 입구에 서시면 세차 모드 맞춰 드릴게요."),
            expectNotices = listOf("세차 예약했어요"),
        ),
        Scene(
            id = "S6",
            title = "주행",
            prep = listOf(Prep(gear = Gear.DRIVE, speedKmh = 60f)),
            expectSaid = emptyList(),
        ),
        Scene(
            id = "S7",
            title = "주유소 도착",
            prep = listOf(
                Prep(place = Place.GAS_STATION, speedKmh = 0f),
                Prep(gear = Gear.PARK),
            ),
            expectSaid = emptyList(),
            expectNotices = listOf("세차 입구에서 맞춰 드릴게요"),
        ),
        Scene(
            id = "S8",
            title = "세차 입구",
            prep = listOf(
                Prep(gear = Gear.DRIVE, speedKmh = 5f),
                Prep(speedKmh = 0f, gear = Gear.PARK),
            ),
            expectSaid = listOf("세차 모드 켤까요?"),
            expectQuestion = QuestionKind.WASH_MODE,
        ),
        Scene(
            id = "S9",
            title = "세차 모드",
            driver = Utterance(
                text = "응, 켜 줘",
                variants = listOf("응", "네 켜 줘", "그래 맞춰 줘"),
                intent = VoiceAction.Answer(yes = true),
            ),
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
            id = "S10",
            title = "세차 후 출발",
            prep = listOf(Prep(gear = Gear.DRIVE)),
            expectSaid = listOf("세차 모드 끝냈어요. 사이드미러 펴졌는지 확인해 주세요."),
            expectNotices = listOf("원래대로 돌려놨어요"),
            expectCar = mapOf(
                Target.Recirculation to false,
                Target.Wiper to WiperMode.AUTO,
                Target.WindowPosition(Window.FRONT_LEFT) to 0,
                Target.DoorLock to true,
            ),
        ),
    )

    /** 모든 장면의 말(대표 문장·변형)을 띄어쓰기·문장부호를 뺀 글자로 찾는 표 */
    private val byText: Map<String, VoiceAction> = scenarioOne.mapNotNull { it.driver }
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
