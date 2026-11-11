package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.TripDialog
import com.westone.sunstone.routines.dialog.WashBooking
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
import com.westone.sunstone.routines.model.RouteSummary
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.InMemoryMemory
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
 * 클래스: TripScenarioTest
 * 목적: 시나리오 ① 길안내 대화에서 대사집에 없는 경우(연료 넉넉, 거절, 최근
 *       세차, 비 소식 등)를 가짜 차·가짜 내비·고정 정보로 본다. 대사집 장면
 *       S1~S5 자체는 `ScriptTest`가 돌린다.
 *       기본 준비: P단, 연료 18%, 마지막 세차 36일 전
 * 근거: 인용 — Tab 1 4.3 시나리오 ① 표와 준비 줄
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
        val RESORT = PlaceHit("부산 OO리조트", GeoPoint(35.1587, 129.1604))

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: WASH_STATION
         * 목적: 세차 되는 주유소 중 가장 싼 곳. 시나리오가 경유지로 넣을 곳이다.
         */
        val WASH_STATION = GasStation(PlaceHit("OO주유소", GeoPoint(37.4846, 127.0340)), 1650, carWash = true)

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: CHEAP_STATION
         * 목적: 가장 싸지만 세차가 안 되는 주유소. 고르면 안 된다.
         */
        val CHEAP_STATION = GasStation(PlaceHit("△△주유소", GeoPoint(37.4900, 127.0300)), 1610, carWash = false)

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: PRICEY_WASH_STATION
         * 목적: 세차는 되지만 더 비싼 주유소. 고르면 안 된다.
         */
        val PRICEY_WASH_STATION = GasStation(PlaceHit("□□주유소", GeoPoint(37.4800, 127.0400)), 1690, carWash = true)
    }

    /** 앱이 한 말, 말한 순서대로. 장면마다 비우고 그 장면 말만 본다 */
    private val said = mutableListOf<String>()

    /** 선루프 두 개. Pleos 에뮬레이터 차처럼 지원하지 않게 둔다 */
    private val roofs = setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2))

    /** 준비 상태의 가짜 차: P단, 연료 18% */
    private val car = FakeCar(CarState(gear = Gear.PARK, fuelPercent = 18)).apply { unsupported = roofs }

    /** 가짜 내비. 목적지는 부산 OO리조트 하나, 경로에 통행료가 있다 */
    private val navi = FakeNavi(listOf(RESORT))

    /** 고정 정보: 주유소 세 곳, 일주일 비 소식 없음 */
    private val info = FakeInfo(listOf(CHEAP_STATION, PRICEY_WASH_STATION, WASH_STATION), rain = false)

    /** 마지막 세차 36일 전 */
    private val memory = InMemoryMemory(lastWash = TODAY.minusDays(36))

    /** 세차 예약. 길안내 대화와 세차 모드가 함께 쓴다 */
    private val booking = WashBooking()

    /** 길안내 대화 */
    private val trip = TripDialog(car, navi, info, memory, booking, { said += it }, { TODAY })

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지를 시작한다.
     * 반환: 실행기
     */
    private fun TestScope.runner() = RoutineRunner(
        car = car,
        routines = listOf(
            WashRoutine(booking, memory, { TODAY }),
            ChildLockRoutine(),
            SeatMemoryRoutine(InMemorySeatProfiles()),
        ),
        speaker = { said += it },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.start() }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: say
     * 목적: 운전자 말 하나를 앱처럼 처리한다. `AppGraph.handle()`과 같은 순서로
     *       길안내 → 길안내 대화의 대답 → 루틴 권하기의 대답을 본다.
     * 인자: runner — 실행기
     *       text — 운전자가 한 말
     * 반환: 처리했으면 true, 못 알아들었으면 false
     */
    private suspend fun say(runner: RoutineRunner, text: String): Boolean =
        when (val action = PhraseMatcher.match(text)) {
            is VoiceAction.Navigate -> { trip.navigate(action.destination); true }
            is VoiceAction.Answer -> trip.answer(action.yes) || runner.answer(action.yes)
            else -> false
        }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 연료가 넉넉하면 길안내만 하고 묻지 않는다
     * 목적: 연료가 기준보다 많으면 #1 앞 문장만 말하고, 뒤이은 "응"은
     *       대답할 것이 없어 처리하지 않는지 본다.
     */
    @Test
    fun `연료가 넉넉하면 길안내만 하고 묻지 않는다`() = runTest {
        car.update { it.copy(fuelPercent = 60) }
        val runner = runner()
        runCurrent()

        say(runner, "부산 OO리조트까지 길안내 해줘")

        assertEquals(listOf("네, 길안내 시작할게요."), said)
        assertFalse(say(runner, "응"))
        assertTrue(navi.waypoints.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 연료를 모르면 묻지 않는다
     * 목적: 연료 값을 읽을 수 없으면(전기차 에뮬레이터에 데모 fuel을 안 넣었을
     *       때) 주유를 권하지 않는지 본다.
     */
    @Test
    fun `연료를 모르면 묻지 않는다`() = runTest {
        car.update { it.copy(fuelPercent = null) }

        trip.navigate("부산 OO리조트")

        assertEquals(listOf("네, 길안내 시작할게요."), said)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 달리는 중에는 주유를 권하지 않는다
     * 목적: 주행 중 새 제안 금지 정책대로, 달리면서 길안내를 부탁하면 길안내만
     *       하는지 본다.
     */
    @Test
    fun `달리는 중에는 주유를 권하지 않는다`() = runTest {
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 50f) }

        trip.navigate("부산 OO리조트")

        assertEquals(listOf("네, 길안내 시작할게요."), said)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 고속도로가 없으면 가시는 길에 주유를 권한다
     * 목적: 경로 통행료가 0원이면 "고속도로 들어가기 전에" 대신 "가시는 길에"로
     *       말하는지 본다.
     */
    @Test
    fun `고속도로가 없으면 가시는 길에 주유를 권한다`() = runTest {
        navi.route = RouteSummary(distanceM = 12_000, durationSec = 1_500, tollWon = 0)

        trip.navigate("부산 OO리조트")

        assertEquals(
            listOf("네, 길안내 시작할게요. 연료가 부족해서 가시는 길에 주유하시는 게 좋겠어요. 근처 주유소 찾아 드릴까요?"),
            said,
        )
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 주유 제안을 거절하면 경유지를 넣지 않는다
     * 목적: #1 뒤에 "괜찮아"라고 하면 알았다고만 하고 경유지·세차 예약을 하지
     *       않는지 본다.
     */
    @Test
    fun `주유 제안을 거절하면 경유지를 넣지 않는다`() = runTest {
        val runner = runner()
        runCurrent()
        say(runner, "부산 OO리조트까지 길안내 해줘")

        said.clear()
        assertTrue(say(runner, "괜찮아"))

        assertEquals(listOf("알겠어요."), said)
        assertTrue(navi.waypoints.isEmpty())
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 최근에 세차했으면 추천도 예약도 하지 않는다
     * 목적: 마지막 세차가 3일 전이면 경유지만 넣고 세차를 추천하지 않으며,
     *       그 주유소에 도착해도 세차를 권하지 않는지 본다.
     */
    @Test
    fun `최근에 세차했으면 추천도 예약도 하지 않는다`() = runTest {
        memory.recordWash(TODAY.minusDays(3))
        val runner = runner()
        runCurrent()
        say(runner, "부산 OO리조트까지 길안내 해줘")

        said.clear()
        say(runner, "그래")
        assertEquals(listOf("가격이 괜찮고 세차도 되는 OO주유소를 경유지로 넣었어요. 세차 안 하신 지 3일 되셨습니다."), said)
        assertFalse(booking.atGasStation)

        said.clear()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 60f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f, place = Place.GAS_STATION) }; runCurrent()
        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 비 소식이 있으면 세차를 추천하지 않는다
     * 목적: 일주일 안에 비가 오면 날 수는 말하되 추천·예약은 하지 않는지 본다.
     */
    @Test
    fun `비 소식이 있으면 세차를 추천하지 않는다`() = runTest {
        info.rain = true
        trip.navigate("부산 OO리조트")

        said.clear()
        trip.answer(yes = true)

        assertEquals(listOf("가격이 괜찮고 세차도 되는 OO주유소를 경유지로 넣었어요. 세차 안 하신 지 36일 되셨습니다."), said)
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 세차 기록이 없으면 날 수 문장을 뺀다
     * 목적: 마지막 세차일을 모르면 "N일 되셨습니다"와 추천을 빼고 경유지만
     *       말하는지 본다(모르는 사실은 말에서 뺀다, Tab 1 2장).
     */
    @Test
    fun `세차 기록이 없으면 날 수 문장을 뺀다`() = runTest {
        val noMemory = InMemoryMemory(lastWash = null) // 세차 기록 없음
        val fresh = TripDialog(car, navi, info, noMemory, booking, { said += it }, { TODAY }) // 기록 없는 대화
        fresh.navigate("부산 OO리조트")

        said.clear()
        fresh.answer(yes = true)

        assertEquals(listOf("가격이 괜찮고 세차도 되는 OO주유소를 경유지로 넣었어요."), said)
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 세차 되는 주유소가 없으면 가장 싼 곳을 넣는다
     * 목적: 세차 되는 곳이 하나도 없으면 가장 싼 주유소를 넣고 세차 이야기를
     *       하지 않는지 본다.
     */
    @Test
    fun `세차 되는 주유소가 없으면 가장 싼 곳을 넣는다`() = runTest {
        info.stations = listOf(CHEAP_STATION)
        trip.navigate("부산 OO리조트")

        said.clear()
        trip.answer(yes = true)

        assertEquals(listOf("가격이 괜찮은 △△주유소를 경유지로 넣었어요."), said)
        assertEquals(listOf(CHEAP_STATION.place), navi.waypoints)
        assertFalse(booking.atGasStation)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 주유소를 못 찾으면 알린다
     * 목적: 근처 주유소가 없으면 경유지 없이 알리기만 하는지 본다.
     */
    @Test
    fun `주유소를 못 찾으면 알린다`() = runTest {
        info.stations = emptyList()
        trip.navigate("부산 OO리조트")

        said.clear()
        trip.answer(yes = true)

        assertEquals(listOf("근처에 주유소를 찾지 못했어요."), said)
        assertTrue(navi.waypoints.isEmpty())
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

        trip.navigate("부산 OO리조트")

        assertEquals(listOf("길안내를 시작하지 못했어요. 다시 말해 주세요."), said)
        assertFalse(trip.answer(yes = true))
    }
}
