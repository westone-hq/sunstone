package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.Target

/**
 * 차일드락(아이 탑승).
 *
 * 뒷문 차일드락 자체는 SDK에 없어서 확인 안내만 하고,
 * 대신 앱이 할 수 있는 것(뒷창 잠금, 문 잠금, 뒷유리 블라인드, 뒷좌석 공조)을 한 번에 켠다.
 * 주행 중 뒷좌석 벨트가 풀리면 알려주고, 아이가 내리면 원래대로 돌린다.
 */
class ChildLockRoutine : Routine {
    override val id = RoutineId.CHILD_LOCK
    override val title = "차일드락"
    override val intro = "아이 탈 준비할게요."

    override fun blockedReason(state: CarState): String? =
        if (!state.stopped) "차가 멈춘 뒤에 할게요." else null

    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> = listOf(
        Step.Control(
            "뒷창 잠금 (왼쪽)", Command(Target.WindowLock(Window.REAR_LEFT), true),
            "운전석 창문 잠금 버튼을 눌러 주세요.",
        ),
        Step.Control(
            "뒷창 잠금 (오른쪽)", Command(Target.WindowLock(Window.REAR_RIGHT), true),
            "운전석 창문 잠금 버튼을 눌러 주세요.",
        ),
        Step.Control("문 잠금", Command(Target.DoorLock, true), "문을 잠가 주세요.", restoreOnUndo = false),
        Step.Control("뒷유리 블라인드 올리기", Command(Target.RearBlind, true), "뒷유리 블라인드를 올려 주세요."),
        Step.Control("뒷좌석 공조 켜기", Command(Target.Climate(Seat.REAR_LEFT), true), "뒷좌석 공조를 켜 주세요."),
        Step.Guide("뒷문 차일드락 확인", "뒷문 차일드락이 켜져 있는지 확인해 주세요."),
    )

    override fun suggestion(before: CarState, now: CarState): String? {
        val boarded = before.anyRearDoorOpen && !now.anyRearDoorOpen && now.rearOccupied
        val kidsPlace = now.place == Place.KIDS
        return if (now.stopped && boarded && (kidsPlace || !before.rearOccupied)) {
            "아이가 탔나요? 차일드락 켤까요?"
        } else {
            null
        }
    }

    override fun watch(before: CarState, now: CarState): String? {
        if (now.stopped) return null
        val unbuckled = Seat.entries.firstOrNull { seat ->
            seat.isRear && now.occupied(seat) && before.buckled(seat) && !now.buckled(seat)
        }
        return unbuckled?.let { "${it.label} 안전벨트가 풀렸어요." }
    }

    override fun shouldUndo(start: CarState, now: CarState): Boolean =
        start.rearOccupied && now.stopped && !now.rearOccupied && !now.anyRearDoorOpen

    override val afterUndo = listOf("아이가 내려서 뒷창 잠금과 블라인드, 뒷좌석 공조를 원래대로 돌렸어요.")
}
