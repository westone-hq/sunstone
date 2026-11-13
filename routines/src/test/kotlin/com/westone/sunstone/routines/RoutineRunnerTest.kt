package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.StepStatus
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.port.InMemoryMemory
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import java.time.LocalDate
import com.westone.sunstone.routines.port.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineRunnerTest {

    private val said = mutableListOf<String>()
    private val profiles = InMemorySeatProfiles()
    private val seatRoutine = SeatMemoryRoutine(profiles)

    /** 지금 길안내 목적지. 차일드락이 도착을 볼 때 쓴다 */
    private val route = CurrentRoute()

    private fun TestScope.runner(car: FakeCar) = RoutineRunner(
        car = car,
        routines = listOf(
            WashRoutine(WashBooking(), InMemoryMemory(), Asking(), { LocalDate.of(2026, 11, 11) }),
            ChildLockRoutine(route, InMemoryMemory(), Asking(), { LocalDate.of(2026, 11, 11) }),
            seatRoutine,
        ),
        speaker = { said += it },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.start() }

    /**
     * 수정: 2026-11-09 14:29:54 · 박건준
     * 함수: 세차 - 창문 닫고 와이퍼 끄고, 떠나면 와이퍼만 원래대로
     * 목적: 세차 모드가 창문·와이퍼·내기 순환을 바꾸고, 세차장을 떠나면
     *       와이퍼·내기 순환만 원래 값으로 돌리고 창문은 다시 열지 않는지 본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `세차 - 창문 닫고 와이퍼 끄고, 떠나면 와이퍼만 원래대로`() = runTest {
        val car = FakeCar(CarState(gear = Gear.NEUTRAL, place = Place.CAR_WASH))
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        Window.entries.forEach { assertEquals(0, car.value(Target.WindowPosition(it))) }
        assertEquals(WiperMode.OFF, car.value(Target.Wiper))
        assertEquals(true, car.value(Target.Recirculation))
        assertEquals(Phase.ACTIVE, runner.ui.value.runs[RoutineId.WASH]?.phase)

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 30f, place = Place.NONE) }; runCurrent()

        assertEquals(WiperMode.AUTO, car.value(Target.Wiper))
        assertEquals(false, car.value(Target.Recirculation))
        assertEquals(0, car.value(Target.WindowPosition(Window.FRONT_LEFT))) // 창문은 다시 열지 않는다
        assertEquals(Phase.FINISHED, runner.ui.value.runs[RoutineId.WASH]?.phase)
    }

    /**
     * 수정: 2026-11-09 14:29:54 · 박건준
     * 함수: 세차 - 달리는 중에는 시작하지 않는다
     * 목적: 달리는 중에 세차 모드를 요청하면 시작하지 않고, 멈춘 뒤에 하라고
     *       말하는지 본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `세차 - 달리는 중에는 시작하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 40f))
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        assertNull(runner.ui.value.runs[RoutineId.WASH])
        assertTrue(said.last().contains("멈춘 뒤"))
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 지원하지 않는 기능은 안내로 넘어가고 루틴은 끝까지 간다
     * 목적: 차가 와이퍼를 지원하지 않으면 그 단계만 음성 안내로 바뀌고 루틴은
     *       끝까지 가는지 본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `지원하지 않는 기능은 안내로 넘어가고 루틴은 끝까지 간다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { unsupported = setOf(Target.Wiper) }
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        val run = runner.ui.value.runs.getValue(RoutineId.WASH)
        assertEquals(StepStatus.GUIDED, run.steps.first { it.title == "와이퍼" }.status)
        assertTrue(said.any { it.contains("와이퍼는 직접 꺼 주세요.") })
        assertEquals(Phase.ACTIVE, run.phase)
    }

    /**
     * 수정: 2026-11-09 14:29:54 · 박건준
     * 함수: 세차장에서 N에 두면 먼저 권한다
     * 목적: 세차장에서 N단에 두면 세차 모드를 먼저 권하고, 받아들이면 실행하는지
     *       본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `세차장에서 N에 두면 먼저 권한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 5f))
        val runner = runner(car)
        runCurrent()

        car.update { it.copy(gear = Gear.NEUTRAL, speedKmh = 0f, place = Place.CAR_WASH) }; runCurrent()

        assertEquals(RoutineId.WASH, runner.ui.value.suggestion?.id)
        runner.acceptSuggestion(); runCurrent()
        assertEquals(WiperMode.OFF, car.value(Target.Wiper))
    }

    /**
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 차일드락 - 아이가 타면 권하고, 도착하면 되돌린다
     * 목적: 뒷문이 열렸다 닫히고 뒷좌석에 사람이 앉으면 차일드락을 권하고,
     *       길안내 목적지에 도착해 P로 서면 창문 잠금·블라인드를 원래 값으로
     *       돌리는지 본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `차일드락 - 아이가 타면 권하고, 도착하면 되돌린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        runCurrent()

        car.update { it.copy(doorOpen = mapOf(Door.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(doorOpen = emptyMap(), seatOccupied = mapOf(Seat.REAR_LEFT to true)) }; runCurrent()
        assertEquals(RoutineId.CHILD_LOCK, runner.ui.value.suggestion?.id)

        runner.acceptSuggestion(); runCurrent()
        assertEquals(true, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(true, car.value(Target.RearBlind))

        route.start(PlaceHit("OO유치원", GeoPoint(37.5058, 127.0446), Place.KIDS))
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 40f) }; runCurrent()
        car.update { it.copy(place = Place.KIDS, speedKmh = 0f, gear = Gear.PARK) }; runCurrent()
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(false, car.value(Target.RearBlind))
    }

    /**
     * 수정: 2026-11-09 14:29:54 · 박건준
     * 함수: 차일드락 - 달리는 중 뒷좌석 벨트가 풀리면 알린다
     * 목적: 차일드락이 켜진 채 달리다가 뒷좌석 안전벨트가 풀리면 알리는지 본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `차일드락 - 달리는 중 뒷좌석 벨트가 풀리면 알린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, seatOccupied = mapOf(Seat.REAR_RIGHT to true), beltBuckled = mapOf(Seat.REAR_RIGHT to true)))
        val runner = runner(car)
        runner.request(RunRequest(RoutineId.CHILD_LOCK)); runCurrent()

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 50f) }; runCurrent()
        car.update { it.copy(beltBuckled = emptyMap()) }; runCurrent()

        assertTrue(said.any { it.contains("안전벨트가 풀렸어요") })
    }

    /**
     * 수정: 2026-11-09 14:29:54 · 박건준
     * 함수: 시트 메모리 - 저장한 자세로 맞추고 원래대로 돌린다
     * 목적: 저장한 자세로 운전석을 옮기고, 되돌리면 옮기기 전 자세로 돌아가는지
     *       본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `시트 메모리 - 저장한 자세로 맞추고 원래대로 돌린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        car.set(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT), 30)
        assertNotNull(seatRoutine.capture("엄마", car))

        car.set(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT), 80)
        runner.request(RunRequest(RoutineId.SEAT_MEMORY, profile = "엄마")); runCurrent()
        assertEquals(30, car.value(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT)))

        runner.undo(RoutineId.SEAT_MEMORY); runCurrent()
        assertEquals(80, car.value(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT)))
    }

    /**
     * 수정: 2026-11-09 14:29:54 · 박건준
     * 함수: 시트 메모리 - P단이 아니면 움직이지 않는다
     * 목적: P단이 아니면 시트를 움직이지 않고 P단에서 하라고 말하는지 본다.
     * 근거: 표준 — kotlinx-coroutines-test 1.8.1에서 `backgroundScope` 작업만
     *       남으면 `advanceUntilIdle()`이 바로 돌아오므로 `runCurrent()`를 쓴다
     */
    @Test
    fun `시트 메모리 - P단이 아니면 움직이지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE))
        val runner = runner(car)
        seatRoutine.capture("아빠", car)

        runner.request(RunRequest(RoutineId.SEAT_MEMORY, profile = "아빠")); runCurrent()

        assertTrue(car.applied.isEmpty())
        assertTrue(said.last().contains("P단"))
    }
}
