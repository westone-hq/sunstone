package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.SeatProfile
import com.westone.sunstone.routines.port.SeatProfiles
import com.westone.sunstone.routines.port.Target

/**
 * 시트 메모리(운전자 교대).
 *
 * 차 메모리 버튼은 1~2개뿐이고 누가 몇 번인지 잊는다. 앱은 사람 이름으로 자세를 저장하고
 * "엄마 자세로" 한마디에 축별로 맞춘다. 저장된 위치가 없으면 차 메모리 번호를 부른다.
 * 시트는 P단에서만 움직인다. 실행 직전 자세를 저장해 두므로 "원래대로"가 된다.
 */
class SeatMemoryRoutine(private val profiles: SeatProfiles) : Routine {
    override val id = RoutineId.SEAT_MEMORY
    override val title = "시트 메모리"
    override val intro = "자세 맞출게요. 잠시만 기다려 주세요."

    override fun blockedReason(state: CarState): String? =
        if (state.gear != Gear.PARK) "P단에서만 시트를 움직일게요." else null

    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> {
        val name = request.profile ?: profiles.names().firstOrNull()
        val profile = name?.let(profiles::get)
            ?: return listOf(Step.Guide("저장된 자세 없음", "저장된 자세가 없어요. 먼저 '지금 자세 저장해줘'라고 말해 주세요."))

        if (profile.positions.isNotEmpty()) {
            return profile.positions.map { (axis, value) ->
                Step.Control(
                    title = "${profile.name} · ${axis.label}",
                    command = Command(Target.SeatPosition(Seat.DRIVER, axis), value),
                    fallback = "시트 ${axis.label}를 직접 맞춰 주세요.",
                )
            }
        }
        val slot = profile.memorySlot
            ?: return listOf(Step.Guide("저장된 자세 없음", "${profile.name} 자세가 비어 있어요. 다시 저장해 주세요."))
        return listOf(
            Step.Control(
                title = "${profile.name} · 메모리 ${slot}번",
                command = Command(Target.SeatMemory(Seat.DRIVER), slot),
                fallback = "시트 메모리 ${slot}번 버튼을 눌러 주세요.",
                restoreOnUndo = false,
            ),
        )
    }

    /** 지금 운전석 자세를 [name]으로 저장한다. 읽을 수 있는 축이 하나도 없으면 null. */
    suspend fun capture(name: String, car: CarPort, memorySlot: Int? = null): SeatProfile? {
        val positions = SeatAxis.entries.mapNotNull { axis ->
            (car.read(Target.SeatPosition(Seat.DRIVER, axis)) as? Int)?.let { axis to it }
        }.toMap()
        if (positions.isEmpty() && memorySlot == null) return null
        return SeatProfile(name, positions, memorySlot).also(profiles::save)
    }

    override fun suggestion(before: CarState, now: CarState): String? {
        val driverChanged = before.gear == Gear.PARK && now.gear == Gear.PARK &&
            !before.occupied(Seat.DRIVER) && now.occupied(Seat.DRIVER)
        return if (driverChanged && profiles.names().size >= 2) {
            "운전자가 바뀌었나요? 누구 자세로 맞출까요?"
        } else {
            null
        }
    }

    override val afterUndo = listOf("원래 자세로 돌렸어요.")

    override val staysActive = false
}
