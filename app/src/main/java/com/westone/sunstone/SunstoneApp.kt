package com.westone.sunstone

import android.app.Application
import android.content.Context
import android.util.Log
import com.westone.sunstone.car.AaosEnergySource
import com.westone.sunstone.car.AaosSpeedSource
import com.westone.sunstone.car.PleosCarPort
import com.westone.sunstone.connect.AndroidSpeaker
import com.westone.sunstone.connect.PrefsSeatProfiles
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.port.CarPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 클래스: AppGraph
 * 목적: 기능 구현(routines)과 앱 연결을 한곳에서 잇는 조립부. 의존성 주입
 *       라이브러리 없이 손으로 만든다.
 *       - 차는 Pleos를 쓰고, 데모 설정(`fake on`)이거나 Pleos 연결에
 *         실패하면 가짜 차를 쓴다
 *       - 말·Gleo·화면·데모 입력은 모두 `handle()`로 들어온다
 * 인자: app — 앱 Context
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

    /** 루틴 실행기. 만들 때 상황 감지를 시작한다 */
    val runner = RoutineRunner(
        car = car,
        routines = listOf(WashRoutine(), ChildLockRoutine(), seatRoutine),
        speaker = speaker,
        scope = scope,
    ).also { it.start() } // → 로직 이동: RoutineRunner.start()

    /** 속도 센서. 값을 Pleos 차 창구에 넣는다. 가짜 차일 때는 버린다 */
    private val speed = AaosSpeedSource(app) { kmh -> pleos?.onSensorSpeed(kmh) }

    /** AAOS 에너지 속성. 값을 Pleos 차 창구에 넣는다. 가짜 차일 때는 버린다 */
    private val energy = AaosEnergySource(app) { id, value -> pleos?.onAaosEnergy(id, value) }

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
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 19:11:42 · 박건준
     * 함수: handle
     * 목적: 말·Gleo·화면·데모 입력을 처리한다.
     *       - `Run` — 루틴 실행을 요청한다
     *       - `Undo` — 이름이 없으면 가장 최근에 켜진 루틴을 되돌린다
     *       - `SaveSeat` — 지금 시트 자세를 이름으로 저장한다
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
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: setPlace
     * 목적: 데모 입력으로 지금 장소를 넣는다. 쓰고 있는 차에만 들어간다.
     * 인자: place — 지금 장소
     */
    fun setPlace(place: Place) {
        pleos?.setPlace(place) // → 로직 이동: PleosCarPort.setPlace()
        fake?.update { it.copy(place = place) }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: overrideSpeed
     * 목적: 데모 입력으로 속도를 덮어쓴다.
     * 인자: kmh — 속도(km/h). null이면 센서 값으로 돌아간다(가짜 차는 0)
     */
    fun overrideSpeed(kmh: Float?) {
        pleos?.overrideSpeed(kmh) // → 로직 이동: PleosCarPort.overrideSpeed()
        fake?.update { it.copy(speedKmh = kmh ?: 0f) }
    }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: overrideFuel
     * 목적: 데모 입력으로 연료(%)를 덮어쓴다. 가짜 차에는 연료 칸이 없어
     *       Pleos 차에만 들어간다.
     * 인자: percent — 연료(0~100%). null이면 차 값으로 돌아간다
     */
    fun overrideFuel(percent: Int?) {
        pleos?.overrideFuel(percent) // → 로직 이동: PleosCarPort.overrideFuel()
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
}
