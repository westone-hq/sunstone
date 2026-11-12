package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.engine.StepOutcome
import com.westone.sunstone.routines.engine.tally
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.MemoryPort
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.ChildLockLines
import com.westone.sunstone.routines.speech.ChildLockPart
import com.westone.sunstone.routines.speech.ReportLines
import java.time.LocalDate

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-12 16:10:00 · 박희정
 * 클래스: ChildLockRoutine
 * 목적: 차일드락(아이 탑승). 뒷문 차일드락은 앱이 걸 수 없어 안내하고, 대신
 *       앱이 할 수 있는 것(뒷창 잠금, 문 잠금, 공조, 뒷유리 블라인드)을 한 번에
 *       한다.
 *       - 정차 중 뒷좌석에 타고 뒷문이 닫히면 먼저 권한다. 오늘 차일드락을
 *         썼고 집으로 길안내 중이면 "이어서 할까요?"로 권한다
 *       - 다 하고 나서 한 번에 말한다. 하나라도 했으면 오늘을 남긴다
 *       - 달리는 중 뒷좌석 벨트가 풀리면 알린다
 *       - 정차 중 뒷좌석이 비고 뒷문이 닫히면(아이가 내리면) 말없이 원래대로
 *         돌린다. 문 잠금은 풀지 않는다
 * 인자: route — 지금 길안내 목적지. `TripDialog`와 같은 것을 넘긴다
 *       memory — 차일드락을 쓴 날을 남기고 읽는다
 *       today — 오늘 날짜. 테스트에서 고정한다
 * 근거: 인용 — Tab 1 4.3 시나리오 ② #1~#5. #4는 "도착해서 정차"에 풀지만,
 *       아이가 탄 채 뒷창 잠금이 풀리지 않게 2026-11-12 박희정이 "내린 뒤"로
 *       정했다(미정)
 */
