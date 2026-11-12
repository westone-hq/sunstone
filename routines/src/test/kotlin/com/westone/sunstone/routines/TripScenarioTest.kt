package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.QuestionRow
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
 * 수정: 2026-11-12 18:36:00 · 박건준
 * 클래스: TripScenarioTest
 * 목적: 시나리오 ① 길안내 대화에서 대사집에 없는 경우(연료 넉넉, 거절, 최근
 *       세차, 비 소식 등)와 질문 패널(`Asking`) 값을 가짜 차·가짜 내비·고정
 *       정보로 본다. 대사집 장면 S1~S10 자체는 `ScriptTest`가 돌린다.
 *       기본 준비: P단, 연료 18%, 마지막 세차 36일 전
 * 근거: 인용 — Tab 1 4.3 시나리오 ① 준비 줄, docs/SCREEN.md 3장
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
     * 수정: 2026-11-12 11:58:00 · 박희정
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
            is VoiceAction.Answer -> runner.answer(action.yes) || trip.answer(action.yes)
            else -> false
        }

    /**
     * 작성: 2026-11-12 18:36:00 · 박건준
     * 함수: stopAtStationTwice
     * 목적: 주유소에 들어와 주유 정차를 하고, 조금 움직였다가 다시 선다(세차
     *       입구). 대사집 S6~S8 준비와 같다.
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
     * 목적: 연료가 기준보다 많으면 길안내 문장만 말하고 패널도 올리지 않으며,
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
     * 작성: 2026-11-12 18:36:00 · 박건준
     * 함수: 주유 제안 패널에 세차 되는 가장 싼 주유소를 보인다
     * 목적: 연료가 낮으면 길안내 말과 주유 질문을 따로 말하고, 패널에 세차
     *       되는 곳 중 가장 싼 주유소와 "세차 가능"을 올리는지 본다.
     */
    @Test
    fun `주유 제안 패널에 세차 되는 가장 싼 주유소를 보인다`() = runTest {
        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("파라다이스 호텔 부산으로 안내할게요.", "연료가 18%예요. 출발 전에 주유하고 가실래요?"), said)
        val q = asking.question.value!! // 올린 질문
        assertEquals(QuestionKind.FUEL_STOP, q.kind)
        assertEquals("연료가 부족해요", q.title)
        assertEquals("가는 길에 들를 주유소를 찾았어요.", q.detail)
        assertEquals(listOf(QuestionRow("fuel", "말죽거리주유소", note = "세차 가능")), q.rows)
        assertEquals("경유지로 넣기", q.yesLabel)
        assertEquals("괜찮아요", q.noLabel)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 주유 제안을 거절하면 경유지를 넣지 않는다
     * 목적: 주유 질문에 "괜찮아"라고 하면 알았다고만 하고 패널을 내리며
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
     * 함수: 새 길안내를 시작하면 거절한 질문도 다시 묻는다
     * 목적: 거절 기록은 그 길안내 안에서만 남는지 본다.
     */
    @Test
    fun `새 길안내를 시작하면 거절한 질문도 다시 묻는다`() = runTest {
        trip.navigate("파라다이스 호텔 부산")
        trip.answer(yes = false)

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(QuestionKind.FUEL_STOP, asking.question.value?.kind)
    }

    /**
     * 작성: 2026-11-12 18:36:00 · 박건준
     * 함수: 주유소를 넣으면 토스트를 내고 세차를 묻는다
     * 목적: 주유 질문에 "그래"면 경유지를 넣고 토스트를 내며, 세차 추천 패널을
     *       올리고 추천 말을 하는지 본다.
     */
    @Test
    fun `주유소를 넣으면 토스트를 내고 세차를 묻는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        said.clear()
        say(runner, "그래"); runCurrent()

        assertEquals(
            listOf(
                "말죽거리주유소를 경유지로 넣었어요.",
                "세차 안 하신 지 36일 됐어요. 일주일간 비 소식이 없는데, 거기서 세차도 하실래요?",
            ),
            said,
        )
        assertEquals(listOf("경유지에 넣었어요"), noticed)
        assertEquals(listOf(WASH_STATION.place), navi.waypoints)
        val q = asking.question.value!! // 올린 질문
        assertEquals(QuestionKind.WASH_RECOMMEND, q.kind)
        assertEquals("세차하기 좋은 날이에요", q.title)
        assertEquals(listOf(QuestionRow("calendar", "마지막 세차 36일 전"), QuestionRow("sun", "7일 동안 비 없음")), q.rows)
        assertEquals("세차도 할게요", q.yesLabel)
        assertFalse(booking.atGasStation) // 대답 전에는 예약하지 않는다
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 최근에 세차했으면 추천도 예약도 하지 않는다
     * 목적: 마지막 세차가 3일 전이면 경유지만 넣고 세차를 묻지 않으며, 그
     *       주유소에 두 번 서도 세차 모드를 권하지 않는지 본다.
     */
    @Test
    fun `최근에 세차했으면 추천도 예약도 하지 않는다`() = runTest {
        memory.recordWash(TODAY.minusDays(3))
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")

        said.clear()
        say(runner, "그래")
        assertEquals(listOf("말죽거리주유소를 경유지로 넣었어요."), said)
        assertNull(asking.question.value)
        assertFalse(booking.atGasStation)

        said.clear()
        stopAtStationTwice()
        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 비 소식이 있으면 세차를 추천하지 않는다
     * 목적: 일주일 안에 비가 오면 경유지만 넣고 세차는 묻지 않는지 본다.
     */
    @Test
    fun `비 소식이 있으면 세차를 추천하지 않는다`() = runTest {
        info.rain = true
        trip.navigate("파라다이스 호텔 부산")

        said.clear()
        trip.answer(yes = true)

        assertEquals(listOf("말죽거리주유소를 경유지로 넣었어요."), said)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 세차 기록이 없으면 세차를 묻지 않는다
     * 목적: 마지막 세차일을 모르면 경유지만 넣고 세차는 묻지 않는지 본다(모르는
     *       사실로 판단하지 않는다, Tab 1 2장).
     */
    @Test
    fun `세차 기록이 없으면 세차를 묻지 않는다`() = runTest {
        val noMemory = InMemoryMemory(lastWash = null) // 세차 기록 없음
        val fresh = TripDialog(car, navi, info, noMemory, booking, asking, { said += it }, { TODAY }) // 기록 없는 대화
        fresh.navigate("파라다이스 호텔 부산")

        said.clear()
        fresh.answer(yes = true)

        assertEquals(listOf("말죽거리주유소를 경유지로 넣었어요."), said)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 세차 되는 주유소가 없으면 가장 싼 곳을 넣는다
     * 목적: 세차 되는 곳이 하나도 없으면 가장 싼 주유소를 "세차 가능" 없이
     *       보이고, 넣은 뒤 세차를 묻지 않는지 본다.
     */
    @Test
    fun `세차 되는 주유소가 없으면 가장 싼 곳을 넣는다`() = runTest {
        info.stations = listOf(CHEAP_STATION)
        trip.navigate("파라다이스 호텔 부산")
        assertEquals(listOf(QuestionRow("fuel", "서울석유양재주유소")), asking.question.value?.rows)

        said.clear()
        trip.answer(yes = true)

        assertEquals(listOf("서울석유양재주유소를 경유지로 넣었어요."), said)
        assertEquals(listOf(CHEAP_STATION.place), navi.waypoints)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 주유소를 못 찾으면 묻지 않는다
     * 목적: 근처 주유소가 없으면 주유를 묻지 않고 길안내 말만 하는지 본다.
     */
    @Test
    fun `주유소를 못 찾으면 묻지 않는다`() = runTest {
        info.stations = emptyList()

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("파라다이스 호텔 부산으로 안내할게요."), said)
        assertNull(asking.question.value)
        assertFalse(trip.answer(yes = true))
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
     * 함수: 경로를 시작하지 못하면 알린다
     * 목적: 내비가 경로를 시작하지 못하면 주유를 권하지 않고 알리기만 하는지
     *       본다.
     */
    @Test
    fun `경로를 시작하지 못하면 알린다`() = runTest {
        navi.route = null

        trip.navigate("파라다이스 호텔 부산")

        assertEquals(listOf("길안내를 시작하지 못했어요. 다시 말해 주세요."), said)
        assertFalse(trip.answer(yes = true))
    }

    /**
     * 작성: 2026-11-12 11:58:00 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 세차 추천에 그래 그러자면 예약하고 세차 입구에서 묻는다
     * 목적: 세차 추천에 "그래 그러자"면 예약하고 토스트를 내며, 주유 정차에서는
     *       말 없이 토스트만, 세차 입구 정차에서 세차 모드를 묻는지 본다. 같은
     *       대답을 또 하면 물은 게 없어 처리하지 않는다.
     */
    @Test
    fun `세차 추천에 그래 그러자면 예약하고 세차 입구에서 묻는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")
        say(runner, "그래"); runCurrent()

        said.clear(); noticed.clear()
        assertTrue(say(runner, "그래 그러자")); runCurrent()
        assertEquals(listOf("네, 세차 입구에 서시면 세차 모드 맞춰 드릴게요."), said)
        assertEquals(listOf("세차 예약했어요"), noticed)
        assertTrue(booking.atGasStation)
        assertNull(asking.question.value)
        assertFalse(say(runner, "그래 그러자"))

        said.clear(); noticed.clear()
        stopAtStationTwice()
        assertEquals(listOf("세차 입구에서 맞춰 드릴게요"), noticed)
        assertEquals(listOf("세차 모드 켤까요?"), said)
        assertEquals(RoutineId.WASH, runner.ui.value.suggestion?.id)
        assertEquals(QuestionKind.WASH_MODE, asking.question.value?.kind)
    }

    /**
     * 작성: 2026-11-12 11:58:00 · 박희정
     * 수정: 2026-11-12 18:36:00 · 박건준
     * 함수: 세차 추천을 거절하면 주유소에서 묻지 않는다
     * 목적: 세차 추천에 "아니 괜찮아"면 알았다고 하고 예약하지 않아, 주유소에
     *       두 번 서도 세차 모드를 묻지 않는지 본다.
     */
    @Test
    fun `세차 추천을 거절하면 주유소에서 묻지 않는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")
        say(runner, "그래")

        said.clear()
        assertTrue(say(runner, "아니 괜찮아"))
        assertEquals(listOf("알겠어요."), said)
        assertFalse(booking.atGasStation)
        assertNull(asking.question.value)

        stopAtStationTwice()
        assertNull(runner.ui.value.suggestion)
    }

    /**
     * 작성: 2026-11-12 18:36:00 · 박건준
     * 함수: 세차 모드를 거절하면 같은 길에서 다시 묻지 않는다
     * 목적: 세차 입구에서 "괜찮아"라고 하면, 권하기 간격(10분)이 지나 다시 서도
     *       묻지 않는지 본다(docs/SCREEN.md 3장 대화 규칙).
     */
    @Test
    fun `세차 모드를 거절하면 같은 길에서 다시 묻지 않는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "파라다이스 호텔 부산까지 길안내 해 줘")
        say(runner, "그래")
        say(runner, "그래 그러자")
        stopAtStationTwice()

        said.clear()
        assertTrue(say(runner, "괜찮아"))
        assertEquals(listOf("알겠어요."), said)
        assertNull(asking.question.value)

        advanceTimeBy(11 * 60_000L)
        said.clear()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 5f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f) }; runCurrent()
        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }
}
