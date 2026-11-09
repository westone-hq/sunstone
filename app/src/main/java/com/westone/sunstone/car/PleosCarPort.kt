package com.westone.sunstone.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.ControlResult
import com.westone.sunstone.routines.port.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * Pleos Vehicle SDK로 만든 [CarPort].
 *
 * - 차에서 오는 값(기어·문·좌석·벨트)은 리스너로 받아 [sensed]에 모은다.
 * - 차가 모르는 값(장소, 에뮬레이터에서 못 넣는 속도)은 [manual]로 앱이 넣는다.
 * - 제어는 "실행 → 다시 읽어 확인". 확인되면 Done, 읽을 수 없으면 Sent.
 *
 * 다른 팀 실측(2026-11): 창문 위치는 반영, 창문 잠금은 읽기값만 바뀜, 창문을 0으로 되돌리기 실패 사례 있음.
 * 우리 실측 결과는 docs/api-spike.md 에 남긴다.
 */
class PleosCarPort(context: Context, scope: CoroutineScope) : CarPort, AutoCloseable {

    private val vehicle = Vehicle(context.applicationContext).also { it.initialize() }
    private val sensed = MutableStateFlow(CarState())
    private val manual = MutableStateFlow(Manual())
    private val unsubscribe = mutableListOf<() -> Unit>()

    private data class Manual(val place: Place = Place.NONE, val speedKmh: Float? = null, val sensorSpeedKmh: Float = 0f)

    override val state: StateFlow<CarState> = combine(sensed, manual) { s, m ->
        s.copy(place = m.place, speedKmh = m.speedKmh ?: m.sensorSpeedKmh)
    }.stateIn(scope, SharingStarted.Eagerly, CarState())

    init {
        subscribeGear()
        Door.entries.forEach(::subscribeDoor)
        Seat.entries.filter { it != Seat.REAR_CENTER }.forEach(::subscribeOccupancy)
        Seat.entries.forEach(::subscribeBelt)
    }

    /** 장소 검색 결과(또는 데모 입력). */
    fun setPlace(place: Place) = manual.update { it.copy(place = place) }

    /** AAOS 속도 센서 값. */
    fun onSensorSpeed(kmh: Float) = manual.update { it.copy(sensorSpeedKmh = kmh) }

    /** 데모용 속도 덮어쓰기. null이면 센서 값으로 돌아간다. */
    fun overrideSpeed(kmh: Float?) = manual.update { it.copy(speedKmh = kmh) }

    // ------------------------------------------------------------------ 읽기 구독

