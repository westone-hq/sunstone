package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.InMemoryMemory
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.ChildLockLines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 클래스: ChildLockScenarioTest
 * 목적: 차일드락이 대사집 밖 경우에도 규칙대로 움직이는지 본다. 대사집 순서
 *       그대로는 `ScriptTest`가 본다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ②, CLAUDE.md 되돌리기 규칙(문 잠금은 풀지
 *       않는다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChildLockScenarioTest {

    private companion object {
        /**
         * 작성: 2026-11-12 16:10:00 · 박희정
         * 변수: TODAY
         * 목적: 테스트의 오늘 날짜.
         */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 12)

        /**
         * 작성: 2026-11-12 16:10:00 · 박희정
         * 변수: HOME
         * 목적: 집 목적지. 앱의 시연 장소 목록(places.json)과 같은 좌표.
         */
        val HOME = PlaceHit("집", GeoPoint(37.5007, 127.0365), Place.HOME)
    }

    /** 앱이 한 말, 말한 순서대로 */
    private val said = mutableListOf<String>()

    /** 지금 길안내 목적지 */
    private val route = CurrentRoute()

    /** 차일드락을 쓴 날 기억 */
    private val memory = InMemoryMemory()

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지를 시작한다.
     * 인자: car — 가짜 차
     * 반환: 실행기
     */
    private fun TestScope.runner(car: FakeCar) = RoutineRunner(
        car = car,
        routines = listOf(
            WashRoutine(WashBooking(), memory, { TODAY }),
            ChildLockRoutine(route, memory, { TODAY }),
            SeatMemoryRoutine(InMemorySeatProfiles()),
        ),
        speaker = { said += it },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.start() }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: board
     * 목적: 뒷좌석 왼쪽에 아이가 탄다. 문 열림 → 탑승·벨트 → 문 닫힘을 한
     *       단계씩 넣는다.
     * 인자: car — 가짜 차
     */
    private fun TestScope.board(car: FakeCar) {
        car.update { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to true)) }; runCurrent()
        car.update {
            it.copy(
                seatOccupied = it.seatOccupied + (Seat.REAR_LEFT to true),
                beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to true),
            )
        }; runCurrent()
        car.update { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to false)) }; runCurrent()
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: getOff
     * 목적: 뒷좌석 왼쪽 아이가 내린다. 문 열림 → 빈자리 → 문 닫힘.
     * 인자: car — 가짜 차
     */
    private fun TestScope.getOff(car: FakeCar) {
        car.update { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(seatOccupied = it.seatOccupied + (Seat.REAR_LEFT to false)) }; runCurrent()
        car.update { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to false)) }; runCurrent()
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 거절하면 아무것도 바꾸지 않는다
     * 목적: 권하기에 "아니"면 "알겠어요."만 하고 뒷창 잠금을 하지 않는지 본다.
     */
    @Test
    fun `거절하면 아무것도 바꾸지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        runCurrent()
        board(car)
        said.clear()

        assertTrue(runner.answer(false)); runCurrent()

        assertEquals(listOf("알겠어요."), said)
        assertTrue(car.applied.isEmpty())
        assertNull(memory.lastChildLockDate())
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 블라인드가 있는 차는 블라인드도 올렸다고 말한다
     * 목적: 블라인드를 지원하는 차면 "직접 올려 주세요" 대신 한 일에 넣는지
     *       본다(에뮬레이터에는 블라인드가 없다).
     */
    @Test
    fun `블라인드가 있는 차는 블라인드도 올렸다고 말한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        runCurrent()
        board(car)
        said.clear()

        runner.answer(true); runCurrent()

        assertEquals(
            listOf(
                "뒷창문 잠그고, 문 잠그고, 공조 켜고, 뒷유리 블라인드 올렸어요. " +
                    "뒷문 차일드락은 문 안쪽 스위치로 걸어 주세요.",
            ),
            said,
        )
        assertEquals(TODAY, memory.lastChildLockDate())
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 아이가 내리면 말없이 되돌리고 문 잠금은 그대로 둔다
     * 목적: 뒷창 잠금·공조·블라인드는 원래 값으로, 문 잠금은 잠근 채로 두는지
     *       본다. 원래 켜져 있던 공조는 켠 채로 둔다.
     */
    @Test
    fun `아이가 내리면 말없이 되돌리고 문 잠금은 그대로 둔다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { set(Target.Climate(Seat.REAR_LEFT), true) }
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(true); runCurrent()
        said.clear()

        getOff(car)

        assertEquals(emptyList<String>(), said)
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_RIGHT)))
        assertEquals(false, car.value(Target.RearBlind))
        assertEquals(true, car.value(Target.Climate(Seat.REAR_LEFT))) // 원래 켜져 있었다
        assertEquals(true, car.value(Target.DoorLock))
        assertEquals(Phase.FINISHED, runner.ui.value.runs[RoutineId.CHILD_LOCK]?.phase)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 문이 열려 있는 동안은 되돌리지 않는다
     * 목적: 아이가 내리는 중(뒷문 열림)에는 뒷창 잠금을 풀지 않는지 본다.
     */
    @Test
    fun `문이 열려 있는 동안은 되돌리지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(true); runCurrent()

        car.update { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(seatOccupied = it.seatOccupied + (Seat.REAR_LEFT to false)) }; runCurrent()

        assertEquals(true, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(Phase.ACTIVE, runner.ui.value.runs[RoutineId.CHILD_LOCK]?.phase)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 처음 타면 집으로 가도 이어서라고 하지 않는다
     * 목적: 오늘 차일드락을 쓴 적이 없으면 목적지가 집이어도 첫 권하기 문장인지
     *       본다.
     */
    @Test
    fun `처음 타면 집으로 가도 이어서라고 하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        runner(car)
        runCurrent()
        route.start(HOME)

        board(car)

        assertEquals(listOf(ChildLockLines.BOARDED), said)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 어제 썼으면 이어서라고 하지 않는다
     * 목적: "이어서"는 오늘 쓴 차일드락만 잇는지 본다.
     */
    @Test
    fun `어제 썼으면 이어서라고 하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        runner(car)
        runCurrent()
        memory.recordChildLock(TODAY.minusDays(1))
        route.start(HOME)

        board(car)

        assertEquals(listOf(ChildLockLines.BOARDED), said)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 벨트는 다시 맸다가 풀어야 또 알린다
     * 목적: 달리는 중 벨트가 풀리면 한 번 알리고, 풀린 채로 달리면 다시 말하지
     *       않고, 다시 맸다가 풀면 또 알리는지 본다. 멈춰 있을 때는 말하지 않는다.
     */
    @Test
    fun `벨트는 다시 맸다가 풀어야 또 알린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(true); runCurrent()
        said.clear()

        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to false)) }; runCurrent() // 정차 중
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 30f) }; runCurrent()
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to false)) }; runCurrent()
        car.update { it.copy(speedKmh = 40f) }; runCurrent()
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to false)) }; runCurrent()

        assertEquals(listOf(ChildLockLines.BELT_OFF, ChildLockLines.BELT_OFF), said)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: 달리는 중에는 차일드락을 하지 않는다
     * 목적: 달리는 중 "애 태웠어"라고 해도 차를 바꾸지 않고 멈춘 뒤에 한다고
     *       말하는지 본다.
     */
    @Test
    fun `달리는 중에는 차일드락을 하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 30f))
        val runner = runner(car)
        runCurrent()

        runner.request(RunRequest(RoutineId.CHILD_LOCK)); runCurrent()

        assertEquals(listOf(ChildLockLines.BLOCKED_MOVING), said)
        assertTrue(car.applied.isEmpty())
    }
}
