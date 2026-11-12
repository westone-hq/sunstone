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
 * 수정: 2026-11-12 18:28:00 · 박건준
 * 클래스: TripDialog
 * 목적: 길안내 대화(docs/SCREEN.md 3장 ①-2~①-6). 목적지를 말하면 길안내를
 *       시작하고, 연료가 낮으면 주유소를 찾아 묻는다. "네"를 받으면 그
 *       주유소를 경유지로 넣고, 마지막 세차일과 날씨를 보고 세차를 묻는다.
 *       세차도 하겠다면 예약해 두고, 세차 입구에 서면 세차 모드가 묻는다
 *       (`WashRoutine`).
 *       - 차를 바꾸지 않는다. 경로·경유지만 바꾼다
 *       - 경유지를 넣는 것은 경로를 바꾸므로 "네"를 받은 뒤에만 한다
 *       - 한 번에 질문 하나. 질문은 `Asking`에 올리고 대답이 오면 내린다
 *       - 달리는 중에는 새로 권하지 않는다
 * 인자: car — 연료·정차 여부를 읽는다
 *       navi — 목적지 찾기, 경로·경유지
 *       info — 주유소, 날씨
 *       memory — 마지막 세차일
 *       booking — 세차 예약. `WashRoutine`과 같은 것을 넘긴다
 *       asking — 질문·토스트. `RoutineRunner`·`WashRoutine`과 같은 것을 넘긴다
 *       speaker — 말하기
 *       today — 오늘 날짜. 테스트에서 고정한다
 * 근거: 인용 — Tab 1 2장 결정 규칙: 앱이 먼저 권한 것·경로를 바꾸는 것은 "네"
 *       받고 실행. 대화 정책: 주행 중 새 제안 금지. docs/SCREEN.md 3장 대화 규칙
 */
