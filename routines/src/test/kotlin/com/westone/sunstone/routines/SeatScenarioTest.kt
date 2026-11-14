package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.dialog.SeatDialog
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.SeatLines
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 작성: 2026-11-13 15:00:00 · 박희정
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: SeatScenarioTest
 * 목적: 시트 맞추기가 대사집 밖 경우에도 규칙대로 움직이는지 본다. 대사집 순서
 *       그대로는 `ScriptTest`가 본다.
 * 근거: 인용 — docs/SCREEN.md 4장 ③ 규칙, 프로젝트 규칙 "시트는 P단에서만"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeatScenarioTest {

    /** 앱이 한 말, 말한 순서대로 */
    private val said = mutableListOf<String>()

    /** 사람별 자세 */
    private val profiles = InMemorySeatProfiles()

    /** 질문·토스트 */
    private val asking = Asking()

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: seat
     * 목적: 시트 메모리 루틴을 가진 실행기와 시트 대화를 만든다.
     * 인자: car — 가짜 차
     * 반환: 시트 대화
     */
    private fun TestScope.seat(car: FakeCar): SeatDialog {
        val runner = RoutineRunner( // 실행기
            car = car,
            routines = listOf(SeatMemoryRoutine(profiles, asking)),
            speaker = { said += it },
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            asking = asking,
        ).also { it.start() }
        return SeatDialog(car, profiles, asking, { said += it }) { runner.request(it) }
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: 달리는 중이면 시트를 맞추지 않는다
     * 목적: 주행 중 "시트 좀 맞춰 줘"면 "차가 멈춘 뒤에 할게요."만 하고 카드를
     *       띄우지 않는지 본다.
     */
    @Test
    fun `달리는 중이면 시트를 맞추지 않는다`() = runTest {
        val dialog = seat(FakeCar(CarState(gear = Gear.DRIVE, speedKmh = 30f)))
        runCurrent()

        dialog.start(Seat.DRIVER); runCurrent()

        assertEquals(listOf(SeatLines.BLOCKED_MOVING), said)
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: 원래대로면 움직인 만큼 되돌린다
     * 목적: 앞으로·등받이를 움직인 뒤 [원래대로]면 처음 자리로 돌아가고 카드가
     *       내려가는지 본다.
     */
    @Test
    fun `원래대로면 움직인 만큼 되돌린다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK)) // 처음 위치는 축마다 50
        val dialog = seat(car)
        runCurrent()
        dialog.start(Seat.DRIVER)
        dialog.adjust(SeatAxis.FORE_AFT, 10)
        dialog.adjust(SeatAxis.BACKREST_ANGLE, -30)
        said.clear()

        assertTrue(dialog.answer(Reply.NO)); runCurrent()

        assertEquals(listOf(SeatLines.RESTORED), said)
        assertEquals(50, car.value(Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT)))
        assertEquals(50, car.value(Target.SeatPosition(Seat.DRIVER, SeatAxis.BACKREST_ANGLE)))
        assertNull(asking.question.value)
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: 처음 보는 이름이면 같이 맞추고 그 이름으로 기억한다
     * 목적: 저장되지 않은 이름이면 같이 맞출지 묻고, 예면 그 좌석을 맞춘 뒤 "됐어"에
     *       이름을 다시 묻지 않고 바로 그 이름으로 저장하는지 본다.
     */
    @Test
    fun `처음 보는 이름이면 같이 맞추고 그 이름으로 기억한다`() = runTest {
        val car = FakeCar(CarState(gear = Gear.PARK))
        val dialog = seat(car)
        runCurrent()

        dialog.recall("민수", Seat.PASSENGER); runCurrent()
        assertEquals(listOf(SeatLines.unknown("민수")), said)
        assertEquals(QuestionKind.SEAT_NEW_PERSON, asking.question.value?.kind)

        dialog.answer(Reply.YES)
        assertEquals(SeatLines.adjustTitle(Seat.PASSENGER), asking.question.value?.title)
        dialog.adjust(SeatAxis.HEIGHT, 10)
        said.clear()
        dialog.answer(Reply.YES); runCurrent()

        assertEquals(listOf(SeatLines.saved("민수")), said)
        assertEquals(60, profiles.get("민수")?.positions?.get(SeatAxis.HEIGHT))
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: 기억 안 함이면 저장하지 않는다
     * 목적: 이름 카드에서 [기억 안 함]이면 저장하지 않고 이름 기다리기를 끝내는지
     *       본다.
     */
    @Test
    fun `기억 안 함이면 저장하지 않는다`() = runTest {
        val dialog = seat(FakeCar(CarState(gear = Gear.PARK)))
        runCurrent()
        dialog.start(Seat.DRIVER)
        dialog.answer(Reply.YES)
        assertTrue(dialog.awaitingName)
        said.clear()

        dialog.answer(Reply.NO)

        assertEquals(listOf(SeatLines.SKIPPED), said)
        assertTrue(profiles.names().isEmpty())
        assertTrue(!dialog.active)
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: 시트 말을 알아듣는다
     * 목적: 대사집 밖 변형도 시트 요청으로 알아듣는지 본다.
     */
    @Test
    fun `시트 말을 알아듣는다`() {
        assertEquals(VoiceAction.SeatAdjust(SeatAxis.FORE_AFT, -30), PhraseMatcher.match("뒤로 많이"))
        assertEquals(VoiceAction.SeatAdjust(SeatAxis.HEIGHT, 10), PhraseMatcher.match("시트 좀 올려 줘"))
        assertEquals(VoiceAction.SeatAdjust(SeatAxis.BACKREST_ANGLE, -10), PhraseMatcher.match("등받이 눕혀 줘"))
        assertEquals(VoiceAction.SeatStart(Seat.PASSENGER), PhraseMatcher.match("조수석 시트 맞춰 줘"))
        assertEquals(VoiceAction.SeatRecall("민수", Seat.PASSENGER), PhraseMatcher.match("옆자리에 민수가 앉았어"))
    }
}
