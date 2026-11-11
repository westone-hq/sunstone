package com.westone.sunstone

import android.app.Application
import android.content.Context
import android.util.Log
import com.westone.sunstone.car.AaosEnergySource
import com.westone.sunstone.car.AaosSpeedSource
import com.westone.sunstone.car.PleosCarPort
import com.westone.sunstone.connect.AndroidSpeaker
import com.westone.sunstone.connect.KeepAwakeService
import com.westone.sunstone.connect.PrefsMemory
import com.westone.sunstone.connect.PrefsSeatProfiles
import com.westone.sunstone.info.FixedInfo
import com.westone.sunstone.navi.PleosNaviPort
import com.westone.sunstone.routines.dialog.TripDialog
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.fake.FakeNavi
import java.time.LocalDate
import com.westone.sunstone.navi.PlaceWatcher
import com.westone.sunstone.navi.PleosNavi
import com.westone.sunstone.navi.Proximity
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.port.CarPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 클래스: SunstoneApp
 * 목적: 앱이 켜질 때 `AppGraph`를 한 번 만들어 앱이 꺼질 때까지 둔다.
 */
class SunstoneApp : Application() {

    /** 앱 전체가 함께 쓰는 조립부. `onCreate()`에서 만든다 */
    lateinit var graph: AppGraph
        private set

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: onCreate
     * 목적: 조립부를 만든다.
     */
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 함수: graph
 * 목적: 화면·수신기에서 조립부를 꺼낸다.
 */
val Context.graph: AppGraph get() = (applicationContext as SunstoneApp).graph

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-11 15:14:38 · 박희정
 * 클래스: AppGraph
 * 목적: 기능 구현(routines)과 앱 연결을 한곳에서 잇는 조립부. 의존성 주입
 *       라이브러리 없이 손으로 만든다.
 *       - 차는 Pleos를 쓰고, 데모 설정(`fake on`)이거나 Pleos 연결에
 *         실패하면 가짜 차를 쓴다
 *       - 말·Gleo·화면·데모 입력은 모두 `handle()`로 들어온다
 *       - 장소는 근접 알림(`PlaceWatcher`)이 바꾸고, 내비 도착은 logcat에만
 *         남긴다(routines 창구에 아직 잇지 않음)
 *       - 길안내 중이거나 루틴이 진행 중이면 `KeepAwakeService`를 켠다
 * 인자: app — 앱 Context
 * 미구현: 프로토타입이라 Pleos 연결에 실패하면 알리지 않고 가짜 차로 진행한다
 *       (상태 줄에 "가짜 차"). 실제 차에서는 연결 실패를 안내해야 한다.
 *       가짜 차일 때 길안내 대화는 장소가 없는 가짜 내비를 써서 목적지를 늘
 *       "찾지 못했어요"로 답한다(시연 빌드 분리 때 고정 장소를 넣는다)
 */
class AppGraph(private val app: Context) {

    private companion object {
        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:49:00 · 박건준
         * 변수: TAG
         * 목적: 앱 전체 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "Sunstone"

        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:49:00 · 박건준
         * 변수: KEY_FAKE
         * 목적: 가짜 차를 쓸지 저장하는 설정 키.
         */
        const val KEY_FAKE = "use_fake_car"

        /**
         * 작성: 2026-11-11 12:06:00 · 박건준
         * 변수: AWAKE_GRACE_MS
         * 목적: 깨어 있을 이유가 사라진 뒤 서비스를 끄기 전에 기다리는
         *       시간(밀리초). 그 사이 다시 필요해지면 끄지 않는다.
         * 근거: 측정 — 경유지를 더하면 내비가 경로를 취소했다 다시 시작해서
         *       길안내 상태가 1초 남짓 꺼진다. 서비스를 한 번 끄면 앱이 뒤에
         *       있어 다시 켜지 못했다(`mAllowStartForeground false`)
         */
        const val AWAKE_GRACE_MS = 15_000L
    }

    /** 앱이 살아 있는 동안 도는 작업 범위. 화면 스레드에서 돈다 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 앱 설정 저장소. 가짜 차 여부만 둔다 */
    private val settings = app.getSharedPreferences("sunstone", Context.MODE_PRIVATE)

    /** 이번 실행에서 가짜 차를 쓰는지. 앱을 켤 때 한 번 정한다 */
    val usingFakeCar: Boolean = settings.getBoolean(KEY_FAKE, false)

