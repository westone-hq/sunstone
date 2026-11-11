package com.westone.sunstone.routines

import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
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

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 클래스: WashScenarioTest
 * 목적: Tab 1 4.3 시나리오 ①의 세차 장면(#3 주유소 도착 → #4 "응" → #5 세차 후
 *       출발)을 가짜 차로 돌려, 하는 말과 차 값 변화가 대사집과 같은지 본다.
 *       기대 문장은 대사집 문장을 글자 그대로 적는다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ① 표
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WashScenarioTest {

    /** 앱이 한 말, 말한 순서대로. 장면마다 비우고 그 장면 말만 본다 */
    private val said = mutableListOf<String>()

    /** 선루프 두 개. Pleos 에뮬레이터 차처럼 지원하지 않게 둔다 */
    private val roofs = setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2))

    /** 지붕을 뺀 옆 창문 네 개 */
    private val sideWindows = Window.entries.filterNot { it == Window.ROOF_1 || it == Window.ROOF_2 }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지를 시작한다.
     * 인자: car — 가짜 차
     * 반환: 실행기
     */
    private fun TestScope.runner(car: FakeCar) = RoutineRunner(
        car = car,
        routines = listOf(WashRoutine(), ChildLockRoutine(), SeatMemoryRoutine(InMemorySeatProfiles())),
        speaker = { said += it },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.start() }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: answer
     * 목적: 말을 알아듣기에 넣고, 대답이면 실행기에 넘긴다. 앱의
     *       `AppGraph.handle()`가 하는 일과 같다.
     * 인자: runner — 실행기
     *       text — 운전자가 한 말
     * 반환: 권하기에 대한 대답으로 처리했으면 true
     */
    private fun answer(runner: RoutineRunner, text: String): Boolean {
        val action = PhraseMatcher.match(text) as VoiceAction.Answer // 알아들은 대답
        return runner.answer(action.yes)
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 시나리오 1 - 주유소 도착부터 세차 후 출발까지
     * 목적: 대사집 #3~#5를 차례로 돌린다.
     *       1. 주행 중 주유소에 들어와 P로 서면 #3 문장으로 권한다
     *       2. "응"이면 창문·와이퍼·내기 순환·문 잠금을 하고 #4 한 문장만 말한다.
     *          선루프는 이 차에 없어 말하지 않는다
     *       3. 주유소를 벗어나 달리면 와이퍼·내기 순환만 되돌리고 #5 문장을 말한다
     */
    @Test
    fun `시나리오 1 - 주유소 도착부터 세차 후 출발까지`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 40f)).apply { unsupported = roofs }
        val runner = runner(car)
        runCurrent()

        car.update { it.copy(place = Place.GAS_STATION, speedKmh = 0f) }; runCurrent()
        car.update { it.copy(gear = Gear.PARK) }; runCurrent()
        assertEquals(listOf("주유소에 도착했어요. 세차하실 거면 세차 모드로 맞춰 드릴까요?"), said)

        said.clear()
        assertTrue(answer(runner, "응")); runCurrent()
        assertEquals(
            listOf("창문 닫고, 와이퍼 끄고, 내기 순환으로 바꾸고, 문 잠갔어요. 사이드미러는 직접 접어 주세요."),
            said,
        )
        sideWindows.forEach { assertEquals(0, car.value(Target.WindowPosition(it))) }
        assertEquals(WiperMode.OFF, car.value(Target.Wiper))
        assertEquals(true, car.value(Target.Recirculation))
        assertEquals(true, car.value(Target.DoorLock))

        said.clear()
        car.update { it.copy(gear = Gear.DRIVE) }; runCurrent()
        car.update { it.copy(speedKmh = 30f, place = Place.NONE) }; runCurrent()
        assertEquals(listOf("세차 모드 끝낼게요. 원래대로 돌려놨어요."), said)
        assertEquals(WiperMode.AUTO, car.value(Target.Wiper))
        assertEquals(false, car.value(Target.Recirculation))
        assertEquals(0, car.value(Target.WindowPosition(Window.FRONT_LEFT))) // 창문은 다시 열지 않는다
        assertEquals(true, car.value(Target.DoorLock)) // 문 잠금은 풀지 않는다
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 권하기를 거절하면 아무것도 바꾸지 않는다
     * 목적: 주유소에서 권했을 때 "괜찮아"라고 하면 차를 바꾸지 않고 알았다고만
     *       말하는지 본다.
     */
    @Test
    fun `권하기를 거절하면 아무것도 바꾸지 않는다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 20f))
        val runner = runner(car)
        runCurrent()
        car.update { it.copy(gear = Gear.PARK, speedKmh = 0f, place = Place.GAS_STATION) }; runCurrent()

        said.clear()
        assertTrue(answer(runner, "괜찮아")); runCurrent()

        assertEquals(listOf("알겠어요."), said)
        assertTrue(car.applied.isEmpty())
        assertNull(runner.ui.value.suggestion)
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
     * 함수: 앱이 못 한 단계는 직접 해 달라고 말한다
     * 목적: 와이퍼를 바꾸지 못하면 "와이퍼 끄고"를 빼고 "와이퍼는 직접 꺼
     *       주세요."를 덧붙이는지 본다.
     */
    @Test
    fun `앱이 못 한 단계는 직접 해 달라고 말한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)).apply { unsupported = roofs + Target.Wiper }
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        assertEquals(
            listOf(
                "창문 닫고, 내기 순환으로 바꾸고, 문 잠갔어요. " +
                    "와이퍼는 직접 꺼 주세요. 사이드미러는 직접 접어 주세요.",
            ),
            said,
        )
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 선루프가 있는 차는 선루프도 닫았다고 말한다
     * 목적: 선루프를 지원하는 차에서는 "선루프 닫고"가 들어가는지 본다.
     */
    @Test
    fun `선루프가 있는 차는 선루프도 닫았다고 말한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val runner = runner(car)

        runner.request(RunRequest(RoutineId.WASH)); runCurrent()

        assertEquals(
            listOf("창문 닫고, 선루프 닫고, 와이퍼 끄고, 내기 순환으로 바꾸고, 문 잠갔어요. 사이드미러는 직접 접어 주세요."),
            said,
        )
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
}
