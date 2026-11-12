package com.westone.sunstone.routines.script

import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.VoiceAction

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-12 11:06:21 · 박희정
 * 클래스: ScriptBook
 * 목적: 시연 대사집. 장면 목록과, 말을 대사집에서 먼저 찾는 일을 맡는다.
 *       - `scenarioOne` — 시나리오 ① 출장 가는 길(S1~S5)
 *       - `lookup()` — 대표 문장·변형과 띄어쓰기·문장부호를 빼고 똑같으면 그
 *         장면의 요청을 돌려준다
 * 근거: 인용 — Tab 1 4.3 시나리오 ① 표와 준비 줄. 기대 말은 대사집 문장 그대로
 *       (#3은 2026-11-11 박희정이 "세차하시나요?"로 고쳤다. 목적지 "부산 OO리조트"와
 *       주유소 "OO주유소"는 2026-11-12 실제 장소 "파라다이스 호텔 부산"과
 *       "말죽거리주유소"로 바꿨다)
 */
object ScriptBook {

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-12 11:06:21 · 박희정
     * 변수: scenarioOne
     * 목적: 시나리오 ① 출장 가는 길: 길안내 → 주유 → 세차.
     *       준비: P단, 연료 18%(조작), 마지막 세차 36일 전(기억, 조작), 서울
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
                text = "파라다이스 호텔 부산까지 길안내 해줘",
                variants = listOf("파라다이스 호텔 부산으로 안내해 줘", "파라다이스 호텔 가자", "파라다이스 호텔 부산까지 가 줘"),
                intent = VoiceAction.Navigate("파라다이스 호텔 부산"),
            ),
            expectSaid = listOf(
                "네, 길안내 시작할게요. 연료가 부족해서 고속도로 들어가기 전에 주유하시는 게 좋겠어요. " +
                    "근처 주유소 찾아 드릴까요?",
            ),
            expectDestination = "파라다이스 호텔 부산",
        ),
        Scene(
            id = "S2",
            title = "주유소 경유",
            driver = Utterance(
                text = "그래",
                variants = listOf("응 찾아줘", "좋아", "네 부탁해"),
                intent = VoiceAction.Answer(yes = true),
            ),
            expectSaid = listOf(
                "가격이 괜찮고 세차도 되는 말죽거리주유소를 경유지로 넣었어요. 세차 안 하신 지 36일 되셨습니다. " +
                    "일주일간 비 소식이 없어 세차 추천드려요.",
            ),
            expectWaypoints = listOf("말죽거리주유소"),
        ),
        Scene(
            id = "S3",
            title = "주유소 도착",
            prep = listOf(
                Prep(gear = Gear.DRIVE, speedKmh = 60f),
                Prep(place = Place.GAS_STATION, speedKmh = 0f),
                Prep(gear = Gear.PARK),
            ),
            expectSaid = listOf("세차하시나요? 세차하실 거면 세차 모드로 맞춰 드릴까요?"),
        ),
        Scene(
            id = "S4",
            title = "세차 모드",
            driver = Utterance(
                text = "응",
                variants = listOf("네", "응 해줘", "그래 맞춰줘"),
                intent = VoiceAction.Answer(yes = true),
            ),
            expectSaid = listOf(
                "창문 닫고, 와이퍼 끄고, 내기 순환으로 바꾸고, 문 잠갔어요. 사이드미러는 직접 접어 주세요.",
            ),
            expectCar = mapOf(
                Target.WindowPosition(Window.FRONT_LEFT) to 0,
                Target.WindowPosition(Window.REAR_RIGHT) to 0,
                Target.Wiper to WiperMode.OFF,
                Target.Recirculation to true,
                Target.DoorLock to true,
            ),
        ),
        Scene(
            id = "S5",
            title = "세차 후 출발",
            prep = listOf(
                Prep(gear = Gear.DRIVE),
                Prep(speedKmh = 30f, place = Place.NONE),
            ),
            expectSaid = listOf("세차 모드 끝낼게요. 원래대로 돌려놨어요."),
            expectCar = mapOf(
                Target.Recirculation to false,
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
