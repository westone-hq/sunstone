package com.westone.sunstone.routines.engine

import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command

enum class RoutineId { WASH, CHILD_LOCK, SEAT_MEMORY }

/** 루틴을 누가 불렀나. 로그와 시연 화면에 쓴다. */
enum class Source { SCREEN, VOICE, GLEO, DETECTED, DEMO }

data class RunRequest(
    val id: RoutineId,
    /** 시트 메모리에서 누구 자세인지. 다른 루틴은 쓰지 않는다. */
    val profile: String? = null,
    val source: Source = Source.SCREEN,
)

/** 루틴 한 단계. 앱이 직접 바꾸거나, 할 수 없으면 말로 안내한다. */
sealed interface Step {
    val title: String

    /**
     * 앱이 차를 바꾸는 단계.
     * 실패하거나 지원하지 않으면 [fallback]을 말하고 다음 단계로 간다.
     * [restoreOnUndo]가 false면 되돌릴 때 건드리지 않는다(예: 창문은 다시 열지 않는다).
     */
    data class Control(
        override val title: String,
        val command: Command,
        val fallback: String,
        val restoreOnUndo: Boolean = true,
    ) : Step

    /** 앱이 할 수 없는 일. 말로 안내만 한다. */
    data class Guide(override val title: String, val say: String) : Step
}

/**
 * 미리 만들어 둔 루틴 하나.
 * 무엇을 할지([plan]), 언제 권할지([suggestion]), 언제 되돌릴지([shouldUndo])만 정한다.
 * 실행·저장·되돌리기는 [RoutineRunner]가 모든 루틴에 똑같이 한다.
 */
interface Routine {
    val id: RoutineId
    val title: String
    /** 시작할 때 하는 말. */
    val intro: String

    /** 지금 실행하면 안 되는 이유. 실행해도 되면 null. 단계마다 다시 확인한다. */
    fun blockedReason(state: CarState): String?

    suspend fun plan(request: RunRequest, car: CarPort): List<Step>

    /** 상황을 보고 먼저 권할 말. 권하지 않으면 null. */
    fun suggestion(before: CarState, now: CarState): String? = null

    /** 켜져 있는 동안 지켜보다가 알려줄 말(예: 뒷좌석 벨트 풀림). */
    fun watch(before: CarState, now: CarState): String? = null

    /** 저절로 되돌릴 때인가. [start]는 루틴을 시작한 순간의 상태. */
    fun shouldUndo(start: CarState, now: CarState): Boolean = false

    /** 되돌린 뒤 하는 말. */
    val afterUndo: List<String> get() = emptyList()

    /**
     * 실행 뒤에도 켜진 상태로 남는가(세차·차일드락처럼 "끝"이 있는 루틴).
     * false면 한 번 맞추고 끝나는 루틴이라 다시 부르면 새로 실행한다(되돌리기는 마지막 실행 기준).
     */
    val staysActive: Boolean get() = true
}
