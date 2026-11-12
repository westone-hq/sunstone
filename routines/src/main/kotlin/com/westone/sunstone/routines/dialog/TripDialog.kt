package com.westone.sunstone.routines.dialog

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.routines.port.MemoryPort
import com.westone.sunstone.routines.port.NaviPort
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.speech.TripLines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: TripDialog
 * 목적: 길안내 대화(Tab 1 시나리오 ① #1~#2). 목적지를 말하면 길안내를 시작하고,
 *       연료가 낮으면 주유를 권한다. "네"를 받으면 세차 되는 주유소 중 가장 싼
 *       곳을 경유지로 넣고, 마지막 세차일과 날씨를 보고 세차를 추천·예약한다.
 *       예약한 주유소에 도착하면 세차 모드가 권한다(#3, `WashRoutine`).
 *       - 차를 바꾸지 않는다. 경로·경유지만 바꾼다
 *       - 경유지를 넣는 것은 경로를 바꾸므로 "네"를 받은 뒤에만 한다
 *       - 달리는 중에는 새로 권하지 않는다
 * 인자: car — 연료·정차 여부를 읽는다
 *       navi — 목적지 찾기, 경로·경유지
 *       info — 주유소, 날씨
 *       memory — 마지막 세차일
 *       booking — 세차 예약. `WashRoutine`과 같은 것을 넘긴다
 *       speaker — 말하기
 *       today — 오늘 날짜. 테스트에서 고정한다
 * 근거: 인용 — Tab 1 2장 결정 규칙: 앱이 먼저 권한 것·경로를 바꾸는 것은 "네"
 *       받고 실행. 대화 정책: 주행 중 새 제안 금지
 */
class TripDialog(
    private val car: CarPort,
    private val navi: NaviPort,
    private val info: InfoPort,
    private val memory: MemoryPort,
    private val booking: WashBooking,
    private val speaker: Speaker,
    private val today: () -> LocalDate,
) {

    private companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: LOW_FUEL_PERCENT
         * 목적: 이 값(%) 이하면 연료가 부족하다고 보고 주유를 권한다.
         * 근거: 미정 — 시나리오 값 18%가 걸리고, 장거리 출발 전 넉넉히 넣을
         *       때를 잡으려고 25%로 정했다
         */
        const val LOW_FUEL_PERCENT = 25

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: WASH_DUE_DAYS
         * 목적: 마지막 세차 뒤 이 날 수 이상 지났으면 세차를 추천한다.
         * 근거: 미정 — 시나리오 값 36일이 걸리고, 2주에 한 번 정도를 기준으로 했다
         */
        const val WASH_DUE_DAYS = 14

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: RAIN_DAYS
         * 목적: 비 소식을 볼 기간(일). 이 안에 비가 오면 세차를 추천하지 않는다.
         * 근거: 인용 — Tab 1 4.3 시나리오 ① #2 "일주일간 비 소식이 없어"
         */
        const val RAIN_DAYS = 7
    }

    /** 한 번에 한 요청만 처리한다. 경로 요청은 10초 넘게 걸릴 수 있다 */
    private val lock = Mutex()

    /** 주유를 권하고 대답을 기다리는 중이면 true */
    @Volatile
    private var offeringFuel = false

    /** 세차를 추천하고 대답을 기다리는 중이면 true */
    @Volatile
    private var askingWash = false

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 11:58:00 · 박희정
     * 함수: navigate
     * 목적: 목적지를 찾아 길안내를 시작하고, 연료가 낮으면 주유를 권한다
     *       (#1). 새 길안내를 시작하면 이전 세차 예약과 주유 제안은 지운다.
     * 인자: query — 운전자가 말한 목적지("파라다이스 호텔 부산")
     * 식: 주유 제안 = fuel ≤ LOW_FUEL_PERCENT 이고 정차 중
     *     fuel — `CarState.fuelPercent`(모르면 제안하지 않음)
     *     고속도로 문장 = tollWon > 0 (`RouteSummary.tollWon`)
     * 근거: 측정 — 에뮬레이터 내비는 고속도로 경로에 통행료를 준다(서울 →
     *       해운대 16,010원, docs/api-spike.md 길안내 행). 내비가 도로 종류를
     *       따로 주지 않아 통행료로 고속도로를 가늠한다
     */
    suspend fun navigate(query: String) = lock.withLock {
        offeringFuel = false
        askingWash = false
        booking.clear()
        val destination = navi.findPlace(query) // 찾은 목적지, 못 찾으면 null
        if (destination == null) {
            speaker.say(TripLines.notFound(query)) // → 로직 이동: Speaker.say()
            return@withLock
        }
        val route = navi.startRoute(destination) // → 로직 이동: NaviPort.startRoute()
        if (route == null) {
            speaker.say(TripLines.ROUTE_FAILED)
            return@withLock
        }
        val state = car.state.value // 지금 차 상태
        val fuel = state.fuelPercent // 연료(%), 모르면 null
        if (fuel == null || fuel > LOW_FUEL_PERCENT || !state.stopped) {
            speaker.say(TripLines.ROUTE_STARTED)
            return@withLock
        }
        offeringFuel = true
        val offer = if (route.tollWon > 0) TripLines.FUEL_BEFORE_HIGHWAY else TripLines.FUEL_ON_THE_WAY // 권하는 말
        speaker.say("${TripLines.ROUTE_STARTED} $offer")
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 11:58:00 · 박희정
     * 함수: answer
     * 목적: 길안내 대화가 물은 것에 대한 대답을 처리한다.
     *       - 주유 제안(#2): "네"면 주유소를 경유지로 넣고, "아니요"면 알았다고
     *         한다
     *       - 세차 추천: "네"면 예약을 두고 도착하면 맞춰 드린다고 하고,
     *         "아니요"면 예약을 지우고 알았다고 한다
     * 인자: yes — 받아들이면 true
     * 반환: 물은 것이 있어서 대답으로 처리했으면 true. 아니면 아무것도 하지
     *       않고 false(부르는 쪽이 "못 알아들었어요"로 처리)
     */
    suspend fun answer(yes: Boolean): Boolean = lock.withLock {
        when {
            offeringFuel -> {
                offeringFuel = false
                if (yes) addGasStop() else speaker.say(TripLines.DECLINED)
            }
            askingWash && booking.atGasStation -> {
                askingWash = false
                if (!yes) booking.clear()
                speaker.say(if (yes) TripLines.WASH_BOOKED else TripLines.DECLINED)
            }
            else -> {
                askingWash = false
                return@withLock false
            }
        }
        true
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 11:58:00 · 박희정
     * 함수: addGasStop
     * 목적: 주유소를 골라 경유지로 넣고, 세차를 추천할지 정해 말한다.
     *       1. 세차 되는 주유소 중 가장 싼 곳. 없으면 그냥 가장 싼 곳
     *       2. 세차 되는 곳이고, 마지막 세차가 `WASH_DUE_DAYS`일 이상 전이고,
     *          `RAIN_DAYS`일 안에 비 소식이 없으면 세차를 추천하고 예약한다.
     *          추천하면 대답("그래 그러자"·"아니 괜찮아")을 기다린다
     *       3. 마지막 세차일·날씨를 모르면 그 문장을 빼고 추천하지 않는다
     */
    private suspend fun addGasStop() {
        val stations = info.gasStations() // → 로직 이동: InfoPort.gasStations()
        val pick = pickStation(stations) // 넣을 주유소, 없으면 null
        if (pick == null) {
            speaker.say(TripLines.NO_STATION)
            return
        }
        if (navi.addWaypoint(pick.place) == null) { // → 로직 이동: NaviPort.addWaypoint()
            speaker.say(TripLines.WAYPOINT_FAILED)
            return
        }
        val days = memory.lastWashDate()?.let { ChronoUnit.DAYS.between(it, today()) } // 마지막 세차 뒤 날 수
        val rain = if (pick.carWash) info.rainWithin(RAIN_DAYS) else null // 비 소식, 모르면 null
        val recommend = pick.carWash && days != null && days >= WASH_DUE_DAYS && rain == false // 세차 추천
        if (recommend) booking.reserve()
        askingWash = recommend
        speaker.say(TripLines.waypointAdded(pick.place.name, pick.carWash, days, recommend))
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: pickStation
     * 목적: 세차 되는 주유소 중 가장 싼 곳을 고른다. 세차 되는 곳이 없으면
     *       가장 싼 곳.
     * 인자: stations — 근처 주유소들
     * 반환: 고른 주유소. 목록이 비었으면 null
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #2 "가격이 괜찮고 세차도 되는"
     */
    private fun pickStation(stations: List<GasStation>): GasStation? =
        stations.filter { it.carWash }.minByOrNull { it.priceWon } ?: stations.minByOrNull { it.priceWon }
}