    /** 데모로 켠 가짜 차. Pleos를 쓰면 null */
    val fake: FakeCar? = if (usingFakeCar) FakeCar() else null

    /** Pleos 차 창구. 가짜 차를 쓰거나 연결에 실패하면 null */
    val pleos: PleosCarPort? = if (usingFakeCar) null else runCatching { PleosCarPort(app, scope) }
        .onFailure { Log.w(TAG, "Pleos 연결 실패, 가짜 차로 진행: ${it.message}") }
        .getOrNull()

    /** 루틴이 쓰는 차. Pleos, 데모 가짜 차, 연결 실패 때의 가짜 차 순서로 고른다 */
    val car: CarPort = pleos ?: fake ?: FakeCar()

    /** 음성 안내 */
    val speaker = AndroidSpeaker(app)

    /** 사람별 시트 자세 저장소 */
    val profiles = PrefsSeatProfiles(app)

    /** 시트 자세 루틴. 자세 저장(`capture`)에도 써서 따로 둔다 */
    private val seatRoutine = SeatMemoryRoutine(profiles)

    /** 사용 기록(마지막 세차일). 데모 `washed N`으로 바꿀 수 있다 */
    val memory = PrefsMemory(app)

    /** 세차 예약. 길안내 대화가 주유소에 예약하고 세차 모드가 본다. 둘이 같은 것을 쓴다 */
    private val booking = WashBooking()

    /** 루틴 실행기. 만들 때 상황 감지를 시작한다 */
    val runner = RoutineRunner(
        car = car,
        routines = listOf(WashRoutine(booking, memory) { LocalDate.now() }, ChildLockRoutine(), seatRoutine),
        speaker = speaker,
        scope = scope,
    ).also { it.start() } // → 로직 이동: RoutineRunner.start()

    /** 속도 센서. 값을 Pleos 차 창구에 넣는다. 가짜 차일 때는 버린다 */
    private val speed = AaosSpeedSource(app) { kmh -> pleos?.onSensorSpeed(kmh) }

    /** 근접 알림 창구. Pleos 차일 때만 만든다 */
    private val proximity: Proximity? = pleos?.let { Proximity(app) }

    /** 내비 창구. Pleos 차일 때만 만든다. 처음 쓸 때 내비 서비스에 연결한다 */
    val navi: PleosNavi? = proximity?.let { PleosNavi(app, it) }

    /** 시연 장소 근접 알림. 들어가고 나가면 Pleos 차 장소를 바꾼다. 위치 권한을 받은 뒤 시작한다 */
    val places: PlaceWatcher? = proximity?.let { p -> PlaceWatcher(app, p) { place -> setPlace(place) } }

    /**
     * 길안내 대화(시나리오 ① #1~#2). Pleos 차면 Pleos 내비, 가짜 차면 장소가 없는 가짜 내비를 쓴다.
     * 주유소·날씨는 고정 응답이다(`FixedInfo`)
     */
    val trip = TripDialog(
        car = car,
        navi = navi?.let { n -> PleosNaviPort(n) { places?.spots?.map { it.hit }.orEmpty() } } ?: FakeNavi(emptyList()),
        info = FixedInfo(),
        memory = memory,
        booking = booking,
        speaker = speaker,
        today = { LocalDate.now() },
    )

    /** 앱을 깨어 있게 하는 중인지. 같은 값으로 서비스를 다시 켜고 끄지 않으려고 둔다 */
    private val awake = MutableStateFlow(false)

    /** AAOS 에너지 속성과 연료 종류. 값을 Pleos 차 창구에 넣는다. 가짜 차일 때는 버린다 */
    private val energy = AaosEnergySource(
        app,
        onValue = { id, value -> pleos?.onAaosEnergy(id, value) }, // → 로직 이동: PleosCarPort.onAaosEnergy()
        onFuelTypes = { types -> pleos?.onFuelTypes(types) }, // → 로직 이동: PleosCarPort.onFuelTypes()
    )