    private fun subscribeGear() = guard("gear") {
        val gear = vehicle.getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(selection: VehicleGear?) {
                sensed.update { it.copy(gear = selection.toGear()) }
            }
            override fun onFailed(e: Exception) = warn("gear", e)
        }
        gear.getCurrentGearSelectionState({ g -> sensed.update { it.copy(gear = g.toGear()) } }, { warn("gear read", it) })
        gear.registerGearSelection(listener)
        unsubscribe += { gear.unregisterGearSelection(listener) }
    }

    private fun subscribeDoor(door: Door) = guard("door $door") {
        val api = vehicle.getDoor()
        val area = door.area()
        val update: (Int?) -> Unit = { pos ->
            sensed.update { it.copy(doorOpen = it.doorOpen + (door to ((pos ?: 0) > 0))) }
        }
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(value: Int?) = update(value)
            override fun onFailed(e: Exception) = warn("door $door", e)
        }
        api.getCurrentDoorPosition(area, update, { warn("door read $door", it) })
        api.registerDoorPosition(area, listener)
        unsubscribe += { api.unregisterDoorPosition(area, listener) }
    }

    private fun subscribeOccupancy(seat: Seat) = guard("occupancy $seat") {
        val api = vehicle.getSeat()
        val area = seat.area()
        val occupied: (Boolean?) -> Unit = { v ->
            val sitting = v != null && v != INVERT_OCCUPANCY
            sensed.update { it.copy(seatOccupied = it.seatOccupied + (seat to sitting)) }
        }
        val listener = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(value: Boolean?) = occupied(value)
            override fun onFailed(e: Exception) = warn("occupancy $seat", e)
        }
        api.getCurrentSeatOccupancy(area, occupied, { warn("occupancy read $seat", it) })
        api.registerSeatOccupancy(area, listener)
        unsubscribe += { api.unregisterSeatOccupancy(area, listener) }
    }

    private fun subscribeBelt(seat: Seat) = guard("belt $seat") {
        val api = vehicle.getSeat()
        val area = seat.area()
        val buckled: (Boolean?) -> Unit = { v ->
            sensed.update { it.copy(beltBuckled = it.beltBuckled + (seat to (v == true))) }
        }
        val listener = object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(value: Boolean?) = buckled(value)
            override fun onFailed(e: Exception) = warn("belt $seat", e)
        }
        api.getCurrentSeatBeltBuckledState(area, buckled, { warn("belt read $seat", it) })
        api.registerSeatBeltBuckledState(area, listener)
        unsubscribe += { api.unregisterSeatBeltBuckledState(area, listener) }
    }

    // ------------------------------------------------------------------ 읽기

    override suspend fun read(target: Target): Any? = runCatching {
        when (target) {
            is Target.WindowPosition -> awaitCallback<Int?> { ok, fail ->
                vehicle.getWindow().getCurrentWindowPosition(target.window.area(), ok, fail)
            }
            is Target.WindowLock -> awaitCallback<Boolean?> { ok, fail ->
                vehicle.getWindow().getCurrentWindowLockState(target.window.area(), ok, fail)
            }
            Target.Wiper -> awaitCallback<WiperSwitch?> { ok, fail ->
                vehicle.getWiper().getCurrentWiperSwitch(FRONT_WIPER, ok, fail)
            }?.toMode()
            Target.Recirculation -> awaitCallback<Boolean?> { ok, fail ->
                vehicle.getHVAC().getHvacRecirculationState(ALL_SEATS, ok, fail)
            }
            Target.DoorLock -> awaitCallback<Boolean?> { ok, fail ->
                vehicle.getDoor().getDoorLockState(ALL_DOORS, ok, fail)
            }
            Target.RearBlind -> awaitCallback<Int?> { ok, fail ->
                vehicle.getWindow().getCurrentWindowBlindPosition(REAR_GLASS, ok, fail)
            }?.let { it > 0 }
            is Target.Climate -> awaitCallback<Boolean?> { ok, fail ->
                vehicle.getHVAC().getHvacPowerState(ALL_SEATS, ok, fail)
            }
            is Target.SeatPosition -> awaitCallback<Int?> { ok, fail ->
                readSeat(target.axis, target.seat.area(), ok, fail)
            }
            is Target.SeatMemory -> null
        }
    }.onFailure { warn("read $target", it) }.getOrNull()

    // ------------------------------------------------------------------ 제어

    override suspend fun apply(command: Command): ControlResult = runCatching {
        val target = command.target
        val value = command.value
        when (target) {
            is Target.WindowPosition -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getWindow().setWindowPosition(target.window.area(), value as Int, { _, _ -> ok(Unit) }, fail)
                }
                verify(target, value, settleMs = 1_500)
            }
            is Target.WindowLock ->
                if (target.window !in LOCKABLE_WINDOWS) unsupported("setWindowLock ${target.window}")
                // 측정(docs/api-spike.md 창문 잠금 행): 다시 읽은 값만 바뀌고 콘솔은 그대로라 확인 못 한다.
                else {
                    awaitCallback<Unit> { ok, fail ->
                        vehicle.getWindow().setWindowLock(target.window.area(), value as Boolean, { _, _ -> ok(Unit) }, fail)
                    }
                    ControlResult.Sent
                }
            Target.Wiper -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getWiper().setWiperSwitch(FRONT_WIPER, (value as WiperMode).toSwitch(), { _, _ -> ok(Unit) }, fail)
                }
                verify(target, value)
            }
            Target.Recirculation -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getHVAC().setHvacRecirculation(ALL_SEATS, value as Boolean, { _, _ -> ok(Unit) }, fail)
                }
                verify(target, value)
            }
            Target.DoorLock -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getDoor().setDoorLock(ALL_DOORS, value as Boolean, { _, _ -> ok(Unit) }, fail)
                }
                verify(target, value)
            }
            Target.RearBlind ->
                if (!rearBlindSupported()) unsupported("moveWindowBlind REAR_WINDSHIELD")
                else {
                    val control = if (value as Boolean) MoveControl.FORWARD else MoveControl.BACKWARD
                    awaitCallback<Unit> { ok, fail ->
                        vehicle.getWindow().moveWindowBlind(REAR_GLASS, control, { _, _ -> ok(Unit) }, fail)
                    }
                    ControlResult.Sent
                }
            is Target.Climate -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getHVAC().setHvacPower(ALL_SEATS, value as Boolean, { _, _ -> ok(Unit) }, fail)
                }
                verify(target, value)
            }
            is Target.SeatPosition -> moveSeatTo(target, value as Int)
            is Target.SeatMemory -> {
                awaitCallback<Unit>(timeoutMs = 1_000) { _, fail ->
                    vehicle.getSeat().selectSeatMemory(target.seat.area(), value as Int, fail)
                }
                ControlResult.Sent
            }
        }
    }.getOrElse { e ->
        // selectSeatMemory는 성공 콜백이 없어서 시간 초과 = 실패 콜백이 안 옴 = 보냈음
        if (command.target is Target.SeatMemory && e is kotlinx.coroutines.TimeoutCancellationException) ControlResult.Sent
        else ControlResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** 다시 읽어서 원하는 값이 됐는지 본다. 읽을 수 없으면 Sent. */
    private suspend fun verify(target: Target, expected: Any, settleMs: Long = 400): ControlResult {
        delay(settleMs)
        val now = read(target) ?: return ControlResult.Sent
        // 읽은 값의 형태가 다르면(서명 미확정 API) 확인할 수 없는 것으로 본다.
        if (now.javaClass != expected.javaClass) return ControlResult.Sent
        return if (now == expected) ControlResult.Done
        else ControlResult.Failed("보냈지만 값이 $now 그대로예요(기대 $expected)")
    }

    /**
     * 시트는 "몇 번 위치로"가 없고 앞/뒤/정지(MoveControl)만 있다.
     * 위치를 읽어 가며 목표에 닿으면 STOP. 방향이 반대로 가면 한 번 뒤집는다.
     * 기어가 P가 아니게 되면 바로 멈춘다.
     */
    private suspend fun moveSeatTo(target: Target.SeatPosition, goal: Int): ControlResult {
        val area = target.seat.area()
        val axis = target.axis
        var pos = read(target) as? Int ?: return unsupported("seat ${target.axis} position")
        if (abs(pos - goal) <= SEAT_TOLERANCE) return ControlResult.Done
        var direction = if (goal > pos) MoveControl.FORWARD else MoveControl.BACKWARD
        var flipped = false
        moveSeat(axis, area, direction)
        val started = System.currentTimeMillis()
        try {
            while (System.currentTimeMillis() - started < SEAT_TIMEOUT_MS) {
                if (state.value.gear != Gear.PARK) return ControlResult.Failed("기어가 바뀌어서 멈췄어요")
                delay(SEAT_POLL_MS)
                val now = read(target) as? Int ?: break
                if (abs(now - goal) <= SEAT_TOLERANCE) return ControlResult.Done
                val gettingWorse = abs(now - goal) > abs(pos - goal)
                if (gettingWorse && !flipped) {
                    direction = if (direction == MoveControl.FORWARD) MoveControl.BACKWARD else MoveControl.FORWARD
                    flipped = true
                    moveSeat(axis, area, direction)
                }
                pos = now
            }
            return ControlResult.Failed("시간 안에 목표 위치에 못 갔어요")
        } finally {
            runCatching { moveSeat(axis, area, MoveControl.STOP) }
        }
    }

    /**
     * 차가 뒷유리 블라인드를 갖고 있는지 SDK에 묻는다. 에뮬레이터는 옆 창문 블라인드만 있다.
     * 묻기에 실패하면 없는 것으로 본다.
     */
    private suspend fun rearBlindSupported(): Boolean = runCatching {
        awaitCallback<Boolean> { ok, fail ->
            vehicle.getWindow().checkMoveWindowBlindCapabilities(
                { _, areas -> ok(areas.any { WindowValue.REAR_WINDSHIELD in it.values }) },
                fail,
            )
        }
    }.getOrDefault(false)

    override fun close() {
        unsubscribe.forEach { runCatching(it) }
        runCatching { vehicle.release() }
    }

    // ------------------------------------------------------------------ 변환

    private fun unsupported(what: String) = ControlResult.Unsupported("$what 없음")

    private inline fun guard(what: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { warn(what, e) }
    }

    private fun warn(what: String, e: Throwable) {
        Log.w(TAG, "$what: ${e.message}")
    }

    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }

    private fun WiperSwitch.toMode(): WiperMode = when {
        this == WiperSwitch.OFF -> WiperMode.OFF
        this == WiperSwitch.AUTO -> WiperMode.AUTO
        this == WiperSwitch.MIST -> WiperMode.MIST
        name.startsWith("INTERMITTENT") -> WiperMode.INTERMITTENT
        name.startsWith("CONTINUOUS") -> WiperMode.CONTINUOUS
        else -> WiperMode.UNKNOWN
    }

    private fun WiperMode.toSwitch(): WiperSwitch = when (this) {
        WiperMode.OFF -> WiperSwitch.OFF
        WiperMode.AUTO -> WiperSwitch.AUTO
        WiperMode.MIST -> WiperSwitch.MIST
        WiperMode.INTERMITTENT -> WiperSwitch.INTERMITTENT_LEVEL_1
        WiperMode.CONTINUOUS -> WiperSwitch.CONTINUOUS_LEVEL_1
        WiperMode.UNKNOWN -> WiperSwitch.OFF
    }

    private fun Window.area() = WindowArea(
        when (this) {
            Window.FRONT_LEFT -> WindowValue.ROW_1_LEFT
            Window.FRONT_RIGHT -> WindowValue.ROW_1_RIGHT
            Window.REAR_LEFT -> WindowValue.ROW_2_LEFT
            Window.REAR_RIGHT -> WindowValue.ROW_2_RIGHT
            Window.ROOF_1 -> WindowValue.ROOF_TOP_1
            Window.ROOF_2 -> WindowValue.ROOF_TOP_2
        },
    )

    private fun Door.area() = DoorArea(
        when (this) {
            Door.FRONT_LEFT -> DoorValue.ROW_1_LEFT
            Door.FRONT_RIGHT -> DoorValue.ROW_1_RIGHT
            Door.REAR_LEFT -> DoorValue.ROW_2_LEFT
            Door.REAR_RIGHT -> DoorValue.ROW_2_RIGHT
            Door.TRUNK -> DoorValue.REAR
        },
    )

    private fun Seat.area() = SeatArea(
        when (this) {
            Seat.DRIVER -> SeatValue.ROW_1_LEFT
            Seat.PASSENGER -> SeatValue.ROW_1_RIGHT
            Seat.REAR_LEFT -> SeatValue.ROW_2_LEFT
            Seat.REAR_CENTER -> SeatValue.ROW_2_CENTER
            Seat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT
        },
    )

    private fun readSeat(axis: SeatAxis, area: SeatArea, ok: (Int?) -> Unit, fail: (Exception) -> Unit) {
        val seat = vehicle.getSeat()
        when (axis) {
            SeatAxis.FORE_AFT -> seat.getCurrentSeatForeAftPosition(area, ok, fail)
            SeatAxis.HEIGHT -> seat.getCurrentSeatHeightPosition(area, ok, fail)
            SeatAxis.BACKREST_ANGLE -> seat.getCurrentSeatBackrestAnglePosition(area, ok, fail)
            SeatAxis.TILT -> seat.getCurrentSeatTiltPosition(area, ok, fail)
            SeatAxis.DEPTH -> seat.getCurrentSeatDepthPosition(area, ok, fail)
            SeatAxis.HEADREST_HEIGHT -> seat.getCurrentSeatHeadrestHeightAftPosition(area, ok, fail)
        }
    }

    private suspend fun moveSeat(axis: SeatAxis, area: SeatArea, control: MoveControl) = awaitCallback<Unit> { ok, fail ->
        val seat = vehicle.getSeat()
        val done: (SeatArea, MoveControl?) -> Unit = { _, _ -> ok(Unit) }
        when (axis) {
            SeatAxis.FORE_AFT -> seat.moveSeatForeAft(area, control, done, fail)
            SeatAxis.HEIGHT -> seat.moveSeatHeight(area, control, done, fail)
            SeatAxis.BACKREST_ANGLE -> seat.moveSeatBackrestAngle(area, control, done, fail)
            SeatAxis.TILT -> seat.moveSeatTilt(area, control, done, fail)
            SeatAxis.DEPTH -> seat.moveSeatDepth(area, control, done, fail)
            SeatAxis.HEADREST_HEIGHT -> seat.moveSeatHeadrestHeight(area, control, done, fail)
        }
    }

    private companion object {
        const val TAG = "SunstoneCar"
        const val SEAT_TOLERANCE = 1
        // 측정(docs/api-spike.md 탑승 행): SDK가 에뮬레이터 탑승 값을 반대로 읽는다. 실제 차는 미확인.
        const val INVERT_OCCUPANCY = true
        const val SEAT_POLL_MS = 250L
        const val SEAT_TIMEOUT_MS = 10_000L
        val FRONT_WIPER = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val REAR_GLASS = WindowArea(WindowValue.REAR_WINDSHIELD)
        // 측정(docs/api-spike.md 지원 area): 공조 전원·내기 순환은 다섯 좌석을 합친 area 하나만 받는다.
        val ALL_SEATS = SeatArea(
            SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT,
        )
        // 측정: 문 잠금은 네 문을 합친 area 하나(와 보닛)만 받는다. 문 전체 잠금으로 다룬다.
        val ALL_DOORS = DoorArea(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
        // 측정: 창문 잠금을 따로 받는 창문은 뒷좌석 둘뿐이다(앞좌석은 네 창문 합친 area로만).
        val LOCKABLE_WINDOWS = setOf(Window.REAR_LEFT, Window.REAR_RIGHT)
    }
}