class ChildLockRoutine(
    private val route: CurrentRoute,
    private val memory: MemoryPort,
    private val today: () -> LocalDate,
) : Routine {

    private companion object {
        /**
         * 작성: 2026-11-12 16:10:00 · 박희정
         * 변수: CHILD_LOCK_TITLE
         * 목적: 뒷문 차일드락 안내 단계 이름. 화면 체크리스트에 보인다.
         */
        const val CHILD_LOCK_TITLE = "뒷문 차일드락"
    }

    override val id = RoutineId.CHILD_LOCK
    override val title = "차일드락"
    override val intro = "아이 탈 준비할게요."
    override val reportsAtEnd = true

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: blockedReason
     * 목적: 달리는 중이면 하지 않는다. 단계마다 다시 본다.
     * 인자: state — 지금 차 상태
     * 반환: 이유 문장. 해도 되면 null
     * 근거: 인용 — CLAUDE.md "차를 바꾸는 일은 정차 중에만"
     */
    override fun blockedReason(state: CarState): String? =
        if (!state.stopped) ChildLockLines.BLOCKED_MOVING else null

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: plan
     * 목적: 차일드락 단계를 만든다. 뒷창 잠금(좌·우) → 문 잠금 → 공조 →
     *       뒷유리 블라인드 → 뒷문 차일드락 안내 순서.
     * 인자: request — 실행 요청. 쓰지 않는다
     *       car — 쓰지 않는다
     * 반환: 실행할 단계들
     * 근거: 측정 — 공조는 좌석별 전원이 없어 차 전체 공조가 켜진다. 블라인드는
     *       에뮬레이터에 없다(docs/api-spike.md 차일드락 공조·블라인드 행)
     */
    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> = listOf(
        control(ChildLockPart.REAR_WINDOW_LOCK, "뒷창 잠금 (왼쪽)", Target.WindowLock(Window.REAR_LEFT)),
        control(ChildLockPart.REAR_WINDOW_LOCK, "뒷창 잠금 (오른쪽)", Target.WindowLock(Window.REAR_RIGHT)),
        control(ChildLockPart.DOOR_LOCK, "문 잠금", Target.DoorLock, restoreOnUndo = false),
        control(ChildLockPart.CLIMATE, "공조 켜기", Target.Climate(Seat.REAR_LEFT)),
        control(ChildLockPart.REAR_BLIND, "뒷유리 블라인드 올리기", Target.RearBlind),
        Step.Guide(CHILD_LOCK_TITLE, ChildLockLines.DOOR_CHILD_LOCK),
    )

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: report
     * 목적: 다 하고 나서 한 번에 할 말을 만든다. 대상마다 모두 됐으면
     *       "했어요" 쪽, 하나라도 안 됐으면 "직접 해 주세요" 쪽에 넣고, 뒷문
     *       차일드락 안내를 끝에 붙인다. 앱이 하나라도 했으면 오늘을 남긴다.
     * 인자: outcomes — 단계마다의 결과
     * 반환: 말할 문장 하나
     * 근거: 측정 — 에뮬레이터에는 블라인드가 없어 "직접 올려 주세요"로 말한다.
     *       선루프와 달리 시연에서 블라인드 기능을 보여 주려고 2026-11-12
     *       박희정이 넣기로 정했다(미정)
     */
    override fun report(outcomes: List<StepOutcome>): List<String> {
        val parts = tally(outcomes, ::partOf) // → 로직 이동: tally()
        if (parts.done.isNotEmpty()) memory.recordChildLock(today()) // → 로직 이동: MemoryPort.recordChildLock()
        // → 로직 이동: ReportLines.report()
        return listOf(ReportLines.report(parts.done, parts.manual, parts.extra)).filter { it.isNotEmpty() }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: suggestion
     * 목적: 정차 중 뒷문이 닫히는 순간 뒷좌석에 사람이 있으면 차일드락을
     *       권한다. 오늘 차일드락을 썼고 집으로 길안내 중이면 "이어서" 말로
     *       권한다.
     * 인자: before — 바뀌기 전 차 상태
     *       now — 지금 차 상태
     * 반환: 권할 말. 권하지 않으면 null
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #1, #5. 대화 정책: 주행 중 새 제안 금지
     */
    override fun suggestion(before: CarState, now: CarState): String? {
        val boarded = now.stopped && before.anyRearDoorOpen && !now.anyRearDoorOpen && now.rearOccupied // 막 탔는지
        if (!boarded) return null
        val usedToday = memory.lastChildLockDate() == today() // 오늘 차일드락을 썼는지
        val goingHome = route.destination?.kind == Place.HOME // 집으로 길안내 중인지
        return if (usedToday && goingHome) ChildLockLines.CONTINUE_HOME else ChildLockLines.BOARDED
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: watch
     * 목적: 달리는 중 뒷좌석 벨트가 풀리는 순간 알린다. 다시 맸다가 풀어야
     *       또 알린다.
     * 인자: before — 바뀌기 전 차 상태
     *       now — 지금 차 상태
     * 반환: 알릴 말. 없으면 null
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #3
     */
    override fun watch(before: CarState, now: CarState): String? {
        if (now.stopped) return null
        val unbuckled = Seat.entries.any { it.isRear && before.buckled(it) && !now.buckled(it) } // 막 풀렸는지
        return if (unbuckled) ChildLockLines.BELT_OFF else null
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: shouldUndo
     * 목적: 탄 채로 시작했고, 지금 정차 중 뒷좌석이 비고 뒷문이 닫혔으면
     *       (아이가 내렸으면) 되돌린다.
     * 인자: start — 루틴을 시작한 순간의 상태
     *       now — 지금 차 상태
     * 반환: 되돌릴 때면 true
     */
    override fun shouldUndo(start: CarState, now: CarState): Boolean =
        start.rearOccupied && now.stopped && !now.rearOccupied && !now.anyRearDoorOpen

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: control
     * 목적: 제어 단계 하나를 만든다. 실패 안내는 대상의 부탁 말로 한다.
     * 인자: part — 대상
     *       title — 단계 이름(화면 체크리스트)
     *       target — 바꿀 대상. 값은 늘 true(잠금·켜기·올리기)
     *       restoreOnUndo — 되돌릴 때 원래 값으로 돌릴지
     * 반환: 제어 단계
     */
    private fun control(part: ChildLockPart, title: String, target: Target, restoreOnUndo: Boolean = true) =
        Step.Control(title, Command(target, true), ReportLines.please(part), restoreOnUndo)

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: partOf
     * 목적: 단계가 어느 대상인지 알아낸다. 뒷창 두 개를 하나로 묶는다.
     * 인자: step — 단계
     * 반환: 대상. 따로 말할 안내(뒷문 차일드락)면 null
     */
    private fun partOf(step: Step): ChildLockPart? = when (step) {
        is Step.Guide -> null
        is Step.Control -> when (step.command.target) {
            is Target.WindowLock -> ChildLockPart.REAR_WINDOW_LOCK
            Target.DoorLock -> ChildLockPart.DOOR_LOCK
            is Target.Climate -> ChildLockPart.CLIMATE
            Target.RearBlind -> ChildLockPart.REAR_BLIND
            else -> null
        }
    }
}