    init {
        watchAwake()
        watchArrivals()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: startSpeed
     * 목적: 속도 구독을 시작한다. 속도 권한을 받은 뒤 `MainActivity`가 부른다.
     */
    fun startSpeed() = speed.start() // → 로직 이동: AaosSpeedSource.start()

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: startEnergy
     * 목적: AAOS 에너지 구독을 시작한다. 에너지 권한을 받은 뒤 `MainActivity`가
     *       부른다.
     */
    fun startEnergy() = energy.start() // → 로직 이동: AaosEnergySource.start()

    /**
     * 작성: 2026-11-11 12:22:00 · 박건준
     * 함수: startPlaces
     * 목적: 시연 장소 근접 알림을 건다. 위치 권한을 받은 뒤 `MainActivity`가
     *       부른다. 가짜 차일 때는 하지 않는다.
     */
    fun startPlaces() {
        val watcher = places ?: return // 장소 알림, 가짜 차면 null
        scope.launch { Log.i(TAG, "장소 알림 ${watcher.start()}곳") } // → 로직 이동: PlaceWatcher.start()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 15:14:38 · 박희정
     * 함수: handle
     * 목적: 말·Gleo·화면·데모 입력을 처리한다.
     *       - `Run` — 루틴 실행을 요청한다
     *       - `Undo` — 이름이 없으면 가장 최근에 켜진 루틴을 되돌린다
     *       - `SaveSeat` — 지금 시트 자세를 이름으로 저장한다
     *       - `Navigate` — 길안내 대화로 목적지를 넘긴다
     *       - `Answer` — 길안내 대화의 주유 제안, 그다음 루틴 권하기 순서로
     *         답한다. 둘 다 없으면 못 알아들은 것으로 본다
     * 인자: action — 알아들은 요청
     */
    fun handle(action: VoiceAction) {
        when (action) {
            // → 로직 이동: RoutineRunner.request()
            is VoiceAction.Run -> runner.request(action.request)
            is VoiceAction.Undo -> {
                val id = action.id ?: runner.ui.value.runs.values // 되돌릴 루틴, 켜진 것이 없으면 null
                    .filter { it.phase == Phase.ACTIVE }
                    .maxByOrNull { it.startedAtMs }?.id
                // → 로직 이동: Speaker.say(), RoutineRunner.undo()
                if (id == null) speaker.say(app.getString(R.string.voice_nothing_to_undo)) else runner.undo(id)
            }
            is VoiceAction.SaveSeat -> scope.launch {
                val saved = seatRoutine.capture(action.name, car) // 저장한 자세, 시트를 못 읽으면 null
                // → 로직 이동: Speaker.say()
                speaker.say(
                    if (saved != null) app.getString(R.string.voice_seat_saved, action.name)
                    else app.getString(R.string.voice_seat_unreadable),
                )
            }
            // → 로직 이동: TripDialog.navigate()
            is VoiceAction.Navigate -> scope.launch { trip.navigate(action.destination) }
            is VoiceAction.Answer -> scope.launch {
                // → 로직 이동: TripDialog.answer(), RoutineRunner.answer(), AppGraph.notUnderstood()
                if (!trip.answer(action.yes) && !runner.answer(action.yes)) notUnderstood()
            }
        }
    }

    /**
     * 작성: 2026-11-09 19:11:42 · 박건준
     * 함수: notUnderstood
     * 목적: 말을 알아듣지 못했다고 말한다. 화면 입력칸과 데모 `say`가 함께
     *       쓴다.
     */
    fun notUnderstood() = speaker.say(app.getString(R.string.voice_not_understood)) // → 로직 이동: Speaker.say()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 12:22:00 · 박건준
     * 함수: setPlace
     * 목적: 지금 장소를 넣는다. 쓰고 있는 차에만 들어간다. Pleos 차는 장소
     *       근접 알림이 부르고, 데모 `place`는 예비로 남겨 둔다.
     * 인자: place — 지금 장소
     */
    fun setPlace(place: Place) {
        pleos?.setPlace(place) // → 로직 이동: PleosCarPort.setPlace()
        fake?.update { it.copy(place = place) }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-10 12:56:20 · 박건준
     * 함수: overrideSpeed
     * 목적: 데모 입력으로 속도를 덮어쓴다.
     * 인자: kmh — 속도(km/h). null이면 센서 값으로 돌아간다(가짜 차는 0)
     * 미구현: 프로토타입이라 데모 `speed`가 부른다. 실제 차에서는 쓰지 않는다
     */
    fun overrideSpeed(kmh: Float?) {
        pleos?.overrideSpeed(kmh) // → 로직 이동: PleosCarPort.overrideSpeed()
        fake?.update { it.copy(speedKmh = kmh ?: 0f) }
    }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 수정: 2026-11-11 15:14:38 · 박희정
     * 함수: overrideFuel
     * 목적: 데모 입력으로 연료(%)를 덮어쓴다. 쓰고 있는 차에만 들어간다.
     * 인자: percent — 연료(0~100%). null이면 차 값으로 돌아간다
     * 미구현: 프로토타입이라 데모 `fuel N`이 부른다. 실제 차에서는 쓰지 않는다
     *       (`PleosCarPort.overrideFuel` 참고)
     */
    fun overrideFuel(percent: Int?) {
        pleos?.overrideFuel(percent) // → 로직 이동: PleosCarPort.overrideFuel()
        fake?.update { it.copy(fuelPercent = percent) }
    }

    /**
     * 작성: 2026-11-11 12:22:00 · 박건준
     * 함수: overrideBelt
     * 목적: 데모 입력으로 좌석 하나의 벨트를 덮어쓴다.
     * 인자: seat — 좌석
     *       buckled — 맸으면 true, 풀었으면 false. null이면 차 값으로 돌아간다
     *       (가짜 차는 바꾸지 않는다)
     * 미구현: 프로토타입이라 에뮬레이터에 좌석별 벨트 값이 없어 데모로 넣는다
     *       (`PleosCarPort.overrideBelt` 참고)
     */
    fun overrideBelt(seat: Seat, buckled: Boolean?) {
        pleos?.overrideBelt(seat, buckled) // → 로직 이동: PleosCarPort.overrideBelt()
        if (buckled != null) fake?.update { it.copy(beltBuckled = it.beltBuckled + (seat to buckled)) }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: editFakeCar
     * 목적: 가짜 차일 때만 차 상태를 바꾼다. Pleos 차는 Virtual Console로
     *       바꾼다.
     * 인자: block — 지금 상태를 받아 바꾼 상태를 돌려주는 함수
     * 반환: 가짜 차라서 바꿨으면 true
     */
    fun editFakeCar(block: (CarState) -> CarState): Boolean {
        val f = fake ?: return false // 바꿀 가짜 차
        f.update(block)
        return true
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: setUseFakeCar
     * 목적: 다음 실행부터 가짜 차를 쓸지 저장한다. 앱을 다시 켜야 적용된다.
     * 인자: on — true면 가짜 차
     */
    fun setUseFakeCar(on: Boolean) = settings.edit().putBoolean(KEY_FAKE, on).apply()

    /**
     * 작성: 2026-11-11 12:22:00 · 박건준
     * 함수: watchAwake
     * 목적: 길안내 중이거나 루틴이 진행 중(실행·켜짐·되돌리는 중)이면
     *       `KeepAwakeService`를 켜고, 둘 다 아닌 채로 `AWAKE_GRACE_MS`가
     *       지나면 끈다.
     */
    private fun watchAwake() {
        val routing = navi?.routing ?: MutableStateFlow(false) // 길안내 중인지
        scope.launch {
            combine(routing, runner.ui) { guiding, ui ->
                guiding || ui.runs.values.any { it.phase != Phase.FINISHED }
            }.distinctUntilChanged().collectLatest { need ->
                if (!need) delay(AWAKE_GRACE_MS)
                if (need == awake.value) return@collectLatest
                awake.value = need
                // → 로직 이동: KeepAwakeService.start(), KeepAwakeService.stop()
                if (need) KeepAwakeService.start(app) else KeepAwakeService.stop(app)
            }
        }
    }

    /**
     * 작성: 2026-11-11 12:22:00 · 박건준
     * 함수: watchArrivals
     * 목적: 내비 도착(목적지·경유지)을 logcat에 남긴다.
     * 미구현: routines 창구에 아직 잇지 않아 도착으로 루틴을 권하지 않는다.
     *       장소 도착(주유소·유치원)은 `PlaceWatcher`가 장소를 바꿔 루틴이 본다
     */
    private fun watchArrivals() {
        val n = navi ?: return // 내비 창구, 가짜 차면 null
        scope.launch { n.arrivals.collect { Log.i(TAG, "내비 도착: ${it.name}, 목적지 ${it.isDestination}") } }
    }
}
