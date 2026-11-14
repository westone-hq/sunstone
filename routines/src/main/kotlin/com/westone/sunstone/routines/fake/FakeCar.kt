package com.westone.sunstone.routines.fake

import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.ControlResult
import com.westone.sunstone.routines.port.Target
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 메모리 속 가짜 차. 단위 테스트와 차 없는 시연에 쓴다.
 * [unsupported]에 넣은 대상은 "지원 안 함"으로 답해서, 안내로 넘어가는 흐름을 확인할 수 있다.
 */
class FakeCar(initial: CarState = CarState(gear = Gear.PARK)) : CarPort {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<CarState> = _state.asStateFlow()

    private val values = mutableMapOf<Target, Any>()
    var unsupported: Set<Target> = emptySet()
    val applied = mutableListOf<Command>()

    fun update(block: (CarState) -> CarState) {
        _state.value = block(_state.value)
    }

    fun set(target: Target, value: Any) {
        values[target] = value
    }

    fun value(target: Target): Any? = values[target] ?: defaultOf(target)

    override suspend fun read(target: Target): Any? =
        if (target in unsupported) null else value(target)

    /**
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: apply
     * 목적: 값을 바꾼다. 시트 조금 움직이기(`SeatNudge`)는 그 좌석·축의 위치에
     *       더한다(0~100으로 자른다).
     */
    override suspend fun apply(command: Command): ControlResult {
        if (command.target in unsupported) return ControlResult.Unsupported("가짜 차에서 지원 안 함")
        applied += command
        val target = command.target // 바꿀 대상
        if (target is Target.SeatNudge) {
            val at = Target.SeatPosition(target.seat, target.axis) // 움직일 위치 칸
            values[at] = ((value(at) as Int) + command.value as Int).coerceIn(0, 100)
            return ControlResult.Done
        }
        values[target] = command.value
        return ControlResult.Done
    }

    private fun defaultOf(target: Target): Any? = when (target) {
        is Target.WindowPosition -> if (target.window == Window.ROOF_1 || target.window == Window.ROOF_2) 0 else 3
        is Target.WindowLock -> false
        Target.Wiper -> WiperMode.AUTO
        Target.Recirculation -> false
        Target.DoorLock -> false
        Target.RearBlind -> false
        is Target.Climate -> false
        is Target.SeatPosition -> 50
        is Target.SeatMemory -> null
        is Target.SeatNudge -> null
    }
}
