package com.westone.sunstone.routines.port

import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.RouteSummary
import java.time.LocalDate
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

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: NaviPort
 * 목적: 기능 → 내비 창구. 목적지를 찾고 경로·경유지를 정한다. Pleos 구현은
 *       app 모듈의 `PleosNaviPort`, 테스트는 `FakeNavi`.
 */
interface NaviPort {
    /** 이름으로 장소를 찾는다. 못 찾으면 null */
    suspend fun findPlace(query: String): PlaceHit?

    /** 목적지로 경로를 시작한다. 실패하면 null */
    suspend fun startRoute(destination: PlaceHit): RouteSummary?

    /** 지금 경로에 첫 번째 경유지를 넣는다. 실패하면 null */
    suspend fun addWaypoint(place: PlaceHit): RouteSummary?
}

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: InfoPort
 * 목적: 기능 → 바깥 정보 창구(주유소, 날씨). 키가 없으면 앱이 고정 응답으로
 *       채운다.
 */
interface InfoPort {
    /** 지금 위치 근처 주유소들. 못 찾으면 빈 목록 */
    suspend fun gasStations(): List<GasStation>

    /** 앞으로 [days]일 안에 비 소식이 있으면 true, 없으면 false, 모르면 null */
    suspend fun rainWithin(days: Int): Boolean?
}

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-12 16:10:00 · 박희정
 * 클래스: MemoryPort
 * 목적: 기능 → 기억 창구. 앱을 다시 켜도 남아야 하는 사용 기록을 둔다.
 *       - 마지막 세차일 — 세차 추천(시나리오 ①)
 *       - 마지막 차일드락 날 — "차일드락 이어서 할까요?"(시나리오 ② #5)
 */
interface MemoryPort {
    /** 마지막으로 세차 모드를 쓴 날. 기록이 없으면 null */
    fun lastWashDate(): LocalDate?

    /** 세차한 날을 남긴다 */
    fun recordWash(date: LocalDate)

    /** 마지막으로 차일드락을 쓴 날. 기록이 없으면 null */
    fun lastChildLockDate(): LocalDate?

    /** 차일드락을 쓴 날을 남긴다 */
    fun recordChildLock(date: LocalDate)
}

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-12 16:10:00 · 박희정
 * 클래스: InMemoryMemory
 * 목적: 메모리에만 두는 기억. 테스트와 가짜 차 시연에 쓴다.
 * 인자: lastWash — 처음 마지막 세차일. 없으면 null
 */
class InMemoryMemory(private var lastWash: LocalDate? = null) : MemoryPort {

    /** 마지막으로 차일드락을 쓴 날. `recordChildLock()`이 채운다. 없으면 null */
    private var lastChildLock: LocalDate? = null

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: lastChildLockDate
     * 목적: 마지막으로 차일드락을 쓴 날을 돌려준다.
     */
    override fun lastChildLockDate() = lastChildLock

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: recordChildLock
     * 목적: 차일드락을 쓴 날을 남긴다.
     */
    override fun recordChildLock(date: LocalDate) {
        lastChildLock = date
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: lastWashDate
     * 목적: 마지막 세차일을 돌려준다.
     */
    override fun lastWashDate() = lastWash

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: recordWash
     * 목적: 세차한 날을 남긴다.
     */
    override fun recordWash(date: LocalDate) {
        lastWash = date
    }
}

class InMemorySeatProfiles : SeatProfiles {
    private val map = linkedMapOf<String, SeatProfile>()
    override fun get(name: String) = map[name]
    override fun save(profile: SeatProfile) { map[profile.name] = profile }
    override fun names() = map.keys.toList()
}
