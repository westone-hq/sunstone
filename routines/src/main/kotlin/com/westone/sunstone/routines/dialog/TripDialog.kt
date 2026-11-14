package com.westone.sunstone.routines.dialog

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.routines.port.MemoryPort
import com.westone.sunstone.routines.port.NaviPort
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.speech.TripLines
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-14 13:41:10 · 박희정
 * 수정: 2026-11-14 16:00:00 · 박건준
 * 클래스: TripDialog
 * 목적: 길안내 대화(docs/SPEC.md 4장, docs/SCREEN.md 6장 시나리오 ① 1~3단계).
 *       목적지를 말하면 길안내를 시작하고, 주유할 때면 주유소를 찾아 한 번
 *       묻는다. 마지막 세차일과 날씨가 맞으면 같은 카드에서 세차도 묻는다.
 *       세차까지 하겠다면 예약해 두고, 세차 입구에 서면 세차 모드가 5초 뒤
 *       켜진다(`WashRoutine`).
 *       - 차를 바꾸지 않는다. 경로·경유지만 바꾼다
 *       - 경유지를 넣는 것은 경로를 바꾸므로 "네"를 받은 뒤에만 한다
 *       - 한 번에 질문 하나. 질문은 `Asking`에 올리고 대답이 오면 내린다
 *       - 달리는 중에는 새로 권하지 않는다
 *       - 목적지 후보가 여럿이면 고른 뒤에만 길안내를 시작한다
 * 인자: car — 연료·정차 여부를 읽는다
 *       navi — 목적지 찾기, 경로·경유지
 *       info — 주유소, 날씨
 *       memory — 마지막 세차일, 세차 권유를 거절한 날
 *       booking — 세차 예약. `WashRoutine`과 같은 것을 넘긴다
 *       asking — 질문·토스트. `RoutineRunner`·`WashRoutine`과 같은 것을 넘긴다
 *       speaker — 말하기
 *       today — 오늘 날짜. 테스트에서 고정한다
 *       currentRoute — 지금 길안내 목적지. `ChildLockRoutine`과 같은 것을 넘긴다
 *       runWash — 비 소식을 알리고도 세차하겠다고 했을 때 세차 모드를 켜는 곳
 *       log —실패를 남길 곳(앱은 logcat). 없으면 버린다
 * 근거: 인용 — 설계 결정 규칙: 앱이 먼저 권한 것·경로를 바꾸는 것은 "네"
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
    private val currentRoute: CurrentRoute,
    private val runWash: () -> Unit = {},
    private val log:(String) -> Unit = {},
) {

    companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: LOW_FUEL_PERCENT
         * 목적: 이 값(%) 이하면 연료가 부족하다고 보고 주유를 권한다.
         * 근거: 인용 — docs/SPEC.md 4장 "연료 25% 이하". 시나리오 값 18%가
         *       걸리고, 장거리 출발 전 넉넉히 넣을 때를 잡는다
         */
        const val LOW_FUEL_PERCENT = 25

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: WASH_DUE_DAYS
         * 목적: 마지막 세차 뒤 이 날 수 이상 지났으면 세차를 권한다.
         * 근거: 인용 — docs/SPEC.md 4장 "마지막 세차가 14일 이상 전". 시나리오 값
         *       36일이 걸리고, 2주에 한 번 정도를 기준으로 했다
         */
        const val WASH_DUE_DAYS = 14

        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 수정: 2026-11-14 16:00:00 · 박건준
         * 변수: RAIN_DAYS
         * 목적: 비 소식을 볼 기간(일). 날씨 근거 칸이 이 기간을 말한다.
         * 근거: 인용 — docs/SPEC.md 4장 "7일 동안 비 없음", 시연 시나리오 ① #2
         *       "일주일간 비 소식이 없어"
         */
        const val RAIN_DAYS = 7

        /**
         * 작성: 2026-11-14 16:00:00 · 박건준
         * 변수: RAIN_SOON_DAYS
         * 목적: 이 날 수 안에(오늘 0 ~ 3일 뒤) 비가 오면 세차를 권하지 않는다.
         * 근거: 인용 — docs/SPEC.md 4장 "앞으로 3일 안에 비 예보가 없음",
         *       "4~7일 뒤에 비가 있으면 N일 뒤 비"(권하되 알린다).
         *       미정 — 3일 뒤 비는 3일 안으로 본다
         */
        const val RAIN_SOON_DAYS = 3

        /**
         * 작성: 2026-11-13 04:20:00 · 박건준
         * 변수: WAYPOINT_WAIT_MS
         * 목적: 내비가 경유지를 넣기를 기다리는 가장 긴 시간(밀리초). 넘으면
         *       실패로 본다.
         * 근거: 인용 — 2026-11-13 박건준 결정 20초. 측정 — 에뮬레이터에서 대답 뒤
         *       `onWaypointChanged`까지 약 9초(docs/api-spike.md 경유지 넣기 시간 행)
         */
        const val WAYPOINT_WAIT_MS = 20_000L

        /**
         * 작성: 2026-11-14 16:00:00 · 박건준
         * 변수: WASH_OFFER
         * 목적: 세차 권유를 거절한 날을 기억할 때 쓰는 이름.
         * 근거: 인용 — docs/SPEC.md 4장 "오늘 세차 권유를 거절한 적이 없음"
         */
        const val WASH_OFFER = "WASH_OFFER"
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 클래스: Offer
     * 목적: 주유를 묻고 대답을 기다리는 동안 들고 있는 것.
     * 인자: station — 카드에 보인 주유소(대답하면 경유지로 넣을 곳)
     *       washStation — 세차 되는 곳 중 가장 싼 곳. 없으면 null
     *       rain — 그때 본 비 예보. 보지 않았거나 받지 못했으면 null
     *       withWash — 카드에서 세차도 같이 물었으면 true
     */
    private data class Offer(
        val station: GasStation,
        val washStation: GasStation?,
        val rain: RainOutlook?,
        val withWash: Boolean,
    )

    /** 한 번에 한 요청만 처리한다. 경로 요청은 10초 넘게 걸릴 수 있다 */
    private val lock = Mutex()

    /** 주유 제안. 주유를 묻고 대답을 기다리는 동안만 있고, 아니면 null */
    @Volatile
    private var offered: Offer? = null

    /** 가장 최근 길안내 요청 번호. 새 요청·시연 재시작이 올리면 앞 요청의 늦은 응답을 버린다 */
    @Volatile
    private var generation = 0L

    /** 고르기를 기다리는 목적지 후보를 바꾸는 곳 */
    private val choices = MutableStateFlow<List<PlaceHit>>(emptyList())

    /** 고르기를 기다리는 목적지 후보(값이 바뀌면 알려주는 상태). 없으면 빈 목록 */
    val destinations: StateFlow<List<PlaceHit>> = choices

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 함수: invalidate
     * 목적: 기다리는 주유 제안과 목적지 후보를 버리고, 아직 돌고 있는 길안내
     *       요청의 늦은 응답(주유소·날씨)이 질문을 올리지 못하게 한다. 시연을
     *       처음으로 돌리거나 목적지 고르기를 그만둘 때 부른다.
     */
    fun invalidate() {
        generation++
        offered = null
        choices.value = emptyList()
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 함수: selectDestination
     * 목적: 후보 중 고른 목적지로 길안내를 시작한다.
     * 인자: index — 후보 번호(0부터)
     */
    suspend fun selectDestination(index: Int) {
        val selected = choices.value.getOrNull(index) ?: return // 고른 목적지
        choices.value = emptyList()
        navigate(selected.name, selected) // → 로직 이동: TripDialog.navigate()
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: navigate
     * 목적: 목적지를 찾아 길안내를 시작하고 "○○으로 안내할게요."만 말한다.
     *       후보가 여럿이면 고르게 하고 멈춘다. 주유할 때이고 정차 중이면
     *       주유소를 찾아 한 번 묻는다(`offerFuel()`). 새 길안내를 시작하면 이전
     *       질문·거절 기록·세차 예약을 지운다. 경로를 시작하면 목적지를
     *       `CurrentRoute`에 남긴다.
     * 인자: query — 운전자가 말한 목적지("파라다이스 호텔 부산")
     *       selected — 후보 중 고른 목적지. 말로 시작했으면 null
     * 식: 주유를 권함 = fuel ≤ LOW_FUEL_PERCENT 또는 range × 1000 < distance
     *     fuel — `CarState.fuelPercent`(%), range — `CarState.rangeKm`(km),
     *     distance — 경로 거리(m). 모르는 값은 그 조건을 보지 않는다
     * 근거: 인용 — docs/SPEC.md 4장 주유를 권하는 때, docs/SCREEN.md 6장
     *       시나리오 ① 다시 짠 흐름 1·2
     */
    suspend fun navigate(query: String, selected: PlaceHit? = null) {
        val request = ++generation // 이 요청 번호. 더 새 요청이 오면 그만둔다
        lock.withLock {
            if (request != generation) return@withLock
            asking.newTrip() // → 로직 이동: Asking.newTrip()
            booking.clear()
            currentRoute.clear()
            offered = null
            choices.value = emptyList()
            val candidates = if (selected != null) listOf(selected) else navi.findPlaces(query) // 목적지 후보
            if (request != generation) return@withLock
            if (candidates.size > 1) {
                choices.value = candidates
                speaker.say(TripLines.CHOOSE_DESTINATION) // → 로직 이동: Speaker.say()
                return@withLock
            }
            val destination = candidates.singleOrNull() // 찾은 목적지, 못 찾으면 null
            if (destination == null) {
                speaker.say(TripLines.notFound(query))
                return@withLock
            }
            val route = navi.startRoute(destination) // 시작한 경로, 실패하면 null. → 로직 이동: NaviPort.startRoute()
            if (route == null) {
                speaker.say(TripLines.ROUTE_FAILED)
                return@withLock
            }
            if (request != generation) return@withLock
            currentRoute.start(destination)
            speaker.say(TripLines.routeStarted(destination.name))
            val state = car.state.value // 지금 차 상태
            val low = state.fuelPercent?.let { it <= LOW_FUEL_PERCENT } == true // 연료가 낮은지
            val short = state.rangeKm?.let { it * 1000L < route.distanceM } == true // 목적지까지 못 가는지
            if ((!low && !short) || !state.stopped) return@withLock
            offerFuel(request) // → 로직 이동: TripDialog.offerFuel()
        }
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: offerFuel
     * 목적: 주유소를 찾아 주유를 묻는다. 세차를 함께 권할 때면 세차 되는 곳 중
     *       가장 싼 곳을, 아니면 가장 싼 곳을 고른다.
     *       1. 오늘 주유 권유를 거절했으면 묻지 않는다
     *       2. 주유소를 찾는다. 없으면 묻지 않는다
     *       3. 세차할 때인지 본다: 마지막 세차가 `WASH_DUE_DAYS`일 이상 전이고,
     *          오늘 세차 권유를 거절하지 않았고, 세차 되는 주유소가 있다
     *       4. 세차할 때면 그 주유소의 비 예보를 본다. `RAIN_SOON_DAYS`일 안에
     *          비가 오면 세차는 권하지 않고 근거 칸에 가장 가까운 비를 보인다.
     *          날씨를 받지 못했으면 날씨 칸을 빼고 세차일만으로 권한다
     *       5. 그동안 차가 움직였거나 더 새 요청이 왔으면 묻지 않는다
     * 인자: request — 이 길안내 요청 번호
     * 근거: 인용 — docs/SPEC.md 4장 세차를 함께 권하는 때, 질문과 근거 칸,
     *       주유소 고르기, 날씨를 받지 못했을 때
     */
    private suspend fun offerFuel(request: Long) {
        if (asking.declinedToday(QuestionKind.FUEL_STOP)) return
        val stations = working { info.gasStations() } // 근처 주유소. → 로직 이동: InfoPort.gasStations()
        val cheapest = cheapest(stations) // 가장 싼 곳
        if (cheapest == null) {
            if (info.nearbyStations && request == generation) speaker.say(TripLines.STATIONS_UNAVAILABLE)
            return
        }
        val washStation = cheapest(stations.filter { it.carWash && it.facilityKnown }) // 세차 되는 곳 중 가장 싼 곳
        val days = washDays() // 마지막 세차 뒤 날 수, 세차할 때가 아니면 null
        val due = days != null && washStation != null && memory.declinedOn(WASH_OFFER) != today() // 세차할 때인지
        // → 로직 이동: InfoPort.rain()
        val rain = if (due) working { info.rain(washStation!!.place.point, RAIN_DAYS) } else null // 비 예보
        if (request != generation || !car.state.value.stopped) return
        val soon = rain?.day?.takeIf { it <= RAIN_SOON_DAYS } // 곧 비가 오는 날, 아니면 null
        val wash = due && soon == null // 세차도 함께 권하는지
        val pick = if (wash) washStation!! else cheapest // 카드에 보일 주유소
        val state = car.state.value // 지금 차 상태
        val question = fuelQuestion(pick, state.fuelPercent, state.rangeKm, days.takeIf { wash }, rain, soon) // 주유 카드
        if (!asking.ask(question)) return // → 로직 이동: Asking.ask()
        offered = Offer(pick, washStation, rain, wash)
        speaker.say(
            TripLines.fuelOffer(
                state.fuelPercent, pick.place.name, days.takeIf { wash },
                dry = rain != null && rain.day == null, nearby = info.nearbyStations,
            ),
        )
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: working
     * 목적: 바깥 정보를 받는 동안 처리 중(`Asking.work`)으로 알린다.
     * 인자: block — 받을 일
     * 반환: 받은 값
     */
    private suspend fun <T> working(block: suspend () -> T): T {
        asking.work(true) // → 로직 이동: Asking.work()
        return try {
            block()
        } finally {
            asking.work(false)
        }
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: fuelQuestion
     * 목적: 주유 카드를 만든다.
     *       - 세차도 권하면 "주유하고 세차할까요?", 근거 칸 연료·세차일·날씨
     *         (날씨를 모르면 뺀다), [주유하고 세차] [주유만]. 닫기가 거절이다
     *       - 아니면 "주유할까요?", 근거 칸 연료(곧 비가 와서 세차를 권하지
     *         않으면 "내일 비 예보"도), [경유지로 넣기] [괜찮아요]
     * 인자: pick — 넣을 주유소
     *       fuel — 연료(%). 모르면 null
     *       rangeKm — 주행 가능 거리(km). 모르면 null
     *       days — 마지막 세차 뒤 날 수. 세차를 권하지 않으면 null
     *       rain — 비 예보. 보지 않았거나 받지 못했으면 null
     *       soon — 곧 비가 와서 세차를 권하지 않으면 그 날. 아니면 null
     * 반환: 주유 질문
     * 근거: 인용 — docs/SPEC.md 4장 질문과 근거 칸, docs/SCREEN.md 6장 2단계
     */
    private fun fuelQuestion(
        pick: GasStation,
        fuel: Int?,
        rangeKm: Int?,
        days: Long?,
        rain: RainOutlook?,
        soon: Int?,
    ): Question {
        val name = pick.place.name // 주유소 이름, 설명에서 굵게
        val fuelFact = Fact("fuel", fuel?.let(TripLines::fuelFact) ?: TripLines.rangeFact(rangeKm ?: 0)) // 연료 근거 칸
        if (days == null) {
            return Question(
                kind = QuestionKind.FUEL_STOP,
                title = TripLines.FUEL_TITLE,
                detail = TripLines.fuelDetail(name, carWash = false, nearby = info.nearbyStations),
                emphasis = name,
                facts = listOfNotNull(fuelFact, soon?.let { Fact("rain", TripLines.rainSoonFact(it)) }),
                choices = listOf(Choice(TripLines.FUEL_YES, Reply.YES), Choice(TripLines.NO_THANKS, Reply.NO)),
            )
        }
        return Question(
            kind = QuestionKind.FUEL_STOP,
            title = TripLines.FUEL_WASH_TITLE,
            detail = TripLines.fuelDetail(name, carWash = true, nearby = info.nearbyStations),
            emphasis = name,
            facts = listOfNotNull(
                fuelFact,
                Fact("calendar", TripLines.washFact(days)),
                rain?.let { Fact(if (it.day == null) "sun" else "rain", TripLines.weatherFact(it.day, RAIN_DAYS)) },
            ),
            choices = listOf(Choice(TripLines.FUEL_WASH_ALL, Reply.ALL), Choice(TripLines.FUEL_WASH_ONLY, Reply.ONLY)),
        )
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: washDays
     * 목적: 마지막 세차가 `WASH_DUE_DAYS`일 이상 전이면 그 날 수를 준다.
     *       마지막 세차일을 모르면 권하지 않는다. 날씨는 따로 본다.
     * 반환: 마지막 세차 뒤 날 수. 세차할 때가 아니면 null
     * 근거: 인용 — docs/SPEC.md 4장 "기록이 없으면 권하지 않음". 설계 결정
     *       규칙: 모르는 사실로 판단하지 않는다
     */
    private fun washDays(): Long? {
        val last = memory.lastWashDate() ?: return null // 마지막 세차일
        val days = ChronoUnit.DAYS.between(last, today()) // 마지막 세차 뒤 날 수
        return days.takeIf { it >= WASH_DUE_DAYS }
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: answer
     * 목적: 주유 질문과 비 소식 질문(`Asking`)에 대한 대답을 처리한다. 말로 한
     *       대답과 카드 버튼·닫기가 모두 여기로 온다.
     *       - 주유하고 세차(`ALL`, 또는 세차 카드에 "응") — 경유지를 넣고 세차를
     *         예약한다
     *       - 주유만·경유지로 넣기 — 경유지만 넣는다. 세차도 물었던 카드면 오늘
     *         세차 권유를 거절한 것으로 남긴다
     *       - 세차를 묻지 않은 카드에 "세차도 할게" — 운전자가 먼저 세차하겠다고
     *         한 것이다(`washToo()`)
     *       - 거절·닫기 — 거절로 남겨(오늘 다시 묻지 않음) 알았다고 한다
     * 인자: reply — 대답 값
     * 반환: 물은 것이 있어서 대답으로 처리했으면 true. 아니면 아무것도 하지
     *       않고 false(부르는 쪽이 "못 알아들었어요"로 처리)
     * 근거: 인용 — docs/SPEC.md 4장, docs/SCREEN.md 6장 3단계. 받아들이면 말
     *       없이 판에만 알린다
     */
    suspend fun answer(reply: Reply): Boolean = lock.withLock {
        val q = asking.question.value // 기다리는 질문
        if (q?.kind == QuestionKind.WASH_RAIN) return@withLock answerRain(q.accepts(reply))
        if (q?.kind != QuestionKind.FUEL_STOP) return@withLock false
        val offer = offered // 물었던 주유 제안
        if (reply == Reply.ALL && offer?.withWash == false) return@withLock washToo(offer)
        if (!q.accepts(reply)) {
            asking.decline(QuestionKind.FUEL_STOP) // → 로직 이동: Asking.decline()
            if (offer?.withWash == true) memory.recordDeclined(WASH_OFFER, today())
            offered = null
            speaker.say(TripLines.DECLINED)
            return@withLock true
        }
        val wash = q.pick(reply)?.reply == Reply.ALL // 세차도 하겠다고 했는지
        if (offer?.withWash == true && !wash) memory.recordDeclined(WASH_OFFER, today())
        asking.answered(QuestionKind.FUEL_STOP) // → 로직 이동: Asking.answered()
        offered = null
        offer?.let { addGasStop(it.station, wash) } // → 로직 이동: TripDialog.addGasStop()
        true
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: washToo
     * 목적: 세차를 권하지 않은 주유 카드에서 운전자가 먼저 세차도 하겠다고 했을
     *       때. `RAIN_SOON_DAYS`일 안에 비가 오면 한 번 알리고 묻는다. 비가
     *       없거나 날씨를 모르면 바로 세차 되는 주유소를 넣고 예약한다(세차한 지
     *       얼마 안 됐어도 묻지 않는다). 근처에 세차 되는 주유소가 없으면 그렇다고
     *       말하고 주유 카드를 그대로 둔다.
     * 인자: offer — 물었던 주유 제안
     * 반환: 늘 true(대답으로 처리했다)
     * 근거: 인용 — docs/SPEC.md 4장 운전자가 먼저 세차하겠다고 할 때
     */
    private suspend fun washToo(offer: Offer): Boolean {
        val station = offer.washStation // 세차 되는 주유소
        if (station == null) {
            speaker.say(TripLines.NO_WASH_STATION)
            return true
        }
        // → 로직 이동: InfoPort.rain()
        val rain = offer.rain ?: working { info.rain(station.place.point, RAIN_DAYS) } // 비 예보
        val soon = rain?.day?.takeIf { it <= RAIN_SOON_DAYS } // 곧 비가 오는 날
        asking.answered(QuestionKind.FUEL_STOP)
        if (soon == null) {
            offered = null
            addGasStop(station, wash = true)
            return true
        }
        offered = offer.copy(rain = rain)
        askRain(soon) // → 로직 이동: TripDialog.askRain()
        return true
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: askRain
     * 목적: "내일 비 소식이 있어요. 그래도 세차할까요?" 카드를 올리고 말한다.
     * 인자: day — 비가 오는 가장 가까운 날
     * 근거: 인용 — docs/SPEC.md 4장 [세차하기] [다음에]
     */
    private fun askRain(day: Int) {
        val line = TripLines.rainWashAsk(day) // 카드 제목이자 하는 말
        val question = Question( // 비 소식 질문
            kind = QuestionKind.WASH_RAIN,
            title = line,
            detail = "",
            choices = listOf(Choice(TripLines.RAIN_WASH_YES, Reply.YES), Choice(TripLines.RAIN_WASH_NO, Reply.NO)),
        )
        if (asking.ask(question)) speaker.say(line)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: answerRain
     * 목적: 비 소식 질문의 대답을 처리한다.
     *       - 주유 카드에서 이어진 질문 — [세차하기]면 세차 되는 주유소를 넣고
     *         예약하고, [다음에]면 처음 보였던 주유소만 넣는다("세차도"라고
     *         했으니 주유는 하는 것으로 본다)
     *       - 세차 모드를 켜려다 나온 질문 — [세차하기]면 세차 모드를 켜고
     *         (`runWash`), [다음에]면 알았다고 한다
     * 인자: yes — [세차하기]면 true
     * 반환: 늘 true(대답으로 처리했다)
     */
    private suspend fun answerRain(yes: Boolean): Boolean {
        asking.answered(QuestionKind.WASH_RAIN)
        val offer = offered // 주유 카드에서 이어졌으면 그 제안
        offered = null
        when {
            offer != null -> addGasStop(if (yes) offer.washStation ?: offer.station else offer.station, wash = yes)
            yes -> runWash()
            else -> speaker.say(TripLines.DECLINED)
        }
        return true
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: wantWash
     * 목적: 운전자가 먼저 세차하겠다고 했을 때("세차할게", 부르는 카드의 세차
     *       모드 줄) 세차 모드를 켜기 전에 본다.
     *       - 주유 카드가 떠 있으면 그 카드에 "세차도"라고 답한 것으로 본다
     *       - 이미 P·N으로 멈춰 있으면(세차 입구) 날씨를 묻지 않는다
     *       - 아니고 `RAIN_SOON_DAYS`일 안에 비가 오면 한 번 알리고 묻는다
     * 반환: 여기서 처리했으면(물었으면) true. false면 부른 쪽이 세차 모드를 켠다
     * 근거: 인용 — docs/SPEC.md 4장 "이미 세차 입구에 멈춰 세차 모드를 켜려는
     *       때에는 날씨를 묻지 않습니다. 줄 서 있는 순간을 막지 않습니다"
     */
    suspend fun wantWash(): Boolean {
        if (asking.waiting(QuestionKind.FUEL_STOP)) return answer(Reply.ALL)
        return lock.withLock {
            val state = car.state.value // 지금 차 상태
            if (state.stopped && (state.gear == Gear.PARK || state.gear == Gear.NEUTRAL)) return@withLock false
            if (asking.question.value != null) return@withLock false
            val soon = info.rain(null, RAIN_DAYS)?.day?.takeIf { it <= RAIN_SOON_DAYS } // 곧 비가 오는 날
                ?: return@withLock false
            askRain(soon)
            asking.waiting(QuestionKind.WASH_RAIN)
        }
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: expire
     * 목적: 대답 없이 닫힌 질문의 제안을 버린다. 경유지는 넣지 않는다.
     */
    fun expire() {
        offered = null
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: addGasStop
     * 목적: 주유소를 경유지로 넣고, 세차도 하면 예약한다. 넣는 동안은 처리
     *       중(`Asking.work`)으로 알리고, 끝나면 판에 토스트("경유지에 넣고
     *       세차를 예약했어요")만 내고 말은 하지 않는다. 내비가 실패하거나
     *       `WAYPOINT_WAIT_MS` 안에 답하지 않으면 실패 토스트와 로그를 남기고
     *       예약하지 않는다. 경유지는 넣었지만 도착을 알아챌 수 없으면 세차는
     *       예약하지 않는다.
     * 인자: station — 넣을 주유소
     *       wash — 세차도 예약하면 true
     * 근거: 인용 — docs/SCREEN.md 6장 3단계, 2026-11-13 박건준 결정(진행 중 판,
     *       20초)
     */
    private suspend fun addGasStop(station: GasStation, wash: Boolean) {
        asking.work(true) // → 로직 이동: Asking.work()
        try {
            var answered = false // 내비가 시간 안에 답했는지
            val added = withTimeoutOrNull(WAYPOINT_WAIT_MS) { // 넣은 경로, 실패나 시간 넘김이면 null
                navi.addWaypoint(station.place).also { answered = true } // → 로직 이동: NaviPort.addWaypoint()
            }
            val name = station.place.name // 로그에 남길 주유소 이름
            when {
                added == null -> {
                    log(if (answered) "경유지 넣기 실패: $name" else "경유지 넣기 20초 넘김: $name")
                    asking.notice(TripLines.WAYPOINT_FAILED_NOTICE)
                }
                wash && !added.arrivalTracked -> asking.notice(TripLines.ARRIVAL_UNAVAILABLE)
                wash -> {
                    booking.reserve()
                    asking.notice(TripLines.WAYPOINT_WASH_NOTICE)
                }
                else -> asking.notice(TripLines.WAYPOINT_NOTICE)
            }
        } finally {
            asking.work(false)
        }
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: cheapest
     * 목적: 가장 싼 주유소를 고른다. 가격이 같거나 가격을 모르면(장소만 찾은
     *       곳) 가까운 곳.
     * 인자: stations — 주유소들
     * 반환: 고른 주유소. 목록이 비었으면 null
     * 근거: 인용 — docs/SPEC.md 4장 주유소 고르기 "가장 싼 곳. 세차를 함께 하면
     *       세차기가 있는 곳 중 가장 싼 곳"
     */
    private fun cheapest(stations: List<GasStation>): GasStation? =
        stations.minWithOrNull(compareBy({ it.priceWon ?: Int.MAX_VALUE }, { it.distanceM ?: Int.MAX_VALUE }))
}
