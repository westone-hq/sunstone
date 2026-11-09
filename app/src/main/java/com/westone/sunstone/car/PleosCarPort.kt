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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:50:00 · 박건준
 * 클래스: PleosCarPort
 * 목적: Pleos Vehicle SDK로 만든 `CarPort`(기능 구현이 차에 요청하는 창구).
 *       - 차에서 오는 값(기어·문·탑승·벨트)은 리스너로 받아 `sensed`에 모은다
 *       - 차가 모르는 값(장소, 에뮬레이터에서 못 넣는 속도)은 앱이 `manual`에
 *         넣는다
 *       - 제어는 "실행 → 다시 읽어 확인". 확인되면 `Done`, 확인할 수 없으면
 *         `Sent`, 차가 받지 않는 대상이면 `Unsupported`
 * 인자: context — 앱 Context. SDK 연결에 쓴다
 *       scope — `state`를 계속 계산할 범위. 앱이 살아 있는 동안 유지된다
 * 근거: 측정 — 대상별 결과는 docs/api-spike.md 표에 있다
 */
class PleosCarPort(context: Context, scope: CoroutineScope) : CarPort, AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: TAG
         * 목적: 차 관련 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: SEAT_TOLERANCE
         * 목적: 시트가 목표 위치에 닿았다고 보는 차이(차가 주는 위치 단위).
         * 근거: 미정 — 위치 단위를 확인하기 전 임시 값
         */
        const val SEAT_TOLERANCE = 1

        /**
         * 작성: 2026-11-09 18:36:19 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: INVERT_OCCUPANCY
         * 목적: 탑승 값 반전. true면 SDK가 준 탑승 값을 뒤집어 쓴다.
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0에서 운전석·조수석·뒷좌석 좌우
         *       네 자리 모두 콘솔 탑승이 앱 빈자리로 읽혔다(docs/api-spike.md
         *       탑승 행). 에뮬레이터는 AAOS 기준 1=빈자리, 2=탑승을 보내는데
         *       SDK 2.3.2는 1을 탑승으로 바꾼다
         * 미확인: 실제 차에서도 반대로 읽히는지 확인하지 못했다. 실제 차에서
         *       맞게 읽히면 false로 바꾼다
         */
        const val INVERT_OCCUPANCY = true

        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: SEAT_POLL_MS
         * 목적: 시트를 움직이는 동안 위치를 다시 읽는 간격(밀리초).
         * 근거: 미정 — 시트 이동 속도를 재기 전 임시 값
         */
        const val SEAT_POLL_MS = 250L

        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: SEAT_TIMEOUT_MS
         * 목적: 시트 축 하나를 움직이는 제한 시간(밀리초). 넘으면 STOP하고
         *       실패로 돌려준다.
         * 근거: 측정 — 에뮬레이터에서 축 하나가 0→100까지 10초 안에 움직였다
         *       (docs/api-spike.md 시트 이동 행)
         */
        const val SEAT_TIMEOUT_MS = 10_000L

        /**
         * 작성: 2026-11-09 18:50:00 · 박건준
         * 변수: VERIFY_SETTLE_MS
         * 목적: 실행 뒤 다시 읽기 전에 기다리는 기본 시간(밀리초).
         * 근거: 측정 — 와이퍼·내기 순환·문 잠금·공조 전원은 0.4초 기다려 읽은
         *       값이 콘솔과 같았다(docs/api-spike.md)
         */
        const val VERIFY_SETTLE_MS = 400L

        /**
         * 작성: 2026-11-09 18:50:00 · 박건준
         * 변수: WINDOW_SETTLE_MS
         * 목적: 창문 위치를 바꾼 뒤 다시 읽기 전에 기다리는 시간(밀리초).
         *       창문은 움직이는 데 시간이 걸려 따로 둔다.
         * 근거: 측정 — 1.5초 기다려 읽은 값이 4개 창문 모두 맞았다
         *       (docs/api-spike.md 창문 행)
         */
        const val WINDOW_SETTLE_MS = 1_500L

        /**
         * 작성: 2026-11-09 18:50:00 · 박건준
         * 변수: MEMORY_WAIT_MS
         * 목적: 시트 메모리를 보낸 뒤 실패 콜백을 기다리는 시간(밀리초). 이
         *       시간 안에 실패가 오지 않으면 보낸 것으로 본다.
         * 근거: 표준 — `selectSeatMemory(area, value, onFailed)`는 성공 콜백이
         *       없다
         */
        const val MEMORY_WAIT_MS = 1_000L

        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: FRONT_WIPER, REAR_GLASS
         * 목적: 앞 와이퍼와 뒷유리 블라인드를 부를 창문 area.
         */
        val FRONT_WIPER = WindowArea(WindowValue.FRONT_WINDSHIELD) // 앞유리 와이퍼
        val REAR_GLASS = WindowArea(WindowValue.REAR_WINDSHIELD) // 뒷유리 블라인드

        /**
         * 작성: 2026-11-09 18:35:16 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: ALL_SEATS
         * 목적: 공조 전원·내기 순환을 부를 area. 다섯 좌석을 합친 하나다.
         * 근거: 측정 — `checkHvacPowerCapabilities`·
         *       `checkHvacRecirculationCapabilities`가 다섯 좌석 합친 area(117)
         *       하나만 돌려줬다(docs/api-spike.md 지원 area 표)
         */
        val ALL_SEATS = SeatArea(
            SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER,
            SeatValue.ROW_2_RIGHT,
        )

        /**
         * 작성: 2026-11-09 18:35:16 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: ALL_DOORS
         * 목적: 문 잠금을 부를 area. 네 문을 합친 하나라서 문 잠금은 "문 전체
         *       잠금" 하나로 다룬다.
         * 근거: 측정 — `checkDoorLockCapabilities`가 보닛과 네 문 합친 area(85)
         *       만 돌려줬다(docs/api-spike.md 지원 area 표)
         */
        val ALL_DOORS = DoorArea(
            DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT,
        )

        /**
         * 작성: 2026-11-09 18:35:16 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: LOCKABLE_WINDOWS
         * 목적: 창문 잠금을 따로 부를 수 있는 창문.
         * 근거: 측정 — `checkWindowLockCapabilities`가 뒷좌석 좌우와 네 창문
         *       합친 area만 돌려줬다. 앞좌석은 따로 잠글 수 없다
         *       (docs/api-spike.md 지원 area 표)
         */
        val LOCKABLE_WINDOWS = setOf(Window.REAR_LEFT, Window.REAR_RIGHT)
    }

    /** Pleos 차 연결. 만들 때 초기화하고 `close()`에서 놓는다 */
    private val vehicle = Vehicle(context.applicationContext).also { it.initialize() }

    /** 차에서 받은 값. 리스너가 채우고, 장소·속도는 늘 기본값이다 */
    private val sensed = MutableStateFlow(CarState())

    /** 앱이 넣는 값(장소, 속도). 장소·속도 함수 셋이 바꾼다 */
    private val manual = MutableStateFlow(Manual())

    /** 구독을 끊는 함수 목록. 구독할 때 더하고 `close()`에서 모두 부른다 */
    private val unsubscribe = mutableListOf<() -> Unit>()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 클래스: Manual
     * 목적: 차가 모르는 값 묶음.
     * 인자: place — 지금 장소. 장소 검색 결과나 데모 입력
     *       speedKmh — 데모가 덮어쓴 속도(km/h). null이면 센서 값을 쓴다
     *       sensorSpeedKmh — AAOS 속도 센서 값(km/h)
     */
    private data class Manual(
        val place: Place = Place.NONE,
        val speedKmh: Float? = null,
        val sensorSpeedKmh: Float = 0f,
    )

    /** 루틴이 보는 차 상태(값이 바뀌면 알려주는 상태). 차에서 받은 값에 장소·속도를 덮어 만든다 */
    override val state: StateFlow<CarState> = combine(sensed, manual) { s, m ->
        s.copy(place = m.place, speedKmh = m.speedKmh ?: m.sensorSpeedKmh)
    }.stateIn(scope, SharingStarted.Eagerly, CarState())

    init {
        subscribeGear()
        Door.entries.forEach(::subscribeDoor)
        Seat.entries.filter { it != Seat.REAR_CENTER }.forEach(::subscribeOccupancy)
        Seat.entries.forEach(::subscribeBelt)
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: setPlace
     * 목적: 지금 장소를 넣는다. 장소 검색 결과나 데모 입력이 부른다.
     * 인자: place — 지금 장소
     */
    fun setPlace(place: Place) = manual.update { it.copy(place = place) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: onSensorSpeed
     * 목적: AAOS 속도 센서 값을 넣는다.
     * 인자: kmh — 속도(km/h)
     */
    fun onSensorSpeed(kmh: Float) = manual.update { it.copy(sensorSpeedKmh = kmh) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: overrideSpeed
     * 목적: 데모용 속도 덮어쓰기. 에뮬레이터에서 속도를 넣을 수 없어서 쓴다.
     * 인자: kmh — 덮어쓸 속도(km/h). null이면 센서 값으로 돌아간다
     */
    fun overrideSpeed(kmh: Float?) = manual.update { it.copy(speedKmh = kmh) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: read
     * 목적: 제어 대상의 지금 값을 읽는다. 원래 값 저장과 실행 뒤 확인에 쓴다.
     * 인자: target — 읽을 대상
     * 반환: 대상 값(창문·시트 위치 Int, 잠금·전원 Boolean, 와이퍼 `WiperMode`).
     *       읽지 못하면 null이고 logcat에 이유를 남긴다. 시트 메모리는 지금
     *       번호를 읽는 API가 없어 늘 null
     * 근거: 측정 — 시트 위치 읽기는 콘솔 값과 상관없이 늘 0을 돌려준다
     *       (docs/api-spike.md 시트 위치 읽기 행)
     */
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

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: apply
     * 목적: 제어 하나를 실행하고 결과를 돌려준다.
     *       - 창문 위치·와이퍼·내기 순환·문 잠금·공조 전원 — 실행 뒤 다시 읽어
     *         확인한다
     *       - 창문 잠금·블라인드·시트 메모리 — 보내기만 하고 `Sent`
     *       - 시트 위치 — `moveSeatTo()`가 움직이며 확인한다
     *       - 좌석별 공조 전원은 차 전체 공조 전원으로 부른다
     * 인자: command — 대상과 바꿀 값
     * 반환: `Done`(확인됨), `Sent`(보냈지만 확인 못 함), `Unsupported`(차가
     *       받지 않는 대상), `Failed`(실패 이유)
     * 근거: 측정 — 창문 잠금은 다시 읽은 값만 바뀌고 콘솔은 그대로였다
     *       (docs/api-spike.md 창문 잠금 행). 공조·문 잠금·창문 잠금 area는
     *       docs/api-spike.md 지원 area 표
     */
    override suspend fun apply(command: Command): ControlResult = runCatching {
        val target = command.target // 바꿀 대상
        val value = command.value // 바꿀 값, 대상마다 형태가 다르다
        when (target) {
            is Target.WindowPosition -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getWindow().setWindowPosition(target.window.area(), value as Int, done(ok), fail)
                }
                verify(target, value, settleMs = WINDOW_SETTLE_MS)
            }
            is Target.WindowLock ->
                if (target.window !in LOCKABLE_WINDOWS) {
                    unsupported("setWindowLock ${target.window}")
                } else {
                    awaitCallback<Unit> { ok, fail ->
                        vehicle.getWindow().setWindowLock(target.window.area(), value as Boolean, done(ok), fail)
                    }
                    ControlResult.Sent
                }
            Target.Wiper -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getWiper().setWiperSwitch(FRONT_WIPER, (value as WiperMode).toSwitch(), done(ok), fail)
                }
                verify(target, value)
            }
            Target.Recirculation -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getHVAC().setHvacRecirculation(ALL_SEATS, value as Boolean, done(ok), fail)
                }
                verify(target, value)
            }
            Target.DoorLock -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getDoor().setDoorLock(ALL_DOORS, value as Boolean, done(ok), fail)
                }
                verify(target, value)
            }
            Target.RearBlind ->
                if (!rearBlindSupported()) {
                    unsupported("moveWindowBlind REAR_WINDSHIELD")
                } else {
                    val control = if (value as Boolean) MoveControl.FORWARD else MoveControl.BACKWARD // 올리기는 FORWARD
                    awaitCallback<Unit> { ok, fail ->
                        vehicle.getWindow().moveWindowBlind(REAR_GLASS, control, done(ok), fail)
                    }
                    ControlResult.Sent
                }
            is Target.Climate -> {
                awaitCallback<Unit> { ok, fail ->
                    vehicle.getHVAC().setHvacPower(ALL_SEATS, value as Boolean, done(ok), fail)
                }
                verify(target, value)
            }
            is Target.SeatPosition -> moveSeatTo(target, value as Int)
            is Target.SeatMemory -> {
                awaitCallback<Unit>(timeoutMs = MEMORY_WAIT_MS) { _, fail ->
                    vehicle.getSeat().selectSeatMemory(target.seat.area(), value as Int, fail)
                }
                ControlResult.Sent
            }
        }
    }.getOrElse { e ->
        // selectSeatMemory는 성공 콜백이 없어서, 실패 콜백 없이 시간이 지나면 보낸 것으로 본다
        if (command.target is Target.SeatMemory && e is TimeoutCancellationException) ControlResult.Sent
        else ControlResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: close
     * 목적: 구독을 모두 끊고 차 연결을 놓는다.
     */
    override fun close() {
        unsubscribe.forEach { runCatching(it) }
        runCatching { vehicle.release() }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: subscribeGear
     * 목적: 기어를 한 번 읽고, 바뀔 때마다 `sensed`에 넣는다.
     * 근거: 측정 — `registerGearSelection`은 deprecated지만 에뮬레이터에서
     *       P·D·R 모두 전달됐다(docs/api-spike.md 기어 행)
     */
    private fun subscribeGear() = guard("gear") {
        val gear = vehicle.getGear() // 기어 API
        val listener = object : GearSelectionListener { // 기어가 바뀌면 받는다
            override fun onSelected(selection: VehicleGear?) {
                sensed.update { it.copy(gear = selection.toGear()) }
            }
            override fun onFailed(e: Exception) = warn("gear", e)
        }
        gear.getCurrentGearSelectionState(
            { g -> sensed.update { it.copy(gear = g.toGear()) } },
            { warn("gear read", it) },
        )
        gear.registerGearSelection(listener)
        unsubscribe += { gear.unregisterGearSelection(listener) }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: subscribeDoor
     * 목적: 문 하나의 열림을 한 번 읽고, 바뀔 때마다 `sensed`에 넣는다.
     *       위치가 0보다 크면 열림으로 본다.
     * 인자: door — 구독할 문
     * 근거: 측정 — 뒷문 좌우 열기·닫기가 1/0으로 전달됐다(docs/api-spike.md
     *       문 행)
     */
    private fun subscribeDoor(door: Door) = guard("door $door") {
        val api = vehicle.getDoor() // 문 API
        val area = door.area() // 이 문의 area
        val update: (Int?) -> Unit = { pos -> // 위치를 받아 열림으로 넣는다, null이면 닫힘
            sensed.update { it.copy(doorOpen = it.doorOpen + (door to ((pos ?: 0) > 0))) }
        }
        val listener = object : DoorPositionListener { // 문 위치가 바뀌면 받는다
            override fun onPositionUpdated(value: Int?) = update(value)
            override fun onFailed(e: Exception) = warn("door $door", e)
        }
        api.getCurrentDoorPosition(area, update, { warn("door read $door", it) })
        api.registerDoorPosition(area, listener)
        unsubscribe += { api.unregisterDoorPosition(area, listener) }
    }

    /**
     * 작성: 2026-11-09 18:36:19 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: subscribeOccupancy
     * 목적: 좌석 하나의 탑승을 한 번 읽고, 바뀔 때마다 `sensed`에 넣는다.
     *       `INVERT_OCCUPANCY`면 받은 값을 뒤집는다. 뒷좌석 가운데는 부르지
     *       않는다.
     * 인자: seat — 구독할 좌석
     * 근거: 측정 — 뒷좌석 가운데는 콘솔에 칸이 없고 앱은 늘 탑승으로 읽었다
     *       (docs/api-spike.md 탑승 행)
     */
    private fun subscribeOccupancy(seat: Seat) = guard("occupancy $seat") {
        val api = vehicle.getSeat() // 시트 API
        val area = seat.area() // 이 좌석의 area
        val occupied: (Boolean?) -> Unit = { v -> // 받은 값을 탑승으로 넣는다, null이면 빈자리
            val sitting = v != null && v != INVERT_OCCUPANCY // 반전을 적용한 탑승 여부
            sensed.update { it.copy(seatOccupied = it.seatOccupied + (seat to sitting)) }
        }
        val listener = object : SeatOccupancyListener { // 탑승이 바뀌면 받는다
            override fun onSeatOccupancyUpdated(value: Boolean?) = occupied(value)
            override fun onFailed(e: Exception) = warn("occupancy $seat", e)
        }
        api.getCurrentSeatOccupancy(area, occupied, { warn("occupancy read $seat", it) })
        api.registerSeatOccupancy(area, listener)
        unsubscribe += { api.unregisterSeatOccupancy(area, listener) }
    }

    /**
     * 작성: 2026-11-09 18:36:19 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: subscribeBelt
     * 목적: 좌석 하나의 벨트를 한 번 읽고, 바뀔 때마다 `sensed`에 넣는다.
     * 인자: seat — 구독할 좌석
     * 근거: 미확인 — Virtual Console에 좌석별 벨트 칸이 없어 실측하지 못했다
     *       (docs/api-spike.md 벨트 행)
     */
    private fun subscribeBelt(seat: Seat) = guard("belt $seat") {
        val api = vehicle.getSeat() // 시트 API
        val area = seat.area() // 이 좌석의 area
        val buckled: (Boolean?) -> Unit = { v -> // 받은 값을 벨트로 넣는다, null이면 안 맴
            sensed.update { it.copy(beltBuckled = it.beltBuckled + (seat to (v == true))) }
        }
        val listener = object : SeatBeltBuckledStateListener { // 벨트가 바뀌면 받는다
            override fun onSeatBeltBuckledStateUpdated(value: Boolean?) = buckled(value)
            override fun onFailed(e: Exception) = warn("belt $seat", e)
        }
        api.getCurrentSeatBeltBuckledState(area, buckled, { warn("belt read $seat", it) })
        api.registerSeatBeltBuckledState(area, listener)
        unsubscribe += { api.unregisterSeatBeltBuckledState(area, listener) }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: verify
     * 목적: 실행 뒤 잠시 기다렸다가 다시 읽어 원하는 값이 됐는지 본다.
     * 인자: target — 확인할 대상
     *       expected — 원하는 값
     *       settleMs — 다시 읽기 전 기다리는 시간(밀리초)
     * 반환: 같으면 `Done`, 다르면 `Failed`, 읽을 수 없거나 형태가 다르면 `Sent`
     */
    private suspend fun verify(target: Target, expected: Any, settleMs: Long = VERIFY_SETTLE_MS): ControlResult {
        delay(settleMs)
        val now = read(target) ?: return ControlResult.Sent // 다시 읽은 값
        if (now.javaClass != expected.javaClass) return ControlResult.Sent
        return if (now == expected) ControlResult.Done
        else ControlResult.Failed("보냈지만 값이 $now 그대로예요(기대 $expected)")
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: moveSeatTo
     * 목적: 시트 축 하나를 목표 위치까지 움직인다. SDK에는 "몇 번 위치로"가
     *       없고 앞/뒤/정지(`MoveControl`)만 있어서 위치를 읽어 가며 움직인다.
     *       1. 지금 위치를 읽고, 이미 목표면 끝낸다
     *       2. 목표 쪽으로 움직이기 시작한다
     *       3. `SEAT_POLL_MS`마다 다시 읽어 목표에 닿으면 끝낸다. 멀어지면
     *          방향을 한 번 뒤집는다
     *       4. 기어가 P가 아니게 되거나 `SEAT_TIMEOUT_MS`가 지나면 실패
     *       - 어떻게 끝나든 STOP을 보낸다
     * 인자: target — 움직일 좌석과 축
     *       goal — 목표 위치(차가 주는 위치 단위)
     * 반환: 목표에 닿으면 `Done`, 위치를 못 읽으면 `Unsupported`, 아니면 `Failed`
     * 식: |now − goal| ≤ SEAT_TOLERANCE 이면 닿음
     *     (now = 다시 읽은 위치, goal = `goal`)
     * 근거: 측정 — 에뮬레이터는 위치 읽기가 늘 0이라 목표에서 못 멈추고 제한
     *       시간 뒤 STOP한다(docs/api-spike.md 시트 이동 행)
     */
    private suspend fun moveSeatTo(target: Target.SeatPosition, goal: Int): ControlResult {
        val area = target.seat.area() // 움직일 좌석의 area
        val axis = target.axis // 움직일 축
        var pos = read(target) as? Int ?: return unsupported("seat ${target.axis} position") // 직전에 읽은 위치
        if (abs(pos - goal) <= SEAT_TOLERANCE) return ControlResult.Done
        var direction = if (goal > pos) MoveControl.FORWARD else MoveControl.BACKWARD // 지금 움직이는 방향
        var flipped = false // 방향을 이미 한 번 뒤집었는지
        moveSeat(axis, area, direction)
        val started = System.currentTimeMillis() // 움직이기 시작한 시각(밀리초)
        try {
            while (System.currentTimeMillis() - started < SEAT_TIMEOUT_MS) {
                if (state.value.gear != Gear.PARK) return ControlResult.Failed("기어가 바뀌어서 멈췄어요")
                delay(SEAT_POLL_MS)
                val now = read(target) as? Int ?: break // 다시 읽은 위치, 못 읽으면 그만 움직인다
                if (abs(now - goal) <= SEAT_TOLERANCE) return ControlResult.Done
                val gettingWorse = abs(now - goal) > abs(pos - goal) // 목표에서 멀어졌는지
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
     * 작성: 2026-11-09 18:37:39 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: readSeat
     * 목적: 시트 축 하나의 위치를 읽는 SDK 함수를 고른다.
     * 인자: axis — 읽을 축
     *       area — 좌석 area
     *       ok — 위치를 받을 콜백(차가 주는 위치 단위, null이면 값 없음)
     *       fail — 실패를 받을 콜백
     * 근거: 표준 — Pleos Vehicle SDK 2.3.2. 머리받이 높이 읽기 이름은
     *       `getCurrentSeatHeadrestHeightAftPosition`이다
     */
    private fun readSeat(axis: SeatAxis, area: SeatArea, ok: (Int?) -> Unit, fail: (Exception) -> Unit) {
        val seat = vehicle.getSeat() // 시트 API
        when (axis) {
            SeatAxis.FORE_AFT -> seat.getCurrentSeatForeAftPosition(area, ok, fail)
            SeatAxis.HEIGHT -> seat.getCurrentSeatHeightPosition(area, ok, fail)
            SeatAxis.BACKREST_ANGLE -> seat.getCurrentSeatBackrestAnglePosition(area, ok, fail)
            SeatAxis.TILT -> seat.getCurrentSeatTiltPosition(area, ok, fail)
            SeatAxis.DEPTH -> seat.getCurrentSeatDepthPosition(area, ok, fail)
            SeatAxis.HEADREST_HEIGHT -> seat.getCurrentSeatHeadrestHeightAftPosition(area, ok, fail)
        }
    }

    /**
     * 작성: 2026-11-09 18:37:39 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: moveSeat
     * 목적: 시트 축 하나에 앞/뒤/정지를 보내고 차가 받을 때까지 기다린다.
     * 인자: axis — 움직일 축
     *       area — 좌석 area
     *       control — FORWARD(값이 커지는 쪽), BACKWARD, STOP
     * 반환: 없음. 실패하면 예외
     * 근거: 측정 — 앞뒤·높이·등받이·쿠션 각도 모두 FORWARD로 콘솔 값이 커졌다
     *       (docs/api-spike.md 시트 이동 행)
     */
    private suspend fun moveSeat(axis: SeatAxis, area: SeatArea, control: MoveControl) = awaitCallback { ok, fail ->
        val seat = vehicle.getSeat() // 시트 API
        val done: (SeatArea, MoveControl?) -> Unit = { _, _ -> ok(Unit) } // 차가 받았다는 콜백
        when (axis) {
            SeatAxis.FORE_AFT -> seat.moveSeatForeAft(area, control, done, fail)
            SeatAxis.HEIGHT -> seat.moveSeatHeight(area, control, done, fail)
            SeatAxis.BACKREST_ANGLE -> seat.moveSeatBackrestAngle(area, control, done, fail)
            SeatAxis.TILT -> seat.moveSeatTilt(area, control, done, fail)
            SeatAxis.DEPTH -> seat.moveSeatDepth(area, control, done, fail)
            SeatAxis.HEADREST_HEIGHT -> seat.moveSeatHeadrestHeight(area, control, done, fail)
        }
    }

    /**
     * 작성: 2026-11-09 18:35:16 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: rearBlindSupported
     * 목적: 차가 뒷유리 블라인드를 갖고 있는지 SDK에 묻는다.
     * 반환: 있으면 true. 묻기에 실패해도 false
     * 근거: 측정 — 에뮬레이터는 옆 창문 네 곳의 블라인드만 있고 뒷유리는
     *       없다(docs/api-spike.md 지원 area 표)
     */
    private suspend fun rearBlindSupported(): Boolean = runCatching {
        awaitCallback<Boolean> { ok, fail ->
            vehicle.getWindow().checkMoveWindowBlindCapabilities(
                { _, areas -> ok(areas.any { WindowValue.REAR_WINDSHIELD in it.values }) },
                fail,
            )
        }
    }.getOrDefault(false)

    /**
     * 작성: 2026-11-09 18:50:00 · 박건준
     * 함수: done
     * 목적: (area, 값)을 주는 SDK 성공 콜백을 `awaitCallback()`의 ok로 잇는다.
     *       받은 area와 값은 쓰지 않는다. 확인은 다시 읽어서 한다.
     * 인자: ok — `awaitCallback()`이 준 성공 콜백
     * 반환: SDK에 넘길 성공 콜백
     */
    private fun <A, V> done(ok: (Unit) -> Unit): (A, V) -> Unit = { _, _ -> ok(Unit) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: unsupported
     * 목적: 차가 받지 않는 대상의 결과를 만든다. 루틴은 이 단계를 음성 안내로
     *       바꾼다.
     * 인자: what — 받지 않는 API나 대상 이름
     * 반환: `Unsupported`
     */
    private fun unsupported(what: String) = ControlResult.Unsupported("$what 없음")

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: guard
     * 목적: 구독 하나가 실패해도 나머지 구독은 이어지게 예외를 잡아 남긴다.
     * 인자: what — 로그에 남길 구독 이름
     *       block — 구독 코드
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { warn(what, e) }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: warn
     * 목적: 실패를 logcat에 한 줄로 남긴다.
     * 인자: what — 실패한 일
     *       e — 실패 이유
     */
    private fun warn(what: String, e: Throwable) {
        Log.w(TAG, "$what: ${e.message}")
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: toGear
     * 목적: SDK 기어 값을 루틴의 기어로 바꾼다. 나머지 주행 기어는 D로 본다.
     * 반환: 루틴 기어. null·UNKNOWN이면 `Gear.UNKNOWN`
     */
    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: toMode
     * 목적: SDK 와이퍼 스위치 값을 루틴의 와이퍼 모드로 바꾼다. 단계(LEVEL)는
     *       묶는다.
     * 반환: 루틴 와이퍼 모드
     */
    private fun WiperSwitch.toMode(): WiperMode = when {
        this == WiperSwitch.OFF -> WiperMode.OFF
        this == WiperSwitch.AUTO -> WiperMode.AUTO
        this == WiperSwitch.MIST -> WiperMode.MIST
        name.startsWith("INTERMITTENT") -> WiperMode.INTERMITTENT
        name.startsWith("CONTINUOUS") -> WiperMode.CONTINUOUS
        else -> WiperMode.UNKNOWN
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: toSwitch
     * 목적: 루틴의 와이퍼 모드를 SDK 스위치 값으로 바꾼다. 단계가 있는 모드는
     *       1단계로 보낸다.
     * 반환: SDK 와이퍼 스위치. 모르는 모드는 OFF
     * 근거: 측정 — 에뮬레이터는 OFF·CONTINUOUS_LEVEL_1·CONTINUOUS_LEVEL_3·
     *       AUTO만 받는다(docs/api-spike.md 지원 area 표)
     */
    private fun WiperMode.toSwitch(): WiperSwitch = when (this) {
        WiperMode.OFF -> WiperSwitch.OFF
        WiperMode.AUTO -> WiperSwitch.AUTO
        WiperMode.MIST -> WiperSwitch.MIST
        WiperMode.INTERMITTENT -> WiperSwitch.INTERMITTENT_LEVEL_1
        WiperMode.CONTINUOUS -> WiperSwitch.CONTINUOUS_LEVEL_1
        WiperMode.UNKNOWN -> WiperSwitch.OFF
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: area
     * 목적: 루틴의 창문을 SDK 창문 area로 바꾼다.
     * 반환: 창문 하나짜리 area
     */
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

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: area
     * 목적: 루틴의 문을 SDK 문 area로 바꾼다.
     * 반환: 문 하나짜리 area
     */
    private fun Door.area() = DoorArea(
        when (this) {
            Door.FRONT_LEFT -> DoorValue.ROW_1_LEFT
            Door.FRONT_RIGHT -> DoorValue.ROW_1_RIGHT
            Door.REAR_LEFT -> DoorValue.ROW_2_LEFT
            Door.REAR_RIGHT -> DoorValue.ROW_2_RIGHT
            Door.TRUNK -> DoorValue.REAR
        },
    )

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: area
     * 목적: 루틴의 좌석을 SDK 좌석 area로 바꾼다.
     * 반환: 좌석 하나짜리 area
     */
    private fun Seat.area() = SeatArea(
        when (this) {
            Seat.DRIVER -> SeatValue.ROW_1_LEFT
            Seat.PASSENGER -> SeatValue.ROW_1_RIGHT
            Seat.REAR_LEFT -> SeatValue.ROW_2_LEFT
            Seat.REAR_CENTER -> SeatValue.ROW_2_CENTER
            Seat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT
        },
    )
}
