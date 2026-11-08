package com.westone.sunstone.routines

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
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun TestScope.runner(car: FakeCar) = RoutineRunner(
        car = car,
        routines = listOf(WashRoutine(), ChildLockRoutine(), seatRoutine),
        speaker = { said += it },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.start() }

    @Test
    fun `세차 - 창문 닫고 와이퍼 끄고, 떠나면 와이퍼만 원래대로`() = runTest {
        val car = FakeCar(CarState(gear = Gear.NEUTRAL, place = Place.CAR_WASH))
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); advanceUntilIdle()

        Window.entries.forEach { assertEquals(0, car.value(Target.WindowPosition(it))) }
        assertEquals(WiperMode.OFF, car.value(Target.Wiper))
        assertEquals(true, car.value(Target.Recirculation))
        assertEquals(Phase.ACTIVE, runner.ui.value.runs[RoutineId.WASH]?.phase)

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 30f, place = Place.NONE) }; advanceUntilIdle()

        assertEquals(WiperMode.AUTO, car.value(Target.Wiper))
        assertEquals(false, car.value(Target.Recirculation))
        assertEquals(0, car.value(Target.WindowPosition(Window.FRONT_LEFT))) // 창문은 다시 열지 않는다
        assertEquals(Phase.FINISHED, runner.ui.value.runs[RoutineId.WASH]?.phase)
    }

    @Test
    fun `세차 - 달리는 중에는 시작하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 40f))
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); advanceUntilIdle()

        assertNull(runner.ui.value.runs[RoutineId.WASH])
        assertTrue(said.last().contains("멈춘 뒤"))
    }

    @Test
    fun `지원하지 않는 기능은 안내로 넘어가고 루틴은 끝까지 간다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { unsupported = setOf(Target.Wiper) }
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); advanceUntilIdle()

        val run = runner.ui.value.runs.getValue(RoutineId.WASH)
        assertEquals(StepStatus.GUIDED, run.steps.first { it.title == "와이퍼 끄기" }.status)
        assertTrue(said.any { it.contains("와이퍼 레버") })
        assertEquals(Phase.ACTIVE, run.phase)
    }

    @Test
    fun `세차장에서 N에 두면 먼저 권한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 5f))
        val runner = runner(car)
        advanceUntilIdle()

        car.update { it.copy(gear = Gear.NEUTRAL, speedKmh = 0f, place = Place.CAR_WASH) }; advanceUntilIdle()

        assertEquals(RoutineId.WASH, runner.ui.value.suggestion?.id)
        runner.acceptSuggestion(); advanceUntilIdle()
        assertEquals(WiperMode.OFF, car.value(Target.Wiper))
    }

    @Test
    fun `차일드락 - 아이가 타면 권하고, 내리면 되돌린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        advanceUntilIdle()

        car.update { it.copy(doorOpen = mapOf(Door.REAR_LEFT to true)) }; advanceUntilIdle()
        car.update { it.copy(doorOpen = emptyMap(), seatOccupied = mapOf(Seat.REAR_LEFT to true)) }; advanceUntilIdle()
        assertEquals(RoutineId.CHILD_LOCK, runner.ui.value.suggestion?.id)

        runner.acceptSuggestion(); advanceUntilIdle()
        assertEquals(true, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(true, car.value(Target.RearBlind))

        car.update { it.copy(seatOccupied = emptyMap()) }; advanceUntilIdle()
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(false, car.value(Target.RearBlind))
    }

    @Test
    fun `차일드락 - 달리는 중 뒷좌석 벨트가 풀리면 알린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, seatOccupied = mapOf(Seat.REAR_RIGHT to true), beltBuckled = mapOf(Seat.REAR_RIGHT to true)))
        val runner = runner(car)
        runner.request(RunRequest(RoutineId.CHILD_LOCK)); advanceUntilIdle()

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 50f) }; advanceUntilIdle()
        car.update { it.copy(beltBuckled = emptyMap()) }; advanceUntilIdle()

        assertTrue(said.any { it.contains("안전벨트가 풀렸어요") })
    }

    @Test
    fun `시트 메모리 - 저장한 자세로 맞추고 원래대로 돌린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        car.set(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT), 30)
        assertNotNull(seatRoutine.capture("엄마", car))

        car.set(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT), 80)
        runner.request(RunRequest(RoutineId.SEAT_MEMORY, profile = "엄마")); advanceUntilIdle()
        assertEquals(30, car.value(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT)))

        runner.undo(RoutineId.SEAT_MEMORY); advanceUntilIdle()
        assertEquals(80, car.value(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT)))
    }

    @Test
    fun `시트 메모리 - P단이 아니면 움직이지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE))
        val runner = runner(car)
        seatRoutine.capture("아빠", car)

        runner.request(RunRequest(RoutineId.SEAT_MEMORY, profile = "아빠")); advanceUntilIdle()

        assertTrue(car.applied.isEmpty())
        assertTrue(said.last().contains("P단"))
    }
}
