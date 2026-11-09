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
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelEconomyListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.VehiclePropertyIds
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import com.westone.sunstone.R
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

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 20:18:22 · 박건준
 * 클래스: PleosCarPort
 * 목적: Pleos Vehicle SDK로 만든 `CarPort`(기능 구현이 차에 요청하는 창구).
 *       - 차에서 오는 값(기어·문·탑승·벨트)은 리스너로 받아 `sensed`에 모은다
 *       - 차가 모르는 값(장소, 에뮬레이터에서 못 넣는 속도)은 앱이 `manual`에
 *         넣는다
 *       - 연료·배터리·주행 가능 거리는 `CarState`에 칸이 없어 `fuel`에 따로
 *         모은다
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
         * 수정: 2026-11-09 18:49:00 · 박건준
         * 변수: TAG
         * 목적: 차 관련 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 20:50:30 · 박건준
         * 변수: EMULATOR_HARDWARE
         * 목적: 에뮬레이터로 보는 하드웨어 이름(`Build.HARDWARE`).
         * 근거: 측정 — Pleos Connect v2.1.0 AVD의 `ro.hardware`는 `ranchu`.
         *       `goldfish`는 이전 안드로이드 에뮬레이터 이름이다
         */
        val EMULATOR_HARDWARE = setOf("ranchu", "goldfish")

        /**
         * 작성: 2026-11-09 18:36:19 · 박건준
         * 수정: 2026-11-09 20:50:30 · 박건준
         * 변수: INVERT_OCCUPANCY
         * 목적: 탑승 값 반전. 에뮬레이터에서만 SDK가 준 탑승 값을 뒤집어 쓴다.
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0에서 운전석·조수석·뒷좌석 좌우
         *       네 자리 모두 콘솔 탑승이 앱 빈자리로 읽혔다(docs/api-spike.md
         *       탑승 행). 에뮬레이터는 AAOS 기준 1=빈자리, 2=탑승을 보내는데
         *       SDK 2.3.2는 1을 탑승으로 바꾼다. 다른 팀도 에뮬레이터에서 같은
         *       현상
         * 미확인: 실제 차에서는 뒤집지 않는다. 실제 차 값이 맞는지는 확인하지
         *       못했다
         */
        val INVERT_OCCUPANCY = Build.HARDWARE in EMULATOR_HARDWARE

        /**
         * 작성: 2026-11-09 19:09:00 · 박건준
         * 변수: SEAT_MAX
         * 목적: 시트 축 위치의 끝 값. 0이 맨 뒤(BACKWARD 끝), 이 값이 맨 앞이다.
         * 근거: 측정 — `checkSeatForeAftPositionCapabilities` 범위 0~100,
         *       콘솔 `seat.positions`도 0~100(docs/api-spike.md 지원 area 표)
         */
        const val SEAT_MAX = 100

        /**
         * 작성: 2026-11-09 19:09:00 · 박건준
         * 변수: SEAT_HOME_MS
         * 목적: 기준점을 잡으려고 BACKWARD로 보내는 시간(밀리초). 어디서
         *       시작해도 맨 뒤(0)에 닿을 만큼 끝에서 끝 시간보다 길게 둔다.
         * 근거: 측정 — api-spike.md 시트 이동 시간 행(에뮬레이터), 끝에서 끝
         *       최대 2.14초. 실차 속도 미확인
         */
        const val SEAT_HOME_MS = 2_500L

        /**
         * 작성: 2026-11-09 19:09:00 · 박건준
         * 변수: SEAT_FULL_MS
         * 목적: 축마다 0→`SEAT_MAX`에 걸리는 시간(밀리초). 목표까지 FORWARD로
         *       보낼 시간을 이 값에 비례해 정한다.
         * 근거: 측정 — api-spike.md 시트 이동 시간 행(에뮬레이터, 0→100 5번
         *       평균). 실차 속도 미확인
         */
        val SEAT_FULL_MS = mapOf(
            SeatAxis.FORE_AFT to 2_075L,
            SeatAxis.HEIGHT to 2_049L,
            SeatAxis.BACKREST_ANGLE to 2_045L,
            SeatAxis.TILT to 2_052L,
        )

        /**
         * 작성: 2026-11-09 19:09:00 · 박건준
         * 변수: SEAT_FULL_DEFAULT_MS
         * 목적: 시간을 재지 못한 축(쿠션 길이, 머리받이)의 0→`SEAT_MAX` 시간
         *       (밀리초).
         * 근거: 미확인 — 콘솔에 칸이 없어 재지 못했다. 잰 축과 같은 1단위
         *       21밀리초로 둔다
         */
        const val SEAT_FULL_DEFAULT_MS = 2_100L

        /**
         * 작성: 2026-11-09 19:09:00 · 박건준
         * 변수: GEAR_CHECK_MS
         * 목적: 시트를 움직이는 동안 기어를 다시 보는 간격(밀리초). 기어가 P가
         *       아니게 되면 이 시간 안에 STOP한다.
         * 근거: 미정 — 시트가 1단위 움직이는 시간(약 20밀리초)의 두세 배
         */
        const val GEAR_CHECK_MS = 50L

        /**
         * 작성: 2026-11-09 18:49:00 · 박건준
         * 변수: VERIFY_SETTLE_MS
         * 목적: 실행 뒤 다시 읽기 전에 기다리는 기본 시간(밀리초).
         * 근거: 측정 — 와이퍼·내기 순환·문 잠금·공조 전원은 0.4초 기다려 읽은
         *       값이 콘솔과 같았다(docs/api-spike.md)
         */
        const val VERIFY_SETTLE_MS = 400L

        /**
         * 작성: 2026-11-09 18:49:00 · 박건준
         * 변수: WINDOW_SETTLE_MS
         * 목적: 창문 위치를 바꾼 뒤 다시 읽기 전에 기다리는 시간(밀리초).
         *       창문은 움직이는 데 시간이 걸려 따로 둔다.
         * 근거: 측정 — 1.5초 기다려 읽은 값이 4개 창문 모두 맞았다
         *       (docs/api-spike.md 창문 행)
         */
        const val WINDOW_SETTLE_MS = 1_500L

        /**
         * 작성: 2026-11-09 18:49:00 · 박건준
         * 변수: MEMORY_WAIT_MS
         * 목적: 시트 메모리를 보낸 뒤 실패 콜백을 기다리는 시간(밀리초). 이
         *       시간 안에 실패가 오지 않으면 보낸 것으로 본다.
         * 근거: 표준 — `selectSeatMemory(area, value, onFailed)`는 성공 콜백이
         *       없다
         */
        const val MEMORY_WAIT_MS = 1_000L

        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:49:00 · 박건준
         * 변수: FRONT_WIPER, REAR_GLASS
         * 목적: 앞 와이퍼와 뒷유리 블라인드를 부를 창문 area.
         */
        val FRONT_WIPER = WindowArea(WindowValue.FRONT_WINDSHIELD) // 앞유리 와이퍼
        val REAR_GLASS = WindowArea(WindowValue.REAR_WINDSHIELD) // 뒷유리 블라인드

        /**
         * 작성: 2026-11-09 18:35:16 · 박건준
         * 수정: 2026-11-09 18:49:00 · 박건준
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
         * 수정: 2026-11-09 18:49:00 · 박건준
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
         * 수정: 2026-11-09 18:49:00 · 박건준
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

    /** 안내 문구를 꺼낼 앱 Context */
    private val app = context.applicationContext

    /**
     * 좌석·축별 추정 위치(0~`SEAT_MAX`). `moveSeatTo()`가 다 움직인 뒤 채우고, 움직이다 멈추면 지운다.
     * 앱을 다시 켜거나 사람이 시트를 손으로 움직이면 실제와 달라진다. 없으면 위치를 모르는 것
     */
    private val seatEstimate = mutableMapOf<Pair<Seat, SeatAxis>, Int>()

    /** 차가 위치를 받는 창문. 처음 쓸 때 SDK에 물어 채운다. 묻기에 실패하면 null로 두고 보내 본다 */
    private var windowAreas: Set<WindowValue>? = null

    /** 연료·배터리·주행 가능 거리. SDK 리스너와 `onAaosEnergy()`가 채우고, 데모 `fuel`이 덮어쓴다 */
    private val fuelState = MutableStateFlow(FuelState())

    /** 화면이 보는 연료 값(값이 바뀌면 알려주는 상태). 받지 못한 칸은 null */
    val fuel: StateFlow<FuelState> = fuelState

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
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
        subscribeFuel()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: setPlace
     * 목적: 지금 장소를 넣는다. 장소 검색 결과나 데모 입력이 부른다.
     * 인자: place — 지금 장소
     */
    fun setPlace(place: Place) = manual.update { it.copy(place = place) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: onSensorSpeed
     * 목적: AAOS 속도 센서 값을 넣는다.
     * 인자: kmh — 속도(km/h)
     */
    fun onSensorSpeed(kmh: Float) = manual.update { it.copy(sensorSpeedKmh = kmh) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: overrideSpeed
     * 목적: 데모용 속도 덮어쓰기. 에뮬레이터에서 속도를 넣을 수 없어서 쓴다.
     * 인자: kmh — 덮어쓸 속도(km/h). null이면 센서 값으로 돌아간다
     */
    fun overrideSpeed(kmh: Float?) = manual.update { it.copy(speedKmh = kmh) }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: overrideFuel
     * 목적: 데모용 연료(%) 덮어쓰기. 에뮬레이터는 전기차라 연료 값을 넣을 수
     *       없어서 쓴다.
     * 인자: percent — 덮어쓸 연료(0~100%). null이면 SDK 값으로 돌아간다
     */
    fun overrideFuel(percent: Int?) = putFuel("override", percent) { it.copy(overridePercent = percent) }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: onAaosEnergy
     * 목적: AAOS 에너지 속성 값을 `fuel`의 경로별 칸에 넣는다.
     * 인자: propertyId — AAOS 속성 번호(`AaosEnergySource`가 읽는 다섯 개)
     *       value — 값(m, ml, Wh)
     */
    fun onAaosEnergy(propertyId: Int, value: Float) {
        val name = VehiclePropertyIds.toString(propertyId) // 로그에 남길 속성 이름
        putFuel(name, value) {
            when (propertyId) {
                VehiclePropertyIds.RANGE_REMAINING -> it.copy(aaosRangeM = value)
                VehiclePropertyIds.FUEL_LEVEL -> it.copy(aaosFuelMl = value)
                VehiclePropertyIds.EV_BATTERY_LEVEL -> it.copy(aaosBatteryWh = value)
                VehiclePropertyIds.INFO_FUEL_CAPACITY -> it.copy(fuelCapacityMl = value)
                VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY -> it.copy(batteryCapacityWh = value)
                else -> it
            }
        }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 19:09:00 · 박건준
     * 함수: read
     * 목적: 제어 대상의 지금 값을 읽는다. 원래 값 저장과 실행 뒤 확인에 쓴다.
     * 인자: target — 읽을 대상
     * 반환: 대상 값(창문·시트 위치 Int, 잠금·전원 Boolean, 와이퍼 `WiperMode`).
     *       읽지 못하면 null이고 logcat에 이유를 남긴다. 차가 받지 않는
     *       창문(선루프)은 묻지 않고 null. 시트 위치는 차에서 읽지 않고
     *       `seatEstimate` 추정값, 한 번도 움직이지 않은 축은 null. 시트
     *       메모리는 지금 번호를 읽는 API가 없어 늘 null
     * 근거: 측정 — SDK 시트 위치 읽기는 콘솔 값과 상관없이 늘 0이고, AAOS
     *       속성은 권한이 없다(docs/api-spike.md 시트 위치 읽기 행)
     */
    override suspend fun read(target: Target): Any? = runCatching {
        when (target) {
            is Target.WindowPosition -> if (!windowSupported(target.window)) {
                null
            } else {
                awaitCallback<Int?> { ok, fail ->
                    vehicle.getWindow().getCurrentWindowPosition(target.window.area(), ok, fail)
                }
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
            is Target.SeatPosition -> seatEstimate[target.seat to target.axis]
            is Target.SeatMemory -> null
        }
    }.onFailure { warn("read $target", it) }.getOrNull()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 20:50:30 · 박건준
     * 함수: apply
     * 목적: 제어 하나를 실행하고 결과를 돌려준다. 결과 이름은 logcat에
     *       남긴다(화면은 `Done`·`Sent` 모두 "완료"로 보인다).
     *       - 창문 위치·창문 잠금·와이퍼·내기 순환·문 잠금·공조 전원 — 실행 뒤
     *         다시 읽어 확인한다
     *       - 블라인드·시트 메모리 — 보내기만 하고 `Sent`
     *       - 시트 위치 — `moveSeatTo()`가 시간으로 움직이고 `Sent`
     *       - 좌석별 공조 전원은 차 전체 공조 전원으로 부른다
     *       - 실패 이유는 화면에 그대로 보이므로 SDK 예외 문장 대신
     *         `failure()`가 고른 한국어 안내를 쓴다
     * 인자: command — 대상과 바꿀 값
     * 반환: `Done`(확인됨), `Sent`(보냈지만 확인 못 함), `Unsupported`(차가
     *       받지 않는 대상), `Failed`(실패 이유, 한국어)
     * 근거: 측정 — 창문 잠금은 SDK 다시 읽기는 반영, 콘솔은 불일치
     *       (에뮬레이터 쪽으로 보임, 다른 팀도 같은 현상, docs/api-spike.md
     *       창문 잠금 행). 공조·문 잠금·창문 잠금 area는 같은 문서 지원
     *       area 표
     * 미확인: 창문 잠금을 실차에서 다시 읽은 값이 실제 잠김과 맞는지
     */
    override suspend fun apply(command: Command): ControlResult = runCatching {
        val target = command.target // 바꿀 대상
        val value = command.value // 바꿀 값, 대상마다 형태가 다르다
        when (target) {
            is Target.WindowPosition -> if (!windowSupported(target.window)) {
                unsupported("setWindowPosition ${target.window}")
            } else {
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
                    verify(target, value)
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
        else failure("apply ${command.target}", e)
    }.also { Log.i(TAG, "결과,${command.target},${it.javaClass.simpleName}") }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: close
     * 목적: 구독을 모두 끊고 차 연결을 놓는다.
     */
    override fun close() {
        unsubscribe.forEach { runCatching(it) }
        runCatching { vehicle.release() }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 20:50:30 · 박건준
     * 함수: subscribeOccupancy
     * 목적: 좌석 하나의 탑승을 한 번 읽고, 바뀔 때마다 `sensed`에 넣는다.
     *       에뮬레이터(`INVERT_OCCUPANCY`)면 받은 값을 뒤집는다. 뒷좌석
     *       가운데는 부르지 않는다.
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: subscribeFuel
     * 목적: 연료(%)·연비·주행 가능 거리·배터리 충전량을 SDK로 한 번 읽고,
     *       바뀔 때마다 `fuel`에 넣는다. 하나가 실패해도 나머지는 구독한다.
     * 근거: 측정 — 에뮬레이터 차는 전기차라 콘솔에 연료 칸이 없고 배터리
     *       충전량·주행 가능 거리만 있다(docs/api-spike.md 연료 행)
     */
    private fun subscribeFuel() {
        guard("fuel percent") {
            val api = vehicle.getFuel() // 연료 API
            val put: (Int?) -> Unit = { v -> putFuel("fuelPercent", v) { it.copy(fuelPercent = v) } } // 연료(%)를 넣는다
            val listener = object : FuelPercentListener { // 연료가 바뀌면 받는다
                override fun onFuelPercentUpdated(value: Int?) = put(value)
                override fun onFailed(e: Exception) = warn("fuel percent", e)
            }
            api.getFuelPercent(put, { warn("fuel percent read", it) })
            api.registerFuelPercent(listener)
            unsubscribe += { api.unregisterFuelPercent(listener) }
        }
        guard("fuel economy") {
            val api = vehicle.getFuel() // 연료 API
            val put: (Float?) -> Unit = { v -> putFuel("economy", v) { it.copy(economy = v) } } // 연비를 넣는다
            val listener = object : FuelEconomyListener { // 연비가 바뀌면 받는다
                override fun onFuelEconomyUpdated(value: Float?) = put(value)
                override fun onFailed(e: Exception) = warn("fuel economy", e)
            }
            api.getCurrentFuelEconomy(put, { warn("fuel economy read", it) })
            api.registerCurrentFuelEconomy(listener)
            unsubscribe += { api.unregisterCurrentFuelEconomy(listener) }
        }
        guard("range") {
            val api = vehicle.getOdometer() // 주행 기록 API
            val put: (Float?) -> Unit = { v -> putFuel("range", v) { it.copy(range = v) } } // 주행 가능 거리를 넣는다
            val listener = object : RangeRemainingListener { // 주행 가능 거리가 바뀌면 받는다
                override fun onRangeRemainingChanged(value: Float?) = put(value)
                override fun onFailed(e: Exception) = warn("range", e)
            }
            api.getRangeRemainingDistance(put, { warn("range read", it) })
            api.registerRangeRemainingListener(listener)
            unsubscribe += { api.unregisterRangeRemainingListener(listener) }
        }
        guard("battery") {
            val api = vehicle.getEvBattery() // 전기차 배터리 API
            val put: (Int?) -> Unit = { v -> putFuel("batteryPercent", v) { it.copy(batteryPercent = v) } } // 충전량(%)
            val listener = object : EvBatteryStateOfChargeListener { // 충전량이 바뀌면 받는다
                override fun onEvBatteryStateOfChargeUpdated(value: Int?) = put(value)
                override fun onFailed(e: Exception) = warn("battery", e)
            }
            api.getEvBatteryStateOfCharge(put, { warn("battery read", it) })
            api.registerEvBatteryStateOfCharge(listener)
            unsubscribe += { api.unregisterEvBatteryStateOfCharge(listener) }
        }
    }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: putFuel
     * 목적: 연료 값 한 칸을 바꾸고, 값이 실제로 바뀌었을 때만 어느 경로에서
     *       무슨 값이 왔는지 logcat에 남긴다.
     * 인자: name — 로그에 남길 칸 이름
     *       value — 받은 값
     *       change — 지금 값을 받아 바꾼 값을 돌려주는 함수
     * 근거: 측정 — 에뮬레이터 AAOS 에너지 속성은 값이 같아도 1초마다 다시
     *       온다(docs/api-spike.md 연료 행)
     */
    private fun putFuel(name: String, value: Any?, change: (FuelState) -> FuelState) {
        val before = fuelState.value // 바꾸기 전 값
        fuelState.update(change)
        if (fuelState.value != before) Log.i(TAG, "fuel,$name,$value")
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 19:09:00 · 박건준
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
        else ControlResult.Failed(app.getString(R.string.car_not_changed, now.toString(), expected.toString()))
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 19:09:00 · 박건준
     * 함수: moveSeatTo
     * 목적: 시트 축 하나를 목표 위치로 움직인다. 차에서 위치를 읽을 수 없어
     *       시간으로 맞춘다.
     *       1. 기어가 P가 아니면 움직이지 않는다
     *       2. BACKWARD로 `SEAT_HOME_MS` 보낸 뒤 STOP. 이 축은 이제 0이다
     *       3. FORWARD로 목표만큼의 시간 보낸 뒤 STOP
     *       4. 추정 위치를 목표로 기억한다
     *       - 움직이는 중 기어가 P가 아니게 되면 바로 STOP하고 추정 위치를
     *         지운다
     * 인자: target — 움직일 좌석과 축
     *       goal — 목표 위치(0~`SEAT_MAX`, 넘으면 끝으로 자른다)
     * 반환: 다 움직이면 `Sent`(위치를 확인할 수 없어서), 기어 때문에 멈추면
     *       `Failed`
     * 식: t = goal × T_full / SEAT_MAX
     *     (t = FORWARD 시간(밀리초), T_full = 축의 `SEAT_FULL_MS`)
     * 근거: 측정 — api-spike.md 시트 이동 시간 행(에뮬레이터), 실차 속도
     *       미확인. 목표 30·70 맞추기 오차는 같은 문서 시간 맞추기 표
     */
    private suspend fun moveSeatTo(target: Target.SeatPosition, goal: Int): ControlResult {
        if (state.value.gear != Gear.PARK) return ControlResult.Failed(app.getString(R.string.car_seat_not_park))
        val key = target.seat to target.axis // 추정 위치를 기억할 칸
        val area = target.seat.area() // 움직일 좌석의 area
        val axis = target.axis // 움직일 축
        val aim = goal.coerceIn(0, SEAT_MAX) // 자른 목표 위치
        val forwardMs = aim * (SEAT_FULL_MS[axis] ?: SEAT_FULL_DEFAULT_MS) / SEAT_MAX // FORWARD 보낼 시간(밀리초)
        seatEstimate.remove(key)
        if (!runSeat(axis, area, MoveControl.BACKWARD, SEAT_HOME_MS)) return seatStopped()
        if (forwardMs > 0 && !runSeat(axis, area, MoveControl.FORWARD, forwardMs)) return seatStopped()
        seatEstimate[key] = aim
        return ControlResult.Sent
    }

    /**
     * 작성: 2026-11-09 19:09:00 · 박건준
     * 함수: runSeat
     * 목적: 시트 축 하나를 한 방향으로 정한 시간만큼 보낸 뒤 STOP한다. 기다리는
     *       동안 `GEAR_CHECK_MS`마다 기어를 보고, P가 아니게 되면 바로 멈춘다.
     *       어떻게 끝나든 STOP을 보낸다.
     * 인자: axis — 움직일 축
     *       area — 좌석 area
     *       direction — FORWARD 또는 BACKWARD
     *       durationMs — 보낼 시간(밀리초)
     * 반환: 시간을 다 채웠으면 true, 기어 때문에 멈췄으면 false
     */
    private suspend fun runSeat(axis: SeatAxis, area: SeatArea, direction: MoveControl, durationMs: Long): Boolean {
        val started = SystemClock.elapsedRealtime() // 보내기 시작한 시각(밀리초)
        try {
            moveSeat(axis, area, direction)
            while (true) {
                if (state.value.gear != Gear.PARK) return false
                val left = durationMs - (SystemClock.elapsedRealtime() - started) // 남은 시간(밀리초)
                if (left <= 0) return true
                delay(minOf(left, GEAR_CHECK_MS))
            }
        } finally {
            runCatching { moveSeat(axis, area, MoveControl.STOP) }
        }
    }

    /**
     * 작성: 2026-11-09 19:09:00 · 박건준
     * 함수: seatStopped
     * 목적: 기어가 바뀌어 시트를 멈췄을 때의 결과. 위치를 모르게 됐으므로
     *       루틴은 이 단계를 안내로 바꾼다.
     * 반환: `Failed`(한국어 이유)
     */
    private fun seatStopped() = ControlResult.Failed(app.getString(R.string.car_seat_gear_changed))

    /**
     * 작성: 2026-11-09 18:37:39 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 작성: 2026-11-09 19:09:00 · 박건준
     * 함수: windowSupported
     * 목적: 차가 이 창문의 위치를 받는지 SDK에 묻는다. 처음 한 번만 묻고
     *       `windowAreas`에 기억한다. 묻기에 실패하면 보내 본다.
     * 인자: window — 확인할 창문
     * 반환: 받으면 true. 묻기에 실패해도 true
     * 근거: 측정 — 에뮬레이터는 선루프(ROOF_TOP_1/2) 위치를 받지 않는다
     *       (docs/api-spike.md 선루프 행, 지원 area 표)
     */
    private suspend fun windowSupported(window: Window): Boolean {
        val known = windowAreas ?: runCatching { // 위치를 받는 창문, 모르면 null
            awaitCallback<Set<WindowValue>> { ok, fail ->
                vehicle.getWindow().checkWindowPositionCapabilities(
                    { _, areas -> ok(areas.keys.flatMap { it.values.toList() }.toSet()) },
                    fail,
                )
            }
        }.onFailure { warn("window capabilities", it) }.getOrNull()?.also { windowAreas = it }
        return known == null || window.area().values.all { it in known }
    }

    /**
     * 작성: 2026-11-09 18:49:00 · 박건준
     * 함수: done
     * 목적: (area, 값)을 주는 SDK 성공 콜백을 `awaitCallback()`의 ok로 잇는다.
     *       받은 area와 값은 쓰지 않는다. 확인은 다시 읽어서 한다.
     * 인자: ok — `awaitCallback()`이 준 성공 콜백
     * 반환: SDK에 넘길 성공 콜백
     */
    private fun <A, V> done(ok: (Unit) -> Unit): (A, V) -> Unit = { _, _ -> ok(Unit) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 19:09:00 · 박건준
     * 함수: unsupported
     * 목적: 차가 받지 않는 대상의 결과를 만든다. 루틴은 이 단계를 음성 안내로
     *       바꾼다. 화면에는 한국어 안내만 보이고, API 이름은 logcat에 남긴다.
     * 인자: what — 받지 않는 API나 대상 이름
     * 반환: `Unsupported`(한국어 이유)
     */
    private fun unsupported(what: String): ControlResult {
        Log.i(TAG, "지원 안 함: $what")
        return ControlResult.Unsupported(app.getString(R.string.car_unsupported))
    }

    /**
     * 작성: 2026-11-09 19:09:00 · 박건준
     * 함수: failure
     * 목적: SDK 예외를 화면에 보일 한국어 이유로 바꾼다. 원래 예외는
     *       logcat에 남긴다.
     * 인자: what — 실패한 일(로그용)
     *       e — SDK나 기다리기에서 난 예외
     * 반환: 차가 받지 않는 area면 `Unsupported`, 나머지는 `Failed`
     * 근거: 측정 — 에뮬레이터는 받지 않는 area에
     *       `IllegalArgumentException: area ID … not supported`를 던진다
     *       (docs/api-spike.md)
     */
    private fun failure(what: String, e: Throwable): ControlResult {
        warn(what, e)
        if (e is IllegalArgumentException && e.message.orEmpty().contains("not supported")) {
            return ControlResult.Unsupported(app.getString(R.string.car_unsupported))
        }
        @StringRes val reason = when (e) { // 화면에 보일 이유
            is TimeoutCancellationException -> R.string.car_no_reply
            is SecurityException -> R.string.car_no_permission
            else -> R.string.car_send_failed
        }
        return ControlResult.Failed(app.getString(reason))
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
     * 수정: 2026-11-09 18:49:00 · 박건준
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
