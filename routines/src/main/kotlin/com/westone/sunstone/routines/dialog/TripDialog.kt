package com.westone.sunstone.routines.dialog

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.routines.port.MemoryPort
import com.westone.sunstone.routines.port.NaviPort
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.speech.TripLines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: TripDialog
 * 목적: 길안내 대화(docs/SCREEN.md 6장 시나리오 ① 1~3단계). 목적지를 말하면
 *       길안내를 시작하고, 연료가 낮으면 주유소를 찾아 한 번 묻는다. 마지막
 *       세차일과 날씨가 맞으면 같은 카드에서 세차도 묻는다. 세차까지 하겠다면
 *       예약해 두고, 세차 입구에 서면 세차 모드가 5초 뒤 켜진다
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
 *       log — 실패를 남길 곳(앱은 logcat). 없으면 버린다
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
    private val log: (String) -> Unit = {},
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

        /**
         * 작성: 2026-11-13 04:20:00 · 박건준
         * 변수: WAYPOINT_WAIT_MS
         * 목적: 내비가 경유지를 넣기를 기다리는 가장 긴 시간(밀리초). 넘으면
         *       실패로 본다.
         * 근거: 인용 — 2026-11-13 박건준 결정 20초. 측정 — 에뮬레이터에서 대답 뒤
         *       `onWaypointChanged`까지 약 9초(docs/api-spike.md 경유지 넣기 시간 행)
         */
        const val WAYPOINT_WAIT_MS = 20_000L
    }

    /** 한 번에 한 요청만 처리한다. 경로 요청은 10초 넘게 걸릴 수 있다 */
    private val lock = Mutex()

    /** 주유 제안에 넣을 주유소. 주유를 묻고 대답을 기다리는 동안만 있고, 아니면 null */
    @Volatile
    private var offered: GasStation? = null

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: navigate
     * 목적: 목적지를 찾아 길안내를 시작하고 "○○으로 안내할게요."만 말한다.
     *       연료가 낮고 정차 중이면 주유소를 찾아, 찾았을 때만 한 번 묻는다.
     *       세차 되는 곳이고 세차할 때(`washDays()`)면 같은 카드에서 세차도
     *       묻는다. 새 길안내를 시작하면 이전 질문·거절 기록·세차 예약을 지운다.
     * 인자: query — 운전자가 말한 목적지("파라다이스 호텔 부산")
     * 식: 주유 제안 = fuel ≤ LOW_FUEL_PERCENT 이고 정차 중이고 주유소를 찾음
     *     fuel — `CarState.fuelPercent`(모르면 제안하지 않음)
     * 근거: 인용 — docs/SCREEN.md 6장 시나리오 ① 다시 짠 흐름 1·2
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
        val days = if (pick.carWash) washDays() else null // 세차도 권할 때 마지막 세차 뒤 날 수, 아니면 null
        asking.ask(fuelQuestion(pick, fuel, days)) // → 로직 이동: Asking.ask()
        speaker.say(TripLines.fuelOffer(fuel, pick.place.name, days))
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: fuelQuestion
     * 목적: 주유 카드를 만든다.
     *       - 세차도 권하면 "주유하고 세차할까요?", 근거 칸 셋, [주유하고 세차]
     *         [주유만]. 닫기가 거절이다
     *       - 아니면 "주유할까요?", 근거 칸 연료 하나, [경유지로 넣기] [괜찮아요]
     * 인자: pick — 넣을 주유소
     *       fuel — 연료(%)
     *       days — 마지막 세차 뒤 날 수. 세차를 권하지 않으면 null
     * 반환: 주유 질문
     * 근거: 인용 — docs/SCREEN.md 6장 2단계, 2026-11-13 박건준 2차 업그레이드
     *       지시(세차 조건이 안 맞을 때 카드)
     */
    private fun fuelQuestion(pick: GasStation, fuel: Int, days: Long?): Question {
        val name = pick.place.name // 주유소 이름, 설명에서 굵게
        val fuelFact = Fact("fuel", TripLines.fuelFact(fuel)) // 연료 근거 칸
        if (days == null) {
            return Question(
                kind = QuestionKind.FUEL_STOP,
                title = TripLines.FUEL_TITLE,
                detail = TripLines.fuelDetail(name, carWash = false),
                emphasis = name,
                facts = listOf(fuelFact),
                choices = listOf(Choice(TripLines.FUEL_YES, Reply.YES), Choice(TripLines.NO_THANKS, Reply.NO)),
            )
        }
        return Question(
            kind = QuestionKind.FUEL_STOP,
            title = TripLines.FUEL_WASH_TITLE,
            detail = TripLines.fuelDetail(name, carWash = true),
            emphasis = name,
            facts = listOf(
                fuelFact,
                Fact("calendar", TripLines.washFact(days)),
                Fact("sun", TripLines.noRainFact(RAIN_DAYS)),
            ),
            choices = listOf(Choice(TripLines.FUEL_WASH_ALL, Reply.ALL), Choice(TripLines.FUEL_WASH_ONLY, Reply.ONLY)),
        )
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: washDays
     * 목적: 세차를 권할 때인지 보고, 그렇다면 마지막 세차 뒤 날 수를 준다.
     *       마지막 세차가 `WASH_DUE_DAYS`일 이상 전이고 `RAIN_DAYS`일 안에 비
     *       소식이 없을 때다. 마지막 세차일·날씨를 모르면 권하지 않는다.
     * 반환: 마지막 세차 뒤 날 수. 권하지 않으면 null
     * 근거: 인용 — Tab 1 2장 모르는 사실로 판단하지 않는다. docs/SCREEN.md 6장 2단계
     */
    private suspend fun washDays(): Long? {
        val last = memory.lastWashDate() ?: return null // 마지막 세차일
        val days = ChronoUnit.DAYS.between(last, today()) // 마지막 세차 뒤 날 수
        if (days < WASH_DUE_DAYS) return null
        return if (info.rainWithin(RAIN_DAYS) == false) days else null
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: answer
     * 목적: 주유 질문(`Asking`)에 대한 대답을 처리한다. 말로 한 대답과 카드
     *       버튼·닫기가 모두 여기로 온다.
     *       - 주유하고 세차(`ALL`, 또는 세차 카드에 "응") — 경유지를 넣고 세차를
     *         예약한다
     *       - 주유만·경유지로 넣기 — 경유지만 넣는다
     *       - 거절·닫기 — 거절로 남겨(이번 길에서 다시 묻지 않음) 알았다고 한다
     * 인자: reply — 대답 값
     * 반환: 물은 것이 있어서 대답으로 처리했으면 true. 아니면 아무것도 하지
     *       않고 false(부르는 쪽이 "못 알아들었어요"로 처리)
     * 근거: 인용 — docs/SCREEN.md 6장 3단계. 받아들이면 말 없이 판에만 알린다
     */
    suspend fun answer(reply: Reply): Boolean = lock.withLock {
        val q = asking.question.value?.takeIf { it.kind == QuestionKind.FUEL_STOP } // 기다리는 주유 질문
            ?: return@withLock false
        if (!q.accepts(reply)) {
            asking.decline(QuestionKind.FUEL_STOP) // → 로직 이동: Asking.decline()
            offered = null
            speaker.say(TripLines.DECLINED)
            return@withLock true
        }
        asking.answered(QuestionKind.FUEL_STOP) // → 로직 이동: Asking.answered()
        addGasStop(wash = q.pick(reply)?.reply == Reply.ALL)
        true
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 04:20:00 · 박건준
     * 함수: addGasStop
     * 목적: 물었던 주유소를 경유지로 넣고, 세차도 하면 예약한다. 넣는 동안은
     *       처리 중(`Asking.work`)으로 알리고, 끝나면 판에 토스트("경유지에 넣고
     *       세차를 예약했어요")만 내고 말은 하지 않는다. 내비가 실패하거나
     *       `WAYPOINT_WAIT_MS` 안에 답하지 않으면 실패 토스트와 로그를 남기고
     *       예약하지 않는다.
     * 인자: wash — 세차도 예약하면 true
     * 근거: 인용 — docs/SCREEN.md 6장 3단계, 2026-11-13 박건준 결정(진행 중 판,
     *       20초)
     */
    private suspend fun addGasStop(wash: Boolean) {
        val pick = offered ?: return // 넣을 주유소
        offered = null
        asking.work(true) // → 로직 이동: Asking.work()
        try {
            var answered = false // 내비가 시간 안에 답했는지
            val added = withTimeoutOrNull(WAYPOINT_WAIT_MS) { // 넣은 경로, 실패나 시간 넘김이면 null
                navi.addWaypoint(pick.place).also { answered = true } // → 로직 이동: NaviPort.addWaypoint()
            }
            if (added == null) {
                log(if (answered) "경유지 넣기 실패: ${pick.place.name}" else "경유지 넣기 20초 넘김: ${pick.place.name}")
                asking.notice(TripLines.WAYPOINT_FAILED_NOTICE)
                return
            }
            if (wash) booking.reserve()
            asking.notice(if (wash) TripLines.WAYPOINT_WASH_NOTICE else TripLines.WAYPOINT_NOTICE)
        } finally {
            asking.work(false)
        }
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
