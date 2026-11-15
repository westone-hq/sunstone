package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.dialog.WashBooking
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
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
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
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: WashScenarioTest
 * 목적: 시나리오 ①의 세차 장면(주유 정차 → 세차 입구 → 5초 뒤 켜기 → D로
 *       해제)을 가짜 차로 돌려, 하는 말·토스트·카드와 차 값 변화가
 *       docs/SCREEN.md 6장과 같은지 본다. 기대 문장은 6장 문장을 글자 그대로
 *       적는다.
 * 근거: 인용 — docs/SCREEN.md 6장 시나리오 ① 다시 짠 흐름 5~9
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WashScenarioTest {

    private companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: TODAY
         * 목적: 테스트의 오늘 날짜. 마지막 세차일 기록을 확인할 때 쓴다.
         */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 11)
    }

    /** 앱이 한 말, 말한 순서대로. 장면마다 비우고 그 장면 말만 본다 */
    private val said = mutableListOf<String>()

    /** 앱이 낸 토스트, 낸 순서대로 */
    private val noticed = mutableListOf<String>()

    /** 세차 예약. 시나리오 ①은 3단계에서 길안내 대화가 예약한 상태로 세차 장면을 시작한다 */
    private val booking = WashBooking().apply { reserve() }

    /** 마지막 세차일 기억 */
    private val memory = InMemoryMemory(lastWash = TODAY.minusDays(36))

    /** 질문·토스트 */
    private val asking = Asking()

    /** 지금 길안내 목적지. 길안내 대화와 차일드락이 함께 쓴다 */
    private val route = CurrentRoute()

    /** 선루프 두 개. Pleos 에뮬레이터 차처럼 지원하지 않게 둔다 */
    private val roofs = setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2))

    /** 지붕을 뺀 옆 창문 네 개 */
    private val sideWindows = Window.entries.filterNot { it == Window.ROOF_1 || it == Window.ROOF_2 }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-12 18:38:00 · 박건준
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지와 토스트 받기를
     *       시작한다.
     * 인자: car — 가짜 차
     * 반환: 실행기
     */
    private fun TestScope.runner(car: FakeCar): RoutineRunner {
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
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: answer
     * 목적: 말을 알아듣기에 넣고, 대답이면 실행기에 넘긴다. 앱의
     *       `AppGraph.handle()`가 하는 일과 같다.
     * 인자: runner — 실행기
     *       text — 운전자가 한 말
     * 반환: 권하기에 대한 대답으로 처리했으면 true
     */
    private fun answer(runner: RoutineRunner, text: String): Boolean {
        val action = PhraseMatcher.match(text) as VoiceAction.Answer // 알아들은 대답
        return runner.answer(action.reply)
    }

    /**
     * 작성: 2026-11-12 18:38:00 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: arriveAndMove
     * 목적: 달리다가 주유소에 들어와 P로 서고(주유 정차), 조금 움직였다가 다시
     *       P로 선다(세차 입구). 대사집 S5·S6 준비와 같다.
     * 인자: car — 가짜 차
     */
    private fun TestScope.arriveAndMove(car: FakeCar) {
        car.update { it.copy(place = Place.GAS_STATION, speedKmh = 0f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK) }; runCurrent()
        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 5f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f) }; runCurrent()
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 시나리오 1 - 주유 정차부터 세차 후 출발까지
     * 목적: docs/SCREEN.md 6장 5~9단계를 차례로 돌린다.
     *       1. 주유소에 처음 P로 서면 말 없이 토스트만 낸다
     *       2. 조금 움직였다 다시 서면 다시 묻지 않고 "곧 세차 모드 켤게요."와
     *          카드(할 일 칸 다섯)를 올린다
     *       3. 5초가 지나면 창문·와이퍼·내기 순환·문 잠금을 하고 짧게 한 번
     *          말한다. 칸에는 아이콘과 미러 "직접"이 붙는다
     *       4. D로 바꾸면(정차 중) 와이퍼·내기 순환만 되돌리고 판에 끝냈다고,
     *          말로 미러 확인을 알린다
     */
    @Test
    fun `시나리오 1 - 주유 정차부터 세차 후 출발까지`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 40f)).apply { unsupported = roofs }
        val runner = runner(car)
        runCurrent()

        car.update { it.copy(place = Place.GAS_STATION, speedKmh = 0f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK) }; runCurrent()
        assertTrue(said.isEmpty())
        assertEquals(listOf("세차 입구에서 맞춰 드릴게요"), noticed)
        assertNull(runner.ui.value.suggestion)

        car.update { it.copy(gear = Gear.DRIVE, speedKmh = 5f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f) }; runCurrent()
        assertEquals(listOf("곧 세차 모드 켤게요."), said)
        val q = asking.question.value!! // 올린 카드
        assertEquals(QuestionKind.WASH_MODE, q.kind)
        assertEquals(listOf("창문", "와이퍼", "내기 순환", "문 잠금", "미러"), q.rows.map { it.text })
        assertTrue(q.rows.last().manual)

        said.clear()
        advanceTimeBy(5_001); runCurrent()
        assertEquals(listOf("세차 모드 맞췄어요. 사이드미러는 직접 접어 주세요."), said)
        assertNull(asking.question.value)
        sideWindows.forEach { assertEquals(0, car.value(Target.WindowPosition(it))) }
        assertEquals(WiperMode.OFF, car.value(Target.Wiper))
        assertEquals(true, car.value(Target.Recirculation))
        assertEquals(true, car.value(Target.DoorLock))
        assertEquals(TODAY, memory.lastWashDate()) // 오늘 세차한 것으로 남긴다
        assertFalse(booking.atGasStation) // 예약은 썼으니 지운다
        val steps = runner.ui.value.runs.getValue(RoutineId.WASH).steps // 진행 줄
        assertTrue(steps.none { it.title == "선루프" }) // 이 차에 없는 선루프는 넣지 않는다
        assertTrue(steps.all { it.icon != null })
        assertEquals(StepStatus.GUIDED, steps.last().status)
        assertEquals("직접", steps.last().note)

        said.clear(); noticed.clear()
        car.update { it.copy(gear = Gear.DRIVE) }; runCurrent()
        assertEquals(listOf("사이드미러 펴졌는지 확인해 주세요."), said)
        assertEquals(listOf("세차 모드를 끝내고 원래대로 돌려놨어요"), noticed)
        assertEquals(WiperMode.AUTO, car.value(Target.Wiper))
        assertEquals(false, car.value(Target.Recirculation))
        assertEquals(0, car.value(Target.WindowPosition(Window.FRONT_LEFT))) // 창문은 다시 열지 않는다
        assertEquals(true, car.value(Target.DoorLock)) // 문 잠금은 풀지 않는다
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: 문이 열려 있으면 시작하지 않는다
     * 목적: 세차 모드 기능 카드 조건(문 닫힘)대로, 문이 하나라도 열려 있으면
     *       차를 바꾸지 않고 문을 닫아 달라고 하는지 본다.
     */
    @Test
    fun `문이 열려 있으면 시작하지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK, doorOpen = mapOf(Door.REAR_LEFT to true)))
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        assertEquals(listOf("문을 모두 닫으시면 시작할게요."), said)
        assertTrue(car.applied.isEmpty())
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:38:00 · 박건준
     * 함수: 세차를 예약하지 않은 주유소에서는 권하지 않는다
     * 목적: 주유만 하러 들른 주유소에서는 두 번 서도 세차를 묻지 않고 토스트도
     *       내지 않는지 본다.
     */
    @Test
    fun `세차를 예약하지 않은 주유소에서는 권하지 않는다`() = runTest {
        booking.clear()
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 20f))
        val runner = runner(car)
        runCurrent()

        arriveAndMove(car)

        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
        assertTrue(noticed.isEmpty())
    }

    /**
     * 작성: 2026-11-12 18:38:00 · 박건준
     * 함수: 주유 정차에서 움직이지 않으면 묻지 않는다
     * 목적: 주유 정차 뒤 P에서 N으로만 바꾸는 것처럼 움직이지 않았으면 세차
     *       입구로 보지 않는지 본다.
     */
    @Test
    fun `주유 정차에서 움직이지 않으면 묻지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 20f))
        val runner = runner(car)
        runCurrent()

        car.update { it.copy(place = Place.GAS_STATION, speedKmh = 0f, gear = Gear.PARK) }; runCurrent()
        car.update { it.copy(gear = Gear.NEUTRAL) }; runCurrent()

        assertNull(runner.ui.value.suggestion)
        assertTrue(said.isEmpty())
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 권하기를 거절하면 아무것도 바꾸지 않는다
     * 목적: 세차 입구에서 곧 켠다고 했을 때 "괜찮아"라고 하면 5초가 지나도 차를
     *       바꾸지 않고 알았다고만 말하며 카드를 내리는지 본다.
     */
    @Test
    fun `권하기를 거절하면 아무것도 바꾸지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 20f))
        val runner = runner(car)
        runCurrent()
        arriveAndMove(car)

        said.clear()
        assertTrue(answer(runner, "괜찮아")); runCurrent()
        advanceTimeBy(10_000); runCurrent()

        assertEquals(listOf("알겠어요."), said)
        assertTrue(car.applied.isEmpty())
        assertNull(runner.ui.value.suggestion)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 권하기가 없을 때 대답은 처리하지 않는다
     * 목적: 앱이 아무것도 권하지 않았는데 "응"이라고 하면 false를 돌려
     *       부르는 쪽이 "잘 못 알아들었어요"로 처리하게 하는지 본다.
     */
    @Test
    fun `권하기가 없을 때 대답은 처리하지 않는다`() = runTest {
        val runner = runner(FakeCar(CarState(gear = Gear.PARK)))
        runCurrent()

        assertFalse(answer(runner, "응"))
        assertTrue(said.isEmpty())
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-12 18:38:00 · 박건준
     * 함수: 앱이 못 한 단계는 직접 해 달라고 말한다
     * 목적: 와이퍼를 바꾸지 못하면 "와이퍼는 직접 꺼 주세요."를 덧붙이는지 본다.
     */
    @Test
    fun `앱이 못 한 단계는 직접 해 달라고 말한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { unsupported = roofs + Target.Wiper }
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        assertEquals(
            listOf("세차 모드 맞췄어요. 와이퍼는 직접 꺼 주세요. 사이드미러는 직접 접어 주세요."),
            said,
        )
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: 선루프가 있는 차는 선루프도 닫는다
     * 목적: 선루프를 지원하는 차에서는 선루프 단계가 들어가 닫히고, 말은 같은지
     *       본다.
     */
    @Test
    fun `선루프가 있는 차는 선루프도 닫는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { set(Target.WindowPosition(Window.ROOF_1), 5) }
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        assertEquals(listOf("세차 모드 맞췄어요. 사이드미러는 직접 접어 주세요."), said)
        assertEquals(0, car.value(Target.WindowPosition(Window.ROOF_1)))
        assertTrue(runner.ui.value.runs.getValue(RoutineId.WASH).steps.any { it.title == "선루프" })
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 세차장에서는 권하는 말이 다르고 N 유지를 안내한다
     * 목적: 세차장에 N으로 서면 세차장 문장으로 권하고, 실행하면 끝에 N 유지
     *       안내를 붙이는지 본다.
     */
    @Test
    fun `세차장에서는 권하는 말이 다르고 N 유지를 안내한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 5f)).apply { unsupported = roofs }
        val runner = runner(car)
        runCurrent()
        car.update { it.copy(gear = Gear.NEUTRAL, speedKmh = 0f, place = Place.CAR_WASH) }; runCurrent()
        assertEquals(listOf("세차장에 도착했어요. 세차 모드로 맞춰 드릴까요?"), said)

        said.clear()
        assertTrue(answer(runner, "네")); runCurrent()

        assertEquals(1, said.size)
        assertTrue(said.single().endsWith("시동을 끈 뒤 3분 안에는 운전석 문을 열지 마세요."))
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 세차 모드가 20분 넘게 켜져 있으면 한 번 묻는다
     * 목적: 세차 모드를 켜고 20분이 지나면 "세차 끝났나요?" [끝내기] [아직이에요]를
     *       한 번 묻고, "아직"이면 그대로 두고 다시 묻지 않으며, 그 뒤 다시 켜서
     *       [끝내기]면 원래대로 돌리는지 본다(docs/SPEC.md 5장).
     */
    @Test
    fun `세차 모드가 20분 넘게 켜져 있으면 한 번 묻는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { unsupported = roofs }
        val runner = runner(car)
        runner.request(RunRequest(RoutineId.WASH)); runCurrent()
        said.clear()

        advanceTimeBy(20 * 60_000L - 1); runCurrent()
        assertNull(asking.question.value)
        advanceTimeBy(2); runCurrent()

        val q = asking.question.value!! // 올린 질문
        assertEquals(QuestionKind.WASH_DONE, q.kind)
        assertEquals("세차 끝났나요?", q.title)
        assertEquals(listOf("끝내기", "아직이에요"), q.choices.map { it.label })
        assertEquals(listOf("세차 끝났나요?"), said)

        assertTrue(answer(runner, "아직이에요")); runCurrent()
        assertNull(asking.question.value)
        assertEquals(Phase.ACTIVE, runner.ui.value.runs.getValue(RoutineId.WASH).phase)
        advanceTimeBy(60 * 60_000L); runCurrent()
        assertNull(asking.question.value)

        runner.undo(RoutineId.WASH); runCurrent()
        runner.request(RunRequest(RoutineId.WASH)); runCurrent()
        advanceTimeBy(20 * 60_000L + 1); runCurrent()
        assertTrue(runner.answer(Reply.YES)); runCurrent()
        assertEquals(Phase.FINISHED, runner.ui.value.runs.getValue(RoutineId.WASH).phase)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 20분 안에 끝내면 묻지 않는다
     * 목적: 세차 모드를 20분 안에 끝냈으면 그 뒤 "세차 끝났나요?"를 묻지 않는지
     *       본다.
     */
    @Test
    fun `20분 안에 끝내면 묻지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { unsupported = roofs }
        val runner = runner(car)
        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        car.update { it.copy(gear = Gear.DRIVE) }; runCurrent()
        advanceTimeBy(30 * 60_000L); runCurrent()

        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: 자동 시작 5초 동안 말로 취소하면 켜지 않는다
     * 목적: 예약한 세차 입구에서 "곧 세차 모드 켤게요." 뒤 5초 안에 "아니"나
     *       "취소"라고 말하면 세차 모드를 켜지 않는지 본다(docs/SPEC.md 3장).
     */
    @Test
    fun `자동 시작 5초 동안 말로 취소하면 켜지 않는다`() = runTest {
        for (word in listOf("아니", "취소")) {
            booking.reserve()
            val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 20f)).apply { unsupported = roofs }
            val runner = runner(car)
            runCurrent()
            arriveAndMove(car)
            assertEquals("곧 세차 모드를 켤게요", asking.question.value?.title)

            advanceTimeBy(3_000)
            assertTrue(answer(runner, word)); runCurrent()
            advanceTimeBy(10_000); runCurrent()

            assertTrue(car.applied.isEmpty())
            assertNull(runner.ui.value.runs[RoutineId.WASH])
            asking.newTrip()
        }
    }
}
