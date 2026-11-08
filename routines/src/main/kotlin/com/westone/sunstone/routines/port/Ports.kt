package com.westone.sunstone.routines.port

import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.model.Window
import kotlinx.coroutines.flow.StateFlow

/*
 * 기능 구현(routines)과 앱 연결(app)이 만나는 자리.
 * routines는 아래 인터페이스만 알고, 실제 차·음성은 app 모듈이 구현해서 넘긴다.
 */

/** 앱이 차에서 바꿀 수 있는 대상. 바꾸기 전 값을 읽어 두었다가 그대로 되돌린다. */
sealed interface Target {
    /** 값: Int. 0 = 닫힘, 10 = 완전히 열림(Pleos 창문 단위). */
    data class WindowPosition(val window: Window) : Target

    /** 값: Boolean. 창문 스위치 잠금. */
    data class WindowLock(val window: Window) : Target

    /** 값: WiperMode. 앞 유리 와이퍼 스위치. */
    data object Wiper : Target

    /** 값: Boolean. true = 내기 순환. */
    data object Recirculation : Target

    /** 값: Boolean. true = 모든 문 잠금. */
    data object DoorLock : Target

    /** 값: Boolean. true = 뒷유리 블라인드 올림. */
    data object RearBlind : Target

    /** 값: Boolean. 해당 좌석 공조 켜짐. */
    data class Climate(val seat: Seat) : Target

    /** 값: Int. 시트 축 위치(차가 주는 단위 그대로). */
    data class SeatPosition(val seat: Seat, val axis: SeatAxis) : Target

    /** 값: Int. 차에 저장된 시트 메모리 번호. 읽을 수 없어서 되돌리기에는 쓰지 않는다. */
    data class SeatMemory(val seat: Seat) : Target
}

data class Command(val target: Target, val value: Any)

sealed interface ControlResult {
    /** 실행한 뒤 다시 읽어서 바뀐 걸 확인했다. */
    data object Done : ControlResult

    /** 보냈지만 읽어서 확인할 방법이 없다. */
    data object Sent : ControlResult

    /** 이 차(또는 에뮬레이터)가 지원하지 않는다. */
    data class Unsupported(val reason: String) : ControlResult

    /** 시도했지만 실패했다. */
    data class Failed(val reason: String) : ControlResult

    val ok: Boolean get() = this is Done || this is Sent
}

/** 차. Pleos 구현은 app 모듈의 PleosCarPort, 테스트·데모는 FakeCar. */
interface CarPort {
    val state: StateFlow<CarState>

    /** 현재 값. 읽을 수 없으면 null. */
    suspend fun read(target: Target): Any?

    suspend fun apply(command: Command): ControlResult
}

/** 말하기. 미리 녹음한 음성이든 TTS든 app 모듈이 고른다. */
fun interface Speaker {
    fun say(text: String)
}

/** 시트 자세 프로필 저장소. */
interface SeatProfiles {
    fun get(name: String): SeatProfile?
    fun save(profile: SeatProfile)
    fun names(): List<String>
}

/**
 * 한 사람의 운전 자세.
 * [positions]는 앱이 읽어 둔 축별 위치, [memorySlot]은 차에 저장된 메모리 번호(있으면).
 */
data class SeatProfile(
    val name: String,
    val positions: Map<SeatAxis, Int>,
    val memorySlot: Int? = null,
)

class InMemorySeatProfiles : SeatProfiles {
    private val map = linkedMapOf<String, SeatProfile>()
    override fun get(name: String) = map[name]
    override fun save(profile: SeatProfile) { map[profile.name] = profile }
    override fun names() = map.keys.toList()
}
