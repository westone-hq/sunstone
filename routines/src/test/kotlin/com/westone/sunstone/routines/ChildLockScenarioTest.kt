package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.Choice
import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.Question
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.Reply
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
import kotlinx.coroutines.launch
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
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: ChildLockScenarioTest
 * 목적: 차일드락이 대사집 밖 경우에도 규칙대로 움직이는지 본다. 대사집 순서
 *       그대로는 `ScriptTest`가 본다.
 * 근거: 인용 — docs/SCREEN.md 4장 ② 표와 "정한 것", CLAUDE.md 되돌리기
 *       규칙(문 잠금은 풀지 않는다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChildLockScenarioTest {

    private companion object {
        /**
         * 작성: 2026-11-12 16:10:00 · 박희정
         * 변수: TODAY
         * 목적: 테스트의 오늘 날짜.
         */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 13)

        /**
         * 작성: 2026-11-12 16:10:00 · 박희정
         * 수정: 2026-11-13 10:00:00 · 박희정
         * 변수: HOME, KIDS
         * 목적: 집·유치원 목적지. 앱의 시연 장소 목록(places.json)과 같은 좌표.
         */
        val HOME = PlaceHit("집", GeoPoint(37.5007, 127.0365), Place.HOME)
        val KIDS = PlaceHit("OO유치원", GeoPoint(37.5058, 127.0446), Place.KIDS)
    }

    /** 앱이 한 말, 말한 순서대로 */
    private val said = mutableListOf<String>()

    /** 앱이 낸 토스트, 낸 순서대로 */
    private val noticed = mutableListOf<String>()

    /** 지금 길안내 목적지 */
    private val route = CurrentRoute()

    /** 차일드락을 쓴 날 기억 */
    private val memory = InMemoryMemory()

    /** 질문·토스트 */
    private val asking = Asking()

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 11:00:00 · 박희정
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지와 토스트(경고
     *       토스트 포함) 받기를 시작한다.
     * 인자: car — 가짜 차
     * 반환: 실행기
     */
    private fun TestScope.runner(car: FakeCar): RoutineRunner {
        backgroundScope.launch { asking.notices.collect { noticed += it } }
        backgroundScope.launch { asking.alerts.collect { noticed += it } } // 경고 토스트도 토스트로 본다
        return RoutineRunner(
            car = car,
            routines = listOf(
                WashRoutine(WashBooking(), memory, asking, { TODAY }),
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
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: board
     * 목적: 뒷자리 왼쪽에 아이가 탄다. 문 열림 → 탑승·벨트 → 문 닫힘을 한
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
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: drive
     * 목적: 목적지를 정하고 달렸다가 그 장소에 도착해 P로 선다.
     * 인자: car — 가짜 차
     *       to — 목적지
     */
    private fun TestScope.drive(car: FakeCar, to: PlaceHit) {
        route.start(to)
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 40f, place = Place.NONE) }; runCurrent()
        car.update { it.copy(place = to.kind, speedKmh = 0f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK) }; runCurrent()
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 거절하면 아무것도 바꾸지 않는다
     * 목적: 카드에 "괜찮아"면 "알겠어요."만 하고 차를 바꾸지 않는지 본다.
     */
    @Test
    fun `거절하면 아무것도 바꾸지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.HOME))
        val runner = runner(car)
        runCurrent()
        board(car)
        assertEquals(QuestionKind.CHILD_LOCK, asking.question.value?.kind)
        said.clear()

        assertTrue(runner.answer(Reply.NO)); runCurrent()

        assertEquals(listOf("알겠어요."), said)
        assertTrue(car.applied.isEmpty())
        assertNull(asking.question.value)
        assertNull(memory.lastChildLockDate())
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 블라인드가 있는 차는 블라인드도 올린다
     * 목적: 블라인드를 읽을 수 있는 차면 블라인드 단계를 넣고, 말은 그대로
     *       짧게 하는지 본다(에뮬레이터에는 블라인드가 없다).
     */
    @Test
    fun `블라인드가 있는 차는 블라인드도 올린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.HOME))
        val runner = runner(car)
        runCurrent()
        board(car)
        said.clear()

        runner.answer(Reply.YES); runCurrent()

        assertEquals(listOf("뒷창문 잠갔어요. 뒷문 차일드락은 문 안쪽 스위치로 걸어 주세요."), said)
        assertEquals(true, car.value(Target.RearBlind))
        val steps = runner.ui.value.runs[RoutineId.CHILD_LOCK]?.steps.orEmpty().map { it.title } // 단계 이름
        assertTrue(ChildLockLines.STEP_BLIND in steps)
        assertEquals(TODAY, memory.lastChildLockDate())
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 도착하면 되돌리고 문 잠금은 그대로 둔다
     * 목적: 목적지에 도착해 P로 서면 뒷창 잠금은 원래 값으로, 문 잠금은 잠근
     *       채로, 원래 켜져 있던 공조는 켠 채로 두고 말과 토스트로 알리는지 본다.
     */
    @Test
    fun `도착하면 되돌리고 문 잠금은 그대로 둔다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.HOME))
            .apply { set(Target.Climate(Seat.REAR_LEFT), true) }
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(Reply.YES); runCurrent()
        said.clear()

        drive(car, KIDS)

        assertEquals(listOf(ChildLockLines.ARRIVED), said)
        assertEquals(listOf(ChildLockLines.UNLOCKED_NOTICE), noticed)
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_RIGHT)))
        assertEquals(true, car.value(Target.Climate(Seat.REAR_LEFT))) // 원래 켜져 있었다
        assertEquals(true, car.value(Target.DoorLock))
        assertEquals(Phase.FINISHED, runner.ui.value.runs[RoutineId.CHILD_LOCK]?.phase)
    }

    /**
     * 작성: 2026-11-13 12:00:00 · 박희정
     * 함수: 끝내기로 끝내면 도착했다고 말하지 않는다
     * 목적: [끝내기](사람이 끝냄)로 되돌리면 "차일드락 끝낼게요."라고 하고, 뒷창
     *       잠금을 풀고 실행을 끝내는지 본다. 도착 토스트는 같다.
     */
    @Test
    fun `끝내기로 끝내면 도착했다고 말하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.HOME))
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(Reply.YES); runCurrent()
        said.clear()

        runner.undo(RoutineId.CHILD_LOCK); runCurrent()

        assertEquals(listOf(ChildLockLines.FINISHED), said)
        assertEquals(false, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(Phase.FINISHED, runner.ui.value.runs[RoutineId.CHILD_LOCK]?.phase)
    }

    /**
     * 작성: 2026-11-13 12:30:00 · 박희정
     * 함수: 다른 질문이 떠 있으면 묻지 않는다
     * 목적: 길안내 대화가 주유를 묻는 중이면, 오늘 썼고 뒷자리에 누가 있어도
     *       차일드락을 묻지 않는지 본다. 질문은 한 번에 하나다.
     */
    @Test
    fun `다른 질문이 떠 있으면 묻지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, seatOccupied = mapOf(Seat.REAR_LEFT to true)))
        val runner = runner(car)
        runCurrent()
        memory.recordChildLock(TODAY)
        asking.ask(
            Question(QuestionKind.FUEL_STOP, "주유할까요?", "", choices = listOf(Choice("넣기", Reply.YES))),
        )

        route.start(HOME)
        runner.recheck(); runCurrent()

        assertEquals(emptyList<String>(), said)
        assertEquals(QuestionKind.FUEL_STOP, asking.question.value?.kind)
    }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: 목적지가 아닌 곳에 서면 되돌리지 않는다
     * 목적: 가는 길에 신호나 다른 곳에서 P로 서도 뒷창 잠금을 풀지 않는지 본다.
     */
    @Test
    fun `목적지가 아닌 곳에 서면 되돌리지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.HOME))
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(Reply.YES); runCurrent()
        route.start(KIDS)

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 40f, place = Place.NONE) }; runCurrent()
        car.update { it.copy(place = Place.GAS_STATION, speedKmh = 0f, gear = Gear.PARK) }; runCurrent()

        assertEquals(true, car.value(Target.WindowLock(Window.REAR_LEFT)))
        assertEquals(Phase.ACTIVE, runner.ui.value.runs[RoutineId.CHILD_LOCK]?.phase)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 처음 타면 집으로 가도 아침처럼이라 하지 않는다
     * 목적: 오늘 차일드락을 쓴 적이 없으면 목적지가 집이어도 처음 문장인지 본다.
     */
    @Test
    fun `처음 타면 집으로 가도 아침처럼이라 하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.KIDS))
        runner(car)
        runCurrent()
        route.start(HOME)

        board(car)

        assertEquals(listOf(ChildLockLines.BOARDED), said)
        assertEquals(QuestionKind.CHILD_LOCK, asking.question.value?.kind)
    }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: 오늘 썼으면 탈 때는 묻지 않고 집으로 길안내하면 묻는다
     * 목적: 하원길처럼 오늘 썼으면 타는 순간에는 조용하고, 집으로 길안내를
     *       시작하면(`recheck()`) "아침처럼"으로 묻는지 본다. 다른 목적지면 처음
     *       문장으로 묻는다.
     */
    @Test
    fun `오늘 썼으면 탈 때는 묻지 않고 집으로 길안내하면 묻는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.KIDS))
        val runner = runner(car)
        runCurrent()
        memory.recordChildLock(TODAY)

        board(car)
        assertEquals(emptyList<String>(), said)

        route.start(HOME)
        runner.recheck(); runCurrent()

        assertEquals(listOf(ChildLockLines.AGAIN), said)
        assertEquals(QuestionKind.CHILD_LOCK_AGAIN, asking.question.value?.kind)

        runner.answer(Reply.YES); runCurrent()
        assertEquals(ChildLockLines.LOCKED, said.last()) // 오늘 두 번째라 차일드락 안내는 하지 않는다
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 어제 썼으면 탈 때 바로 묻는다
     * 목적: "아침처럼"은 오늘 쓴 차일드락만 잇는지 본다.
     */
    @Test
    fun `어제 썼으면 탈 때 바로 묻는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.KIDS))
        runner(car)
        runCurrent()
        memory.recordChildLock(TODAY.minusDays(1))
        route.start(HOME)

        board(car)

        assertEquals(listOf(ChildLockLines.BOARDED), said)
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 벨트는 다시 맸다가 풀어야 또 알린다
     * 목적: 달리는 중 벨트가 풀리면 말과 토스트로 한 번 알리고, 풀린 채로
     *       달리면 다시 말하지 않고, 다시 맸다가 풀면 또 알리는지 본다. 멈춰
     *       있을 때는 말하지 않는다.
     */
    @Test
    fun `벨트는 다시 맸다가 풀어야 또 알린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, place = Place.HOME))
        val runner = runner(car)
        runCurrent()
        board(car)
        runner.answer(Reply.YES); runCurrent()
        said.clear()

        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to false)) }; runCurrent() // 정차 중
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 30f) }; runCurrent()
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to false)) }; runCurrent()
        car.update { it.copy(speedKmh = 40f) }; runCurrent()
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to true)) }; runCurrent()
        car.update { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to false)) }; runCurrent()

        assertEquals(listOf(ChildLockLines.BELT_OFF, ChildLockLines.BELT_OFF), said)
        assertEquals(listOf(ChildLockLines.BELT_OFF, ChildLockLines.BELT_OFF), noticed)
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