class TripDialog(
    private val car: CarPort,
    private val navi: NaviPort,
    private val info: InfoPort,
    private val memory: MemoryPort,
    private val booking: WashBooking,
    private val asking: Asking,
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

    /** 주유 제안에 넣을 주유소. 주유를 묻고 대답을 기다리는 동안만 있고, 아니면 null */
    @Volatile
    private var offered: GasStation? = null

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 20:00:00 · 박건준
     * 함수: navigate
     * 목적: 목적지를 찾아 길안내를 시작하고 "○○으로 안내할게요."만 말한다.
     *       연료가 낮고 정차 중이면 주유소를 먼저 찾아, 찾았을 때만 주유를
     *       묻는다(①-3). 새 길안내를 시작하면 이전 질문·거절 기록·세차 예약을
     *       지운다.
     * 인자: query — 운전자가 말한 목적지("파라다이스 호텔 부산")
     * 식: 주유 제안 = fuel ≤ LOW_FUEL_PERCENT 이고 정차 중이고 주유소를 찾음
     *     fuel — `CarState.fuelPercent`(모르면 제안하지 않음)
     * 근거: 인용 — docs/SCREEN.md 3장 ①-2·①-3. 2장 다듬을 점 3: S1은 한 번에
     *       길안내·연료·제안 3가지를 말했다
     */
    suspend fun navigate(query: String) = lock.withLock {
        asking.newTrip() // → 로직 이동: Asking.newTrip()
        booking.clear()
        offered = null
        val destination = navi.findPlace(query) // 찾은 목적지, 못 찾으면 null
        if (destination == null) {
            speaker.say(TripLines.notFound(query)) // → 로직 이동: Speaker.say()
            return@withLock
        }
        if (navi.startRoute(destination) == null) { // → 로직 이동: NaviPort.startRoute()
            speaker.say(TripLines.ROUTE_FAILED)
            return@withLock
        }
        speaker.say(TripLines.routeStarted(destination.name))
        val state = car.state.value // 지금 차 상태
        val fuel = state.fuelPercent // 연료(%), 모르면 null
        if (fuel == null || fuel > LOW_FUEL_PERCENT || !state.stopped) return@withLock
        val pick = pickStation(info.gasStations()) ?: return@withLock // 넣을 주유소, 못 찾으면 묻지 않는다
        offered = pick
        asking.ask( // → 로직 이동: Asking.ask()
            Question(
                kind = QuestionKind.FUEL_STOP,
                title = TripLines.FUEL_TITLE,
                detail = TripLines.FUEL_DETAIL,
                rows = listOf(
                    QuestionRow("fuel", pick.place.name, note = if (pick.carWash) TripLines.CAR_WASH_OK else null),
                ),
                yesLabel = TripLines.FUEL_YES,
                noLabel = TripLines.NO_THANKS,
                hint = TripLines.FUEL_HINT,
            ),
        )
        speaker.say(TripLines.fuelOffer(fuel))
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:28:00 · 박건준
     * 함수: answer
     * 목적: 길안내 대화가 올린 질문(`Asking`)에 대한 대답을 처리한다. 말로 한
     *       대답과 패널 버튼이 모두 여기로 온다.
     *       - 주유 제안: "네"면 주유소를 경유지로 넣는다
     *       - 세차 추천: "네"면 세차를 예약하고 세차 입구에서 맞춰 드린다고 한다
     *       - "아니요"면 거절로 남겨(이번 길에서 다시 묻지 않음) 알았다고 한다
     * 인자: yes — 받아들이면 true
     * 반환: 물은 것이 있어서 대답으로 처리했으면 true. 아니면 아무것도 하지
     *       않고 false(부르는 쪽이 "못 알아들었어요"로 처리)
     */
    suspend fun answer(yes: Boolean): Boolean = lock.withLock {
        val kind = when { // 기다리는 질문
            asking.waiting(QuestionKind.FUEL_STOP) -> QuestionKind.FUEL_STOP
            asking.waiting(QuestionKind.WASH_RECOMMEND) -> QuestionKind.WASH_RECOMMEND
            else -> return@withLock false
        }
        if (!yes) {
            asking.decline(kind) // → 로직 이동: Asking.decline()
            offered = null
            speaker.say(TripLines.DECLINED)
            return@withLock true
        }
        asking.answered(kind) // → 로직 이동: Asking.answered()
        if (kind == QuestionKind.FUEL_STOP) {
            addGasStop()
        } else {
            booking.reserve()
            speaker.say(TripLines.WASH_BOOKED)
            asking.notice(TripLines.WASH_BOOKED_NOTICE)
        }
        true
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 20:00:00 · 박건준
     * 함수: addGasStop
     * 목적: 물었던 주유소를 경유지로 넣고, 세차를 추천할지 정해 묻는다.
     *       1. 경유지를 넣고 "○○를 경유지로 넣었어요." + 토스트
     *       2. 세차 되는 곳이고, 마지막 세차가 `WASH_DUE_DAYS`일 이상 전이고,
     *          `RAIN_DAYS`일 안에 비 소식이 없으면 세차를 묻는다(①-5)
     *       3. 마지막 세차일·날씨를 모르면 묻지 않는다
     * 근거: 인용 — docs/SCREEN.md 3장 ①-4·①-5
     */
    private suspend fun addGasStop() {
        val pick = offered ?: return // 넣을 주유소
        offered = null
        if (navi.addWaypoint(pick.place) == null) { // → 로직 이동: NaviPort.addWaypoint()
            speaker.say(TripLines.WAYPOINT_FAILED)
            return
        }
        speaker.say(TripLines.waypointAdded(pick.place.name))
        asking.notice(TripLines.WAYPOINT_NOTICE)
        val days = memory.lastWashDate()?.let { ChronoUnit.DAYS.between(it, today()) } // 마지막 세차 뒤 날 수
        val rain = if (pick.carWash) info.rainWithin(RAIN_DAYS) else null // 비 소식, 모르면 null
        if (!pick.carWash || days == null || days < WASH_DUE_DAYS || rain != false) return
        // → 로직 이동: Asking.ask()
        val asked = asking.ask( // 세차 추천 패널을 올렸는지
            Question(
                kind = QuestionKind.WASH_RECOMMEND,
                title = TripLines.WASH_TITLE,
                detail = "",
                rows = listOf(
                    QuestionRow("calendar", TripLines.lastWashRow(days)),
                    QuestionRow("sun", TripLines.noRainRow(RAIN_DAYS)),
                ),
                yesLabel = TripLines.WASH_YES,
                noLabel = TripLines.NO_THANKS,
                hint = TripLines.WASH_HINT,
            ),
        )
        if (asked) speaker.say(TripLines.washRecommend(days))
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
