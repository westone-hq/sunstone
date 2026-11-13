package com.westone.sunstone.routines.engine

import com.westone.sunstone.routines.dialog.Question
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.ControlResult

enum class RoutineId { WASH, CHILD_LOCK, SEAT_MEMORY }

/** 루틴을 누가 불렀나. 로그와 시연 화면에 쓴다. */
enum class Source { SCREEN, VOICE, GLEO, DETECTED, DEMO }

data class RunRequest(
    val id: RoutineId,
    /** 시트 메모리에서 누구 자세인지. 다른 루틴은 쓰지 않는다. */
    val profile: String? = null,
    val source: Source = Source.SCREEN,
    /** 시트 메모리에서 맞출 좌석(운전석·조수석). 다른 루틴은 쓰지 않는다. */
    val seat: Seat = Seat.DRIVER,
)

/** 루틴 한 단계. 앱이 직접 바꾸거나, 할 수 없으면 말로 안내한다. */
sealed interface Step {
    val title: String

    /** 화면 칸 아이콘 이름(`QuestionRow.icon`과 같은 이름). 없으면 null */
    val icon: String?

    /**
     * 작성: 2026-11-12 18:12:00 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 클래스: Control
     * 목적: 앱이 차를 바꾸는 단계. 실패하거나 지원하지 않으면 [fallback]을
     *       말하고 다음 단계로 간다.
     * 인자: title — 단계 이름(화면 줄 글자)
     *       command — 차에 보낼 명령
     *       fallback — 실패하면 할 안내
     *       restoreOnUndo — false면 되돌릴 때 건드리지 않는다(창문은 다시 열지
     *       않는다)
     *       icon — 화면 칸 아이콘 이름. 없으면 null
     */
    data class Control(
        override val title: String,
        val command: Command,
        val fallback: String,
        val restoreOnUndo: Boolean = true,
        override val icon: String? = null,
    ) : Step

    /**
     * 작성: 2026-11-12 18:12:00 · 박건준
     * 클래스: Guide
     * 목적: 앱이 할 수 없는 일. 말로 안내만 한다.
     * 인자: title — 단계 이름(화면 줄 글자)
     *       say — 단계마다 말하는 루틴이면 할 안내
     *       icon — 화면 줄 아이콘 이름. 없으면 null
     *       hint — 화면 줄 오른쪽 짧은 안내("직접 접어 주세요"). 없으면 null
     */
    data class Guide(
        override val title: String,
        val say: String,
        override val icon: String? = null,
        val hint: String? = null,
    ) : Step
}

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 클래스: StepOutcome
 * 목적: 단계 하나를 처리한 결과. 끝에 한 번에 말하는 루틴([Routine.report])이
 *       무엇이 됐고 무엇을 사람이 해야 하는지 고를 때 쓴다.
 * 인자: step — 처리한 단계
 *       result — 제어 단계를 차에 보낸 결과. 안내 단계면 null
 */
data class StepOutcome(val step: Step, val result: ControlResult?)

/**
 * 미리 만들어 둔 루틴 하나.
 * 무엇을 할지([plan]), 언제 권할지([suggestion]), 언제 되돌릴지([shouldUndo])만 정한다.
 * 실행·저장·되돌리기는 [RoutineRunner]가 모든 루틴에 똑같이 한다.
 */
interface Routine {
    val id: RoutineId
    val title: String

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: runTitle
     * 목적: 이번 실행의 이름(화면 카드 "○○ 맞추는 중"). 요청마다 다르게 보일
     *       루틴(시트 메모리: "희정 님 자세로")이 바꾼다. 기본은 [title].
     * 인자: request — 실행 요청
     * 반환: 실행 이름
     */
    fun runTitle(request: RunRequest): String = title
    /** 시작할 때 하는 말. */
    val intro: String

    /** 지금 실행하면 안 되는 이유. 실행해도 되면 null. 단계마다 다시 확인한다. */
    fun blockedReason(state: CarState): String?

    suspend fun plan(request: RunRequest, car: CarPort): List<Step>

    /** 상황을 보고 먼저 권할 말. 권하지 않으면 null. */
    fun suggestion(before: CarState, now: CarState): String? = null

    /**
     * 작성: 2026-11-12 18:12:00 · 박건준
     * 함수: question
     * 목적: 권할 때 화면에 올릴 질문. [suggestion]이 말을 돌려준 뒤에 부른다.
     *       null이면 말만 한다.
     * 인자: now — 지금 차 상태
     * 반환: 질문. 없으면 null
     */
    fun question(now: CarState): Question? = null

    /** 켜져 있는 동안 지켜보다가 알려줄 말(예: 뒷좌석 벨트 풀림). */
    fun watch(before: CarState, now: CarState): String? = null

    /** 저절로 되돌릴 때인가. [start]는 루틴을 시작한 순간의 상태. */
    fun shouldUndo(start: CarState, now: CarState): Boolean = false

    /** 되돌린 뒤 하는 말. */
    val afterUndo: List<String> get() = emptyList()

    /**
     * 작성: 2026-11-13 12:00:00 · 박희정
     * 함수: autoUndoSay
     * 목적: 저절로 되돌렸을 때([shouldUndo]) 하는 말. null이면 [afterUndo]를
     *       말한다. 사람이 끝냈을 때와 말이 달라야 하는 루틴(차일드락: 도착해서
     *       풀었는지, [끝내기]로 끝냈는지)이 쓴다.
     */
    val autoUndoSay: List<String>? get() = null

    /**
     * 작성: 2026-11-12 18:12:00 · 박건준
     * 함수: undoNotice
     * 목적: 되돌린 뒤 토스트로 보일 문장. 없으면 null.
     */
    val undoNotice: String? get() = null

    /**
     * 실행 뒤에도 켜진 상태로 남는가(세차·차일드락처럼 "끝"이 있는 루틴).
     * false면 한 번 맞추고 끝나는 루틴이라 다시 부르면 새로 실행한다(되돌리기는 마지막 실행 기준).
     */
    val staysActive: Boolean get() = true

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: reportsAtEnd
     * 목적: true면 시작 말과 단계마다의 안내를 하지 않고, 모든 단계가 끝난 뒤
     *       [report]가 만든 말만 한다. 대사집처럼 한 번에 말해야 하는 루틴이 쓴다.
     */
    val reportsAtEnd: Boolean get() = false

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: report
     * 목적: 모든 단계가 끝난 뒤 할 말을 만든다. [reportsAtEnd]가 true일 때만
     *       부른다.
     * 인자: outcomes — 단계마다의 결과, 실행한 순서대로
     * 반환: 차례로 말할 문장들. 비어 있으면 말하지 않는다
     */
    fun report(outcomes: List<StepOutcome>): List<String> = emptyList()
}
