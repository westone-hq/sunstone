package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.Choice
import com.westone.sunstone.routines.dialog.Fact
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.QuestionRow
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.dialog.TripDialog
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.fake.FakeInfo
import com.westone.sunstone.routines.fake.FakeNavi
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.InMemoryMemory
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: TripScenarioTest
 * 목적: 시나리오 ① 길안내 대화에서 대사집에 없는 경우(연료 넉넉, 거절, 주유만,
 *       최근 세차, 비 소식, 세차 입구 취소 등)와 질문 카드(`Asking`) 값을 가짜
 *       차·가짜 내비·고정 정보로 본다. 대사집 장면 S1~S9 자체는 `ScriptTest`가
 *       돌린다.
 *       기본 준비: P단, 연료 18%, 마지막 세차 36일 전
 * 근거: 인용 — Tab 1 4.3 시나리오 ① 준비 줄, docs/SCREEN.md 6장
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripScenarioTest {

    private companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: TODAY
         * 목적: 테스트의 오늘 날짜.
         */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 11)

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: RESORT
         * 목적: 시나리오 목적지.
         */
        val RESORT = PlaceHit("파라다이스 호텔 부산", GeoPoint(35.1602, 129.1652))

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: WASH_STATION
         * 목적: 세차 되는 주유소 중 가장 싼 곳. 시나리오가 경유지로 넣을 곳이다.
         */
        val WASH_STATION = GasStation(PlaceHit("말죽거리주유소", GeoPoint(37.4852, 127.0407)), 1650, carWash = true)

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: CHEAP_STATION
         * 목적: 가장 싸지만 세차가 안 되는 주유소. 고르면 안 된다.
         */
        val CHEAP_STATION = GasStation(PlaceHit("서울석유양재주유소", GeoPoint(37.4781, 127.0396)), 1610, carWash = false)

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: PRICEY_WASH_STATION
         * 목적: 세차는 되지만 더 비싼 주유소. 고르면 안 된다.
         */
        val PRICEY_WASH_STATION = GasStation(PlaceHit("S-OIL 중앙주유소", GeoPoint(37.4816, 127.0422)), 1690, carWash = true)
    }

    /** 앱이 한 말, 말한 순서대로. 장면마다 비우고 그 장면 말만 본다 */
    private val said = mutableListOf<String>()

    /** 앱이 낸 토스트, 낸 순서대로 */
    private val noticed = mutableListOf<String>()

    /** 선루프 두 개. Pleos 에뮬레이터 차처럼 지원하지 않게 둔다 */
    private val roofs = setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2))

    /** 준비 상태의 가짜 차: P단, 연료 18% */
    private val car = FakeCar(CarState(gear = Gear.PARK, fuelPercent = 18)).apply { unsupported = roofs }

    /** 가짜 내비. 목적지는 파라다이스 호텔 부산 하나 */
    private val navi = FakeNavi(listOf(RESORT))

    /** 고정 정보: 주유소 세 곳, 일주일 비 소식 없음 */
    private val info = FakeInfo(listOf(CHEAP_STATION, PRICEY_WASH_STATION, WASH_STATION), rain = false)

    /** 마지막 세차 36일 전 */
    private val memory = InMemoryMemory(lastWash = TODAY.minusDays(36))

    /** 세차 예약. 길안내 대화와 세차 모드가 함께 쓴다 */
    private val booking = WashBooking()

    /** 질문·토스트. 길안내 대화와 실행기가 함께 쓴다 */
    private val asking = Asking()

    /** 길안내 대화 */
    private val trip = TripDialog(car, navi, info, memory, booking, asking, { said += it }, { TODAY })

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지와 토스트 받기를
     *       시작한다.
     * 반환: 실행기
     */
    private fun TestScope.runner(): RoutineRunner {
        backgroundScope.launch { asking.notices.collect { noticed += it } }
        return RoutineRunner(
            car = car,
            routines = listOf(
                WashRoutine(booking, memory, asking, { TODAY }),
                ChildLockRoutine(),
                SeatMemoryRoutine(InMemorySeatProfiles()),
            ),
            speaker = { said += it },
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            asking = asking,
        ).also { it.start() }
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: say
     * 목적: 운전자 말 하나를 앱처럼 처리한다. `AppGraph.handle()`과 같은 순서로
     *       길안내 → 루틴 권하기의 대답 → 길안내 대화의 대답을 본다.
     * 인자: runner — 실행기
     *       text — 운전자가 한 말
     * 반환: 처리했으면 true, 못 알아들었으면 false
     */
    private suspend fun say(runner: RoutineRunner, text: String): Boolean =
        when (val action = PhraseMatcher.match(text)) {
            is VoiceAction.Navigate -> { trip.navigate(action.destination); true }
            is VoiceAction.Answer -> runner.answer(action.reply) || trip.answer(action.reply)
            else -> false
        }

    /**
     * 작성: 2026-11-12 18:36:00 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: stopAtStationTwice
     * 목적: 주유소에 들어와 주유 정차를 하고, 조금 움직였다가 다시 선다(세차
     *       입구). 대사집 S4~S6 준비와 같다.
     */
    private fun TestScope.stopAtStationTwice() {
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 60f) }; runCurrent()
        car.update { it.copy(place = Place.GAS_STATION, speedKmh = 0f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK) }; runCurrent()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 5f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f) }; runCurrent()
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 연료가 넉넉하면 길안내만 하고 묻지 않는다
     * 목적: 연료가 기준보다 많으면 길안내 문장만 말하고 카드도 올리지 않으며,
     *       뒤이은 "응"은 대답할 것이 없어 처리하지 않는지 본다.
     */
    @Test
    fun `연료가 넉넉하면 길안내만 하고 묻지 않는다`() = runTest {
        car.update { it.copy(fuelPercent = 60) }
        val runner = runner()
        runCurrent()

        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        assertEquals(listOf("파라다이스 호텔 부산으로 안내할게요."), said)
        assertNull(asking.question.value)
        assertFalse(say(runner, "응"))
        assertTrue(navi.waypoints.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 연료를 모르면 묻지 않는다
     * 목적: 연료 값을 읽을 수 없으면(전기차 에뮬레이터에 데모 fuel을 안 넣었을
     *       때) 주유를 권하지 않는지 본다.
     */
    @Test
    fun `연료를 모르면 묻지 않는다`() = runTest {
        car.update { it.copy(fuelPercent = null) }

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("파라다이스 호텔 부산으로 안내할게요."), said)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 달리는 중에는 주유를 권하지 않는다
     * 목적: 주행 중 새 제안 금지 정책대로, 달리면서 길안내를 부탁하면 길안내만
     *       하는지 본다.
     */
    @Test
    fun `달리는 중에는 주유를 권하지 않는다`() = runTest {
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 50f) }

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("파라다이스 호텔 부산으로 안내할게요."), said)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: 주유와 세차를 한 카드로 묻는다
     * 목적: 연료가 낮고 세차할 때면 길안내 말 뒤에 주유·세차를 한 번에
     *       권하고, 카드에 세차 되는 가장 싼 주유소(굵게), 근거 칸 셋, 버튼
     *       [주유하고 세차] [주유만]을 올리는지 본다.
     */
    @Test
    fun `주유와 세차를 한 카드로 묻는다`() = runTest {
        trip.navigate("파라다이스 호텔 부산")

        assertEquals(
            listOf(
                "파라다이스 호텔 부산으로 안내할게요.",
                "연료가 18%예요. 가는 길에 말죽거리주유소가 있어요. " +
                    "세차 안 하신 지 36일 됐고 일주일간 비 소식이 없어서 세차도 추천드려요.",
            ),
            said,
        )
        val q = asking.question.value!! // 올린 질문
        assertEquals(QuestionKind.FUEL_STOP, q.kind)
        assertEquals("주유하고 세차할까요?", q.title)
        assertEquals("가는 길에 말죽거리주유소가 있어요. 세차도 되는 곳이에요.", q.detail)
        assertEquals("말죽거리주유소", q.emphasis)
        assertEquals(
            listOf(Fact("fuel", "연료 18%"), Fact("calendar", "세차 36일 전"), Fact("sun", "7일 동안 비 없음")),
            q.facts,
        )
        assertEquals(listOf(Choice("주유하고 세차", Reply.ALL), Choice("주유만", Reply.ONLY)), q.choices)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 주유 제안을 거절하면 경유지를 넣지 않는다
     * 목적: 주유 질문에 "괜찮아"라고 하면 알았다고만 하고 카드를 내리며
     *       경유지·세차 예약을 하지 않는지 본다.
     */
    @Test
    fun `주유 제안을 거절하면 경유지를 넣지 않는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        said.clear()
        assertTrue(say(runner, "괜찮아"))

        assertEquals(listOf("알겠어요."), said)
        assertNull(asking.question.value)
        assertTrue(navi.waypoints.isEmpty())
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-12 18:36:00 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 새 길안내를 시작하면 거절한 질문도 다시 묻는다
     * 목적: 거절 기록은 그 길안내 안에서만 남는지 본다.
     */
    @Test
    fun `새 길안내를 시작하면 거절한 질문도 다시 묻는다`() = runTest {
        trip.navigate("파라다이스 호텔 부산")
        trip.answer(Reply.NO)

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(QuestionKind.FUEL_STOP, asking.question.value?.kind)
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: 세차도 할게면 경유지를 넣고 세차를 예약한다
     * 목적: "응, 세차도 할게"면 말 없이 경유지를 넣고 세차를 예약하며 판에
     *       토스트를 내는지, 같은 대답을 또 하면 물은 게 없어 처리하지 않는지
     *       본다.
     */
    @Test
    fun `세차도 할게면 경유지를 넣고 세차를 예약한다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        said.clear()
        assertTrue(say(runner, "응, 세차도 할게")); runCurrent()

        assertTrue(said.isEmpty())
        assertEquals(listOf("경유지에 넣고 세차를 예약했어요"), noticed)
        assertEquals(listOf(WASH_STATION.place), navi.waypoints)
        assertTrue(booking.atGasStation)
        assertNull(asking.question.value)
        assertFalse(say(runner, "응, 세차도 할게"))
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: 세차 카드에 응이면 세차까지 한다
     * 목적: 주유·세차 카드에 그냥 "응"이라고 하면 첫 번째 버튼(주유하고 세차)을
     *       고른 것과 같은지 본다.
     */
    @Test
    fun `세차 카드에 응이면 세차까지 한다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        assertTrue(say(runner, "응")); runCurrent()

        assertEquals(listOf("경유지에 넣고 세차를 예약했어요"), noticed)
        assertTrue(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: 주유만이면 경유지만 넣고 주유소에서 세차를 권하지 않는다
     * 목적: "주유만"이면 경유지만 넣고 예약하지 않아, 주유소에 두 번 서도 세차
     *       모드를 권하지 않는지 본다.
     */
    @Test
    fun `주유만이면 경유지만 넣고 주유소에서 세차를 권하지 않는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        said.clear()
        assertTrue(say(runner, "주유만")); runCurrent()

        assertTrue(said.isEmpty())
        assertEquals(listOf("경유지에 넣었어요"), noticed)
        assertEquals(listOf(WASH_STATION.place), navi.waypoints)
        assertFalse(booking.atGasStation)

        stopAtStationTwice()
        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 최근에 세차했으면 주유만 묻는다
     * 목적: 마지막 세차가 3일 전이면 세차 근거 칸과 세차 버튼 없이 "주유할까요?"
     *       카드를 올리고, "그래"면 경유지만 넣어 주유소에 두 번 서도 세차 모드를
     *       권하지 않는지 본다.
     */
    @Test
    fun `최근에 세차했으면 주유만 묻는다`() = runTest {
        memory.recordWash(TODAY.minusDays(3))
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        assertEquals("연료가 18%예요. 가는 길에 말죽거리주유소가 있어요.", said.last())
        val q = asking.question.value!! // 올린 질문
        assertEquals("주유할까요?", q.title)
        assertEquals("가는 길에 말죽거리주유소가 있어요.", q.detail)
        assertEquals(listOf(Fact("fuel", "연료 18%")), q.facts)
        assertEquals(listOf(Choice("경유지로 넣기", Reply.YES), Choice("괜찮아요", Reply.NO)), q.choices)

        said.clear()
        assertTrue(say(runner, "그래")); runCurrent()
        assertEquals(listOf("경유지에 넣었어요"), noticed)
        assertFalse(booking.atGasStation)

        stopAtStationTwice()
        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 비 소식이 있으면 세차를 추천하지 않는다
     * 목적: 일주일 안에 비가 오면 주유만 묻는지 본다.
     */
    @Test
    fun `비 소식이 있으면 세차를 추천하지 않는다`() = runTest {
        info.rain = true

        trip.navigate("파라다이스 호텔 부산")

        assertEquals("주유할까요?", asking.question.value?.title)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 세차 기록이 없으면 세차를 묻지 않는다
     * 목적: 마지막 세차일을 모르면 주유만 묻는지 본다(모르는 사실로 판단하지
     *       않는다, Tab 1 2장).
     */
    @Test
    fun `세차 기록이 없으면 세차를 묻지 않는다`() = runTest {
        val noMemory = InMemoryMemory(lastWash = null) // 세차 기록 없음
        val fresh = TripDialog(car, navi, info, noMemory, booking, asking, { said += it }, { TODAY }) // 기록 없는 대화

        fresh.navigate("파라다이스 호텔 부산")

        assertEquals("주유할까요?", asking.question.value?.title)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 세차 되는 주유소가 없으면 가장 싼 곳을 넣는다
     * 목적: 세차 되는 곳이 하나도 없으면 가장 싼 주유소로 주유만 묻고, 받아들이면
     *       그곳을 경유지로 넣는지 본다.
     */
    @Test
    fun `세차 되는 주유소가 없으면 가장 싼 곳을 넣는다`() = runTest {
        info.stations = listOf(CHEAP_STATION)
        trip.navigate("파라다이스 호텔 부산")
        assertEquals("가는 길에 서울석유양재주유소가 있어요.", asking.question.value?.detail)

        assertTrue(trip.answer(Reply.YES))

        assertEquals(listOf(CHEAP_STATION.place), navi.waypoints)
        assertNull(asking.question.value)
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 주유소를 못 찾으면 묻지 않는다
     * 목적: 근처 주유소가 없으면 주유를 묻지 않고 길안내 말만 하는지 본다.
     */
    @Test
    fun `주유소를 못 찾으면 묻지 않는다`() = runTest {
        info.stations = emptyList()

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("파라다이스 호텔 부산으로 안내할게요."), said)
        assertNull(asking.question.value)
        assertFalse(trip.answer(Reply.YES))
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 목적지를 못 찾으면 다시 말해 달라고 한다
     * 목적: 장소 목록에 없는 목적지면 경로를 시작하지 않고 다시 말해 달라고
     *       하는지 본다.
     */
    @Test
    fun `목적지를 못 찾으면 다시 말해 달라고 한다`() = runTest {
        trip.navigate("제주 공항")

        assertEquals(listOf("제주 공항을 찾지 못했어요. 다시 말해 주세요."), said)
        assertNull(navi.destination)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 경로를 시작하지 못하면 알린다
     * 목적: 내비가 경로를 시작하지 못하면 주유를 권하지 않고 알리기만 하는지
     *       본다.
     */
    @Test
    fun `경로를 시작하지 못하면 알린다`() = runTest {
        navi.route = null

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("길안내를 시작하지 못했어요. 다시 말해 주세요."), said)
        assertFalse(trip.answer(Reply.YES))
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: 세차를 예약하면 세차 입구에서 5초 뒤 켠다
     * 목적: 세차를 예약했으면 주유 정차에서는 말 없이 토스트만, 세차 입구에서는
     *       다시 묻지 않고 "곧 세차 모드 켤게요." 카드를 올리고, 5초가 지나면
     *       대답 없이도 세차 모드를 켜는지 본다.
     */
    @Test
    fun `세차를 예약하면 세차 입구에서 5초 뒤 켠다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")
        say(runner, "응, 세차도 할게"); runCurrent()

        said.clear(); noticed.clear()
        stopAtStationTwice()
        assertEquals(listOf("세차 입구에서 맞춰 드릴게요"), noticed)
        assertEquals(listOf("곧 세차 모드 켤게요."), said)
        val q = asking.question.value!! // 올린 카드
        assertEquals(QuestionKind.WASH_MODE, q.kind)
        assertEquals("곧 세차 모드를 켤게요", q.title)
        assertEquals("창문, 와이퍼, 공조, 문을 세차용으로 맞춰요.", q.detail)
        assertEquals(listOf(Choice("지금 켜기", Reply.YES, 5_000), Choice("취소", Reply.NO)), q.choices)
        assertEquals(listOf("창문", "와이퍼", "내기 순환", "문 잠금", "미러"), q.rows.map { it.text })
        assertEquals(QuestionRow("mirror", "미러", manual = true, note = "직접"), q.rows.last())

        advanceTimeBy(4_900); runCurrent()
        assertNull(runner.ui.value.runs[RoutineId.WASH])

        advanceTimeBy(200); runCurrent()
        assertEquals("세차 모드 맞췄어요. 사이드미러는 직접 접어 주세요.", said.last())
        assertEquals(true, car.value(Target.Recirculation))
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: 세차 입구에서 취소라고 하면 켜지 않는다
     * 목적: 5초가 지나기 전에 "취소"라고 하면 켜지 않고, 권하기 간격(10분)이
     *       지나 다시 서도 같은 길에서는 다시 권하지 않는지 본다.
     */
    @Test
    fun `세차 입구에서 취소라고 하면 켜지 않는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")
        say(runner, "응, 세차도 할게"); runCurrent()
        stopAtStationTwice()

        said.clear()
        assertTrue(say(runner, "취소"))
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf("알겠어요."), said)
        assertNull(runner.ui.value.runs[RoutineId.WASH])
        assertNull(asking.question.value)

        advanceTimeBy(11 * 60_000L)
        said.clear()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 5f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f) }; runCurrent()
        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }
}
