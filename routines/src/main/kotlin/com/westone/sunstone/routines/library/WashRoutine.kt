package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.Target

/**
 * 세차 모드.
 *
 * 현대 세차 모드가 하는 일(창문·선루프 닫기, 와이퍼 AUTO 해제, 내기 전환, 문 잠금, 미러 접기)을
 * 앱이 한 단계씩 직접 한다. 미러 접기와 N 유지는 API가 없어서 말로 안내한다.
 * 세차장을 벗어나 달리기 시작하면 와이퍼·내외기를 원래대로 돌린다. 창문은 다시 열지 않는다.
 */
class WashRoutine : Routine {
    override val id = RoutineId.WASH
    override val title = "세차 모드"
    override val intro = "세차 준비할게요."

    override fun blockedReason(state: CarState): String? = when {
        !state.stopped -> "차가 멈춘 뒤에 할게요."
        state.gear != Gear.PARK && state.gear != Gear.NEUTRAL -> "P나 N에 두시면 시작할게요."
        else -> null
    }

    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> {
        val windows = Window.entries.map { w ->
            Step.Control(
                title = "${w.label} 닫기",
                command = Command(Target.WindowPosition(w), CLOSED),
                fallback = "${w.label}을 닫아 주세요.",
                restoreOnUndo = false,
            )
        }
        return windows + listOf(
            Step.Control("와이퍼 끄기", Command(Target.Wiper, WiperMode.OFF), "와이퍼 레버를 OFF로 내려 주세요."),
            Step.Control("내기 순환", Command(Target.Recirculation, true), "내기 순환 버튼을 눌러 주세요."),
            Step.Control("문 잠금", Command(Target.DoorLock, true), "문을 잠가 주세요.", restoreOnUndo = false),
            Step.Guide("사이드미러 접기", "사이드미러 접기 버튼을 눌러 주세요."),
            Step.Guide(
                "자동 세차기라면 N 유지",
                "자동 세차기라면 N에서 스티어링 휠 버튼을 길게 눌러 N 유지를 켜 주세요. 시동을 끈 뒤 3분 안에는 운전석 문을 열지 마세요.",
            ),
        )
    }

    override fun suggestion(before: CarState, now: CarState): String? {
        val atWash = now.place == Place.CAR_WASH || now.place == Place.GAS_STATION
        val ready = now.stopped && (now.gear == Gear.NEUTRAL || now.gear == Gear.PARK)
        val wasReady = before.place == now.place && before.stopped && before.gear == now.gear
        return if (atWash && ready && !wasReady) "세차하시나요? 준비해 드릴까요?" else null
    }

    override fun shouldUndo(start: CarState, now: CarState): Boolean =
        now.gear == Gear.DRIVE && now.speedKmh >= LEAVE_SPEED_KMH &&
            now.place != Place.CAR_WASH && now.place != Place.GAS_STATION

    override val afterUndo = listOf(
        "와이퍼와 공조를 원래대로 돌렸어요.",
        "사이드미러를 펴 주세요. 브레이크가 젖었을 수 있으니 처음 몇 번은 살살 밟아 주세요.",
    )

    private companion object {
        const val CLOSED = 0
        const val LEAVE_SPEED_KMH = 10f
    }
}
