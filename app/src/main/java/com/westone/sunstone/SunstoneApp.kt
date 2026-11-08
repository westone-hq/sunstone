package com.westone.sunstone

import android.app.Application
import android.content.Context
import android.util.Log
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

class SunstoneApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as SunstoneApp).graph

/**
 * 기능(routines)과 앱(연결)을 한곳에서 잇는다. 의존성 주입 라이브러리 없이 손으로 만든다.
 *
 * 차는 기본으로 Pleos를 쓰고, 데모 설정(`fake on`)이면 가짜 차를 쓴다.
 */
class AppGraph(private val app: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = app.getSharedPreferences("sunstone", Context.MODE_PRIVATE)

    val usingFakeCar: Boolean = settings.getBoolean(KEY_FAKE, false)
    val fake: FakeCar? = if (usingFakeCar) FakeCar() else null
    val pleos: PleosCarPort? = if (usingFakeCar) null else runCatching { PleosCarPort(app, scope) }
        .onFailure { Log.w(TAG, "Pleos 연결 실패, 가짜 차로 진행: ${it.message}") }
        .getOrNull()
    val car: CarPort = pleos ?: fake ?: FakeCar()

    val speaker = AndroidSpeaker(app)
    val profiles = PrefsSeatProfiles(app)
    private val seatRoutine = SeatMemoryRoutine(profiles)

    val runner = RoutineRunner(
        car = car,
        routines = listOf(WashRoutine(), ChildLockRoutine(), seatRoutine),
        speaker = speaker,
        scope = scope,
    ).also { it.start() }

    private val speed = AaosSpeedSource(app) { kmh -> pleos?.onSensorSpeed(kmh) }

    /** 속도 권한을 받은 뒤 부른다. */
    fun startSpeed() = speed.start()

    /** 말·Gleo·화면·데모 입력이 모두 여기로 들어온다. */
    fun handle(action: VoiceAction) {
        when (action) {
            is VoiceAction.Run -> runner.request(action.request)
            is VoiceAction.Undo -> {
                val id = action.id ?: runner.ui.value.runs.values
                    .filter { it.phase == Phase.ACTIVE }
                    .maxByOrNull { it.startedAtMs }?.id
                if (id == null) speaker.say("되돌릴 게 없어요.") else runner.undo(id)
            }
            is VoiceAction.SaveSeat -> scope.launch {
                val saved = seatRoutine.capture(action.name, car)
                speaker.say(if (saved != null) "${action.name} 자세를 저장했어요." else "시트 위치를 읽을 수 없어요.")
            }
        }
    }

    // ------------------------------------------------------------------ 데모 입력

    fun setPlace(place: Place) {
        pleos?.setPlace(place)
        fake?.update { it.copy(place = place) }
    }

    fun overrideSpeed(kmh: Float?) {
        pleos?.overrideSpeed(kmh)
        fake?.update { it.copy(speedKmh = kmh ?: 0f) }
    }

    /** 가짜 차일 때만 차 상태를 직접 바꾼다. Pleos면 Virtual Console을 쓴다. */
    fun editFakeCar(block: (CarState) -> CarState): Boolean {
        val f = fake ?: return false
        f.update(block)
        return true
    }

    /** 다음 실행부터 가짜 차를 쓸지. 앱을 다시 켜야 적용된다. */
    fun setUseFakeCar(on: Boolean) = settings.edit().putBoolean(KEY_FAKE, on).apply()

    private companion object {
        const val TAG = "Sunstone"
        const val KEY_FAKE = "use_fake_car"
    }
}
