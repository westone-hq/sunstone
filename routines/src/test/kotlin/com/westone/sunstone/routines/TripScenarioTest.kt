package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.CurrentRoute
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
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.InMemoryMemory
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.routines.port.NaviPort
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-14 16:00:00 · 박건준
 * 클래스: TripScenarioTest
 * 목적: 시나리오 ① 길안내 대화에서 대사집에 없는 경우(연료 넉넉, 거절, 주유만,
 *       최근 세차, 비 소식, 세차 입구 취소 등)와 질문 카드(`Asking`) 값을 가짜
 *       차·가짜 내비·고정 정보로 본다. 대사집 장면 S1~S9 자체는 `ScriptTest`가
 *       돌린다.
 *       기본 준비: P단, 연료 18%, 마지막 세차 36일 전
 * 근거: 인용 — 시연 시나리오 ① 준비 줄, docs/SCREEN.md 6장
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

    /** 지금 길안내 목적지. 길안내 대화와 차일드락이 함께 쓴다 */
    private val route = CurrentRoute()

    /** 길안내 대화 */
    private val trip = TripDialog(car, navi, info, memory, booking, asking, { said += it }, { TODAY }, route)

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
                ChildLockRoutine(route, memory, asking, { TODAY }),
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
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 최근에 세차했으면 주유만 묻는다
     * 목적: 마지막 세차가 3일 전이면 세차 근거 칸과 세차 버튼 없이 가장 싼
     *       주유소로 "주유할까요?" 카드를 올리고, "그래"면 경유지만 넣어 주유소에
     *       두 번 서도 세차 모드를 권하지 않는지 본다.
     */
    @Test
    fun `최근에 세차했으면 주유만 묻는다`() = runTest {
        memory.recordWash(TODAY.minusDays(3))
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        assertEquals("연료가 18%예요. 가는 길에 서울석유양재주유소가 있어요.", said.last())
        val q = asking.question.value!! // 올린 질문
        assertEquals("주유할까요?", q.title)
        assertEquals("가는 길에 서울석유양재주유소가 있어요.", q.detail)
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
     * 수정: 2026-11-14 03:00:00 · 박건준
     * 함수: 세차 기록이 없으면 세차를 묻지 않는다
     * 목적: 마지막 세차일을 모르면 주유만 묻는지 본다(모르는 사실로 판단하지
     *       않는다는 설계 결정 규칙).
     */
    @Test
    fun `세차 기록이 없으면 세차를 묻지 않는다`() = runTest {
        val noMemory = InMemoryMemory(lastWash = null) // 세차 기록 없음
        val fresh = TripDialog(car, navi, info, noMemory, booking, asking, { said += it }, { TODAY }, route) // 기록 없는 대화

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

    /**
     * 작성: 2026-11-13 04:20:00 · 박건준
     * 함수: 경유지를 넣는 동안 진행 중으로 알린다
     * 목적: 내비가 경유지를 넣는 동안(9초) `Asking.working`이 켜져 있다가, 넣고
     *       나면 꺼지고 결과 토스트가 나오는지 본다. 화면은 그동안 들은 말 판을
     *       내리지 않는다.
     */
    @Test
    fun `경유지를 넣는 동안 진행 중으로 알린다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")
        navi.waypointDelayMs = 9_000

        launch { say(runner, "응, 세차도 할게") }
        runCurrent()
        assertTrue(asking.working.value)
        assertTrue(noticed.isEmpty())

        advanceTimeBy(9_001); runCurrent()
        assertFalse(asking.working.value)
        assertEquals(listOf("경유지에 넣고 세차를 예약했어요"), noticed)
        assertTrue(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-13 04:20:00 · 박건준
     * 함수: 경유지를 20초 안에 못 넣으면 실패를 알린다
     * 목적: 내비가 20초 넘게 답하지 않으면 기다리기를 그만두고 "경유지를 넣지
     *       못했어요" 토스트와 로그를 남기며, 세차도 예약하지 않는지 본다.
     */
    @Test
    fun `경유지를 20초 안에 못 넣으면 실패를 알린다`() = runTest {
        val logged = mutableListOf<String>() // 남긴 로그
        val slow = TripDialog(car, navi, info, memory, booking, asking, { said += it }, { TODAY }, route) {
            logged += it
        }
        backgroundScope.launch { asking.notices.collect { noticed += it } }
        slow.navigate("파라다이스 호텔 부산")
        navi.waypointDelayMs = 30_000

        launch { slow.answer(Reply.ALL) }
        advanceTimeBy(19_900); runCurrent()
        assertTrue(asking.working.value)

        advanceTimeBy(200); runCurrent()
        assertFalse(asking.working.value)
        assertEquals(listOf("경유지를 넣지 못했어요"), noticed)
        assertEquals(listOf("경유지 넣기 20초 넘김: 말죽거리주유소"), logged)
        assertFalse(booking.atGasStation)
        assertTrue(navi.waypoints.isEmpty())
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 내일 비가 오면 주유만 묻고 가장 가까운 비를 보인다
     * 목적: 3일 안에 비 예보가 있으면 세차를 권하지 않고 "주유할까요?"만 묻는지,
     *       근거 칸에 "내일 비 예보"가 보이는지, 세차를 권하지 않으니 가장 싼
     *       주유소를 고르는지 본다(docs/SPEC.md 4장).
     */
    @Test
    fun `내일 비가 오면 주유만 묻고 가장 가까운 비를 보인다`() = runTest {
        info.rain = true
        info.rainDay = 1

        trip.navigate("파라다이스 호텔 부산")

        val q = asking.question.value!! // 올린 질문
        assertEquals("주유할까요?", q.title)
        assertEquals("가는 길에 서울석유양재주유소가 있어요.", q.detail)
        assertEquals(listOf(Fact("fuel", "연료 18%"), Fact("rain", "내일 비 예보")), q.facts)
        assertEquals(listOf(Choice("경유지로 넣기", Reply.YES), Choice("괜찮아요", Reply.NO)), q.choices)
        assertEquals("연료가 18%예요. 가는 길에 서울석유양재주유소가 있어요.", said.last())
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 3일 뒤 비까지는 세차를 권하지 않는다
     * 목적: 비가 3일 뒤면 3일 안으로 보아 주유만 묻는지 본다.
     */
    @Test
    fun `3일 뒤 비까지는 세차를 권하지 않는다`() = runTest {
        info.rain = true
        info.rainDay = 3

        trip.navigate("파라다이스 호텔 부산")

        val q = asking.question.value!! // 올린 질문
        assertEquals("주유할까요?", q.title)
        assertEquals(listOf(Fact("fuel", "연료 18%"), Fact("rain", "3일 뒤 비 예보")), q.facts)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 4일 뒤 비면 세차를 권하고 날씨 칸에 알린다
     * 목적: 비가 4~7일 뒤면 세차를 함께 권하고 날씨 칸에 "4일 뒤 비"를 보이는지,
     *       말에는 "일주일간 비 소식이 없어서"를 넣지 않는지 본다.
     */
    @Test
    fun `4일 뒤 비면 세차를 권하고 날씨 칸에 알린다`() = runTest {
        info.rain = true
        info.rainDay = 4

        trip.navigate("파라다이스 호텔 부산")

        val q = asking.question.value!! // 올린 질문
        assertEquals("주유하고 세차할까요?", q.title)
        assertEquals(
            listOf(Fact("fuel", "연료 18%"), Fact("calendar", "세차 36일 전"), Fact("rain", "4일 뒤 비")),
            q.facts,
        )
        assertEquals(
            "연료가 18%예요. 가는 길에 말죽거리주유소가 있어요. 세차 안 하신 지 36일 돼서 세차도 추천드려요.",
            said.last(),
        )
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 8일 뒤 비는 7일 동안 비 없음으로 본다
     * 목적: 본 기간(7일) 밖의 비는 없는 것으로 보고 시연 준비 값과 같은 카드를
     *       올리는지 본다.
     */
    @Test
    fun `8일 뒤 비는 7일 동안 비 없음으로 본다`() = runTest {
        info.rain = true
        info.rainDay = 8

        trip.navigate("파라다이스 호텔 부산")

        val q = asking.question.value!! // 올린 질문
        assertEquals("주유하고 세차할까요?", q.title)
        assertEquals(Fact("sun", "7일 동안 비 없음"), q.facts.last())
        assertTrue(said.last().endsWith("세차 안 하신 지 36일 됐고 일주일간 비 소식이 없어서 세차도 추천드려요."))
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 날씨를 못 받으면 날씨 칸을 빼고 세차일만으로 권한다
     * 목적: 날씨를 모르면 세차일만으로 판단해 세차를 함께 권하고, 근거 칸은
     *       연료와 세차일 둘뿐인지 본다.
     */
    @Test
    fun `날씨를 못 받으면 날씨 칸을 빼고 세차일만으로 권한다`() = runTest {
        info.rain = null

        trip.navigate("파라다이스 호텔 부산")

        val q = asking.question.value!! // 올린 질문
        assertEquals("주유하고 세차할까요?", q.title)
        assertEquals(listOf(Fact("fuel", "연료 18%"), Fact("calendar", "세차 36일 전")), q.facts)
        assertEquals(
            "연료가 18%예요. 가는 길에 말죽거리주유소가 있어요. 세차 안 하신 지 36일 돼서 세차도 추천드려요.",
            said.last(),
        )
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 먼저 세차하겠다고 하면 비 소식을 한 번 알리고 묻는다
     * 목적: 내일 비라 주유만 물었는데 "세차도 하자"면 "내일 비 소식이 있어요.
     *       그래도 세차할까요?" [세차하기] [다음에]를 올리고, [세차하기]면 세차
     *       되는 주유소를 넣고 예약하는지 본다.
     */
    @Test
    fun `먼저 세차하겠다고 하면 비 소식을 한 번 알리고 묻는다`() = runTest {
        info.rain = true
        info.rainDay = 1
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        assertTrue(say(runner, "세차도 하자"))

        val q = asking.question.value!! // 올린 질문
        assertEquals(QuestionKind.WASH_RAIN, q.kind)
        assertEquals("내일 비 소식이 있어요. 그래도 세차할까요?", q.title)
        assertEquals(listOf(Choice("세차하기", Reply.YES), Choice("다음에", Reply.NO)), q.choices)
        assertEquals("내일 비 소식이 있어요. 그래도 세차할까요?", said.last())
        assertTrue(navi.waypoints.isEmpty())

        assertTrue(trip.answer(Reply.YES)); runCurrent()
        assertEquals(listOf(WASH_STATION.place), navi.waypoints)
        assertTrue(booking.atGasStation)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 비 소식을 듣고 다음에라고 하면 주유소만 넣는다
     * 목적: 비 소식 질문에 [다음에]면 세차는 예약하지 않고 처음 보였던 가장 싼
     *       주유소만 경유지로 넣는지 본다.
     */
    @Test
    fun `비 소식을 듣고 다음에라고 하면 주유소만 넣는다`() = runTest {
        info.rain = true
        trip.navigate("파라다이스 호텔 부산")
        trip.answer(Reply.ALL)

        assertTrue(trip.answer(Reply.NO))

        assertEquals(listOf(CHEAP_STATION.place), navi.waypoints)
        assertFalse(booking.atGasStation)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 비가 없으면 최근에 세차했어도 묻지 않고 세차를 넣는다
     * 목적: 세차한 지 3일이라 주유만 물었는데 "세차도 할게"면 다시 묻지 않고 세차
     *       되는 주유소를 넣고 예약하는지 본다.
     */
    @Test
    fun `비가 없으면 최근에 세차했어도 묻지 않고 세차를 넣는다`() = runTest {
        memory.recordWash(TODAY.minusDays(3))
        trip.navigate("파라다이스 호텔 부산")
        assertEquals("주유할까요?", asking.question.value?.title)

        assertTrue(trip.answer(Reply.ALL))

        assertNull(asking.question.value)
        assertEquals(listOf(WASH_STATION.place), navi.waypoints)
        assertTrue(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 세차 입구에 멈춰 있으면 비가 와도 묻지 않는다
     * 목적: P단으로 멈춘 채 세차 모드를 켜려 하면 날씨를 묻지 않고(`wantWash()`가
     *       false), 달리는 중이면 비 소식을 묻고 [세차하기]에 세차 모드를 켜는
     *       곳(`runWash`)을 부르는지 본다.
     */
    @Test
    fun `세차 입구에 멈춰 있으면 비가 와도 묻지 않는다`() = runTest {
        info.rain = true
        var ran = 0 // 세차 모드를 켜라고 부른 횟수
        val dialog = TripDialog( // 세차 모드 켜기를 세는 대화
            car, navi, info, memory, booking, asking, { said += it }, { TODAY }, route, runWash = { ran++ },
        )

        assertFalse(dialog.wantWash())
        assertNull(asking.question.value)

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 40f) }
        assertTrue(dialog.wantWash())
        assertEquals("내일 비 소식이 있어요. 그래도 세차할까요?", asking.question.value?.title)
        assertTrue(dialog.answer(Reply.YES))
        assertEquals(1, ran)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 거절한 날에는 다시 권하지 않는다
     * 목적: 주유 권유를 거절하면 같은 날 새 길안내에서는 묻지 않고, 다음 날에는
     *       다시 묻는지 본다(같은 권유 하루에 한 번).
     */
    @Test
    fun `거절한 날에는 다시 권하지 않는다`() = runTest {
        var day = TODAY // 오늘, 테스트가 넘긴다
        val daily = Asking(memory) { day } // 거절한 날을 기억하는 질문 관리
        val dialog = TripDialog(car, navi, info, memory, booking, daily, { said += it }, { day }, route) // 그 대화
        dialog.navigate("파라다이스 호텔 부산")
        assertTrue(dialog.answer(Reply.NO))

        dialog.navigate("파라다이스 호텔 부산")
        assertNull(daily.question.value)
        assertEquals("파라다이스 호텔 부산으로 안내할게요.", said.last())

        day = TODAY.plusDays(1)
        dialog.navigate("파라다이스 호텔 부산")
        assertEquals("주유하고 세차할까요?", daily.question.value?.title)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 주유만 고른 날에는 세차를 다시 권하지 않는다
     * 목적: 세차도 물은 카드에 [주유만]이면 세차 권유를 거절한 것으로 남겨, 같은
     *       날 새 길안내에서는 "주유할까요?"만 묻는지 본다.
     */
    @Test
    fun `주유만 고른 날에는 세차를 다시 권하지 않는다`() = runTest {
        trip.navigate("파라다이스 호텔 부산")
        assertTrue(trip.answer(Reply.ONLY))

        trip.navigate("파라다이스 호텔 부산")

        val q = asking.question.value!! // 올린 질문
        assertEquals("주유할까요?", q.title)
        assertEquals(listOf(Fact("fuel", "연료 18%")), q.facts)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 대답 없이 닫힌 질문은 이번 길에서만 다시 묻지 않는다
     * 목적: 20초 동안 대답이 없어 닫힌 질문(`expire()`)은 거절로 치지 않아, 같은
     *       날 새 길안내에서는 다시 묻는지 본다.
     */
    @Test
    fun `대답 없이 닫힌 질문은 이번 길에서만 다시 묻지 않는다`() = runTest {
        val daily = Asking(memory) { TODAY } // 거절한 날을 기억하는 질문 관리
        val dialog = TripDialog(car, navi, info, memory, booking, daily, { said += it }, { TODAY }, route) // 그 대화
        dialog.navigate("파라다이스 호텔 부산")

        daily.expire(QuestionKind.FUEL_STOP)
        dialog.expire()

        assertNull(daily.question.value)
        assertFalse(daily.declinedToday(QuestionKind.FUEL_STOP))
        dialog.navigate("파라다이스 호텔 부산")
        assertEquals("주유하고 세차할까요?", daily.question.value?.title)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 연료가 넉넉해도 목적지까지 못 가면 주유를 권한다
     * 목적: 주행 가능 거리가 목적지까지 거리보다 짧으면 연료가 25%를 넘어도
     *       주유를 묻는지 본다.
     */
    @Test
    fun `연료가 넉넉해도 목적지까지 못 가면 주유를 권한다`() = runTest {
        car.update { it.copy(fuelPercent = 60, rangeKm = 100) }
        navi.route = navi.route!!.copy(distanceM = 400_000)

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(QuestionKind.FUEL_STOP, asking.question.value?.kind)
        assertEquals(Fact("fuel", "연료 60%"), asking.question.value?.facts?.first())
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 세차 날씨는 목적지가 아니라 세차할 주유소 좌표로 본다
     * 목적: 비 예보를 부산(목적지)이 아니라 세차 되는 주유소 자리로 묻는지 본다.
     */
    @Test
    fun `세차 날씨는 목적지가 아니라 세차할 주유소 좌표로 본다`() = runTest {
        trip.navigate("파라다이스 호텔 부산")

        assertEquals(WASH_STATION.place.point, info.asked)
        assertEquals(Reply.ALL, asking.question.value!!.choices.first().reply)
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 날씨를 기다리는 중에 다시 시작하면 질문을 올리지 않는다
     * 목적: 예보가 늦게 오는 동안 시연을 처음으로 돌리면(`invalidate()`) 늦은
     *       응답이 질문을 올리지 않고 처리 중 표시도 꺼지는지 본다.
     */
    @Test
    fun `날씨를 기다리는 중에 다시 시작하면 질문을 올리지 않는다`() = runTest {
        val slow = object : InfoPort { // 예보가 1초 걸리는 정보
            override suspend fun gasStations() = listOf(WASH_STATION)
            override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? {
                delay(1000)
                return RainOutlook(null)
            }
        }
        val dialog = TripDialog(car, navi, slow, memory, booking, asking, { said += it }, { TODAY }, route) // 그 대화
        val job = launch { dialog.navigate("파라다이스 호텔 부산") } // 길안내 요청
        runCurrent()
        assertTrue(asking.working.value)

        dialog.invalidate()
        advanceTimeBy(1001)
        job.join()

        assertFalse(asking.working.value)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 목적지 후보가 여럿이면 고른 뒤에 길안내를 시작한다
     * 목적: 후보가 둘이면 길안내를 시작하지 않고 후보를 내놓고, 고르면 그
     *       목적지로 시작하는지 본다.
     */
    @Test
    fun `목적지 후보가 여럿이면 고른 뒤에 길안내를 시작한다`() = runTest {
        val second = RESORT.copy(name = "테스트 목적지", address = "후보 주소", point = GeoPoint(37.0, 127.0)) // 둘째 후보
        val port = object : NaviPort by navi { // 후보를 둘 주는 내비
            override suspend fun findPlaces(query: String) = listOf(RESORT, second)
        }
        val dialog = TripDialog(car, port, info, memory, booking, asking, { said += it }, { TODAY }, route) // 그 대화

        dialog.navigate("호텔")
        assertNull(navi.destination)
        assertEquals(2, dialog.destinations.value.size)
        assertEquals("어디로 갈까요? 카드에서 골라 주세요.", said.last())

        dialog.selectDestination(1)
        assertEquals(second, navi.destination)
        assertTrue(dialog.destinations.value.isEmpty())
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 실제 검색에서 주유소가 없으면 그렇다고 말하고 묻지 않는다
     * 목적: 지금 위치 근처를 실제로 찾았는데 없으면 지어낸 주유소를 권하지 않고
     *       못 찾았다고 말하는지 본다.
     */
    @Test
    fun `실제 검색에서 주유소가 없으면 그렇다고 말하고 묻지 않는다`() = runTest {
        val empty = object : InfoPort { // 주유소를 못 찾는 실제 정보
            override val nearbyStations = true
            override suspend fun gasStations() = emptyList<GasStation>()
            override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? = null
        }

        TripDialog(car, navi, empty, memory, booking, asking, { said += it }, { TODAY }, route)
            .navigate("파라다이스 호텔 부산")

        assertNull(asking.question.value)
        assertEquals("근처 주유소를 찾지 못했어요.", said.last())
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 도착을 알아챌 수 없으면 세차를 예약하지 않는다
     * 목적: 경유지는 넣었지만 그 주유소 도착을 알아챌 수 없으면(근접 알림 등록
     *       실패) 세차를 예약하지 않는지 본다.
     */
    @Test
    fun `도착을 알아챌 수 없으면 세차를 예약하지 않는다`() = runTest {
        navi.route = navi.route!!.copy(arrivalTracked = false)
        trip.navigate("파라다이스 호텔 부산")

        trip.answer(Reply.ALL)

        assertEquals(WASH_STATION.place.point, navi.waypoints.single().point)
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: 가격을 모르는 주유소는 가까운 곳을 고른다
     * 목적: 장소만 찾아 가격·세차 시설을 모르는 주유소들이면 가장 가까운 곳으로
     *       주유만 묻고("근처에"), 날씨는 묻지 않는지 본다.
     */
    @Test
    fun `가격을 모르는 주유소는 가까운 곳을 고른다`() = runTest {
        val close = WASH_STATION.copy(priceWon = null, carWash = false, facilityKnown = false, distanceM = 100) // 가까운 곳
        val far = PRICEY_WASH_STATION.copy(priceWon = null, carWash = false, facilityKnown = false, distanceM = 900) // 먼 곳
        var asked = 0 // 날씨를 물은 횟수
        val live = object : InfoPort { // 장소만 찾는 실제 정보
            override val nearbyStations = true
            override suspend fun gasStations() = listOf(far, close)
            override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? = RainOutlook(null).also { asked++ }
        }
        val dialog = TripDialog(car, navi, live, memory, booking, asking, { said += it }, { TODAY }, route) // 그 대화

        dialog.navigate("파라다이스 호텔 부산")

        assertEquals("근처에 말죽거리주유소가 있어요.", asking.question.value?.detail)
        assertEquals("주유할까요?", asking.question.value?.title)
        assertEquals(0, asked)
        dialog.answer(Reply.YES)
        assertEquals(close.place.point, navi.waypoints.single().point)
        assertFalse(booking.atGasStation)
    }
}
