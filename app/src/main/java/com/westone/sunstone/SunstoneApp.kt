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
import com.westone.sunstone.demo.installDemo
import com.westone.sunstone.info.FixedInfo
import com.westone.sunstone.ui.SceneControls
import com.westone.sunstone.navi.PleosNaviPort
import com.westone.sunstone.routines.dialog.TripDialog
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.fake.FakeNavi
import com.westone.sunstone.routines.knowledge.CardBook
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.CardLines
import org.json.JSONObject
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
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.understand.PhraseMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
     * 수정: 2026-11-11 16:42:25 · 박희정
     * 함수: onCreate
     * 목적: 조립부를 만들고, debug 빌드면 시연 모드를 붙인다(출시 빌드의
     *       `installDemo()`는 아무것도 하지 않는다).
     */
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        installDemo(graph) // → 로직 이동: installDemo()
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
 * 작성: 2026-11-11 16:02:00 · 박희정
 * 클래스: ChatLine
 * 목적: 대화 기록 한 줄. 화면이 말풍선처럼 보여준다.
 * 인자: fromDriver — 운전자가 한 말이면 true, Sunstone이 한 말이면 false
 *       text — 말
 */
data class ChatLine(val fromDriver: Boolean, val text: String)

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
 *       가짜 차일 때 길안내 대화는 시연 장소 고정 목록만 찾는 가짜 내비를 쓴다
 *       (지도 화면은 바뀌지 않는다)
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

        /**
         * 작성: 2026-11-11 16:02:00 · 박희정
         * 변수: CHAT_KEEP
         * 목적: 대화 기록에 남길 최근 줄 수.
         * 근거: 미정 — 시나리오 하나(10줄 안팎)가 다 보이게 넉넉히 잡았다
         */
        const val CHAT_KEEP = 30

        /**
         * 작성: 2026-11-11 17:40:00 · 박희정
         * 변수: NO_SUNROOF
         * 목적: 가짜 차가 지원하지 않는 대상(선루프 두 개).
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0에는 선루프가 없다(docs/api-spike.md
         *       세차 선루프 행). 안전 경로도 실제 경로와 같은 말을 해야 한다(Tab 1
         *       1장 시연 통과 조건 1)
         */
        val NO_SUNROOF = setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2))

        /**
         * 작성: 2026-11-11 17:40:00 · 박희정
         * 변수: PLACES_FILE
         * 목적: 시연 장소 고정 목록 파일(assets). 가짜 내비가 목적지를 여기서 찾는다.
         */
        const val PLACES_FILE = "places.json"
    }

    /** 앱이 살아 있는 동안 도는 작업 범위. 화면 스레드에서 돈다 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 앱 설정 저장소. 가짜 차 여부만 둔다 */
    private val settings = app.getSharedPreferences("sunstone", Context.MODE_PRIVATE)

    /** 이번 실행에서 가짜 차를 쓰는지. 앱을 켤 때 한 번 정한다 */
    val usingFakeCar: Boolean = settings.getBoolean(KEY_FAKE, false)

    /** Pleos 차 창구. 가짜 차를 쓰거나 연결에 실패하면 null */
    val pleos: PleosCarPort? = if (usingFakeCar) null else runCatching { PleosCarPort(app, scope) }
        .onFailure { Log.w(TAG, "Pleos 연결 실패, 가짜 차로 진행: ${it.message}") }
        .getOrNull()

    /**
     * 가짜 차. 데모 설정(`fake on`)이거나 Pleos 연결에 실패하면 만든다(안전 경로). Pleos 에뮬레이터 차처럼
     * 선루프가 없어서 대사집과 같은 말을 한다. Pleos를 쓰면 null
     */
    val fake: FakeCar? = if (pleos == null) FakeCar().apply { unsupported = NO_SUNROOF } else null

    /** 루틴이 쓰는 차. Pleos 차, 없으면 가짜 차 */
    val car: CarPort = pleos ?: fake!!

    /** 음성 안내(TTS) */
    private val voice = AndroidSpeaker(app)

    /** 대화 기록. 운전자 말과 Sunstone 말을 차례로 쌓고, 화면이 보여준다. 최근 `CHAT_KEEP`줄만 둔다 */
    private val chatLines = MutableStateFlow<List<ChatLine>>(emptyList())

    /** 화면이 보는 대화 기록(값이 바뀌면 알려주는 상태) */
    val chat: StateFlow<List<ChatLine>> = chatLines

    /** 시연 모드 장면 실행기. debug 빌드의 `installDemo()`가 앱 시작 때 넣고, 출시 빌드에서는 null */
    var scenes: SceneControls? = null

    /** 말하기. 대화 기록에 남기고 음성으로 말한다. 루틴·길안내 대화가 모두 이것으로 말한다 */
    val speaker = Speaker { text ->
        addChat(ChatLine(fromDriver = false, text = text))
        voice.say(text) // → 로직 이동: AndroidSpeaker.say()
    }

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
        navi = navi?.let { n -> PleosNaviPort(n) { places?.spots?.map { it.hit }.orEmpty() } }
            ?: FakeNavi(fixedPlaces()),
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
     *       - `Ask` — 기능 카드로 답한다. 차는 바꾸지 않는다
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
            // → 로직 이동: CardBook.byId(), CardLines.answer(), Speaker.say()
            is VoiceAction.Ask -> speaker.say(
                action.cardId?.let(CardBook::byId)?.let(CardLines::answer) ?: CardLines.NO_CARD,
            )
            is VoiceAction.Answer -> scope.launch {
                // → 로직 이동: TripDialog.answer(), RoutineRunner.answer(),
                //   AppGraph.notUnderstood()
                if (!trip.answer(action.yes) && !runner.answer(action.yes)) notUnderstood()
            }
        }
    }

    /**
     * 작성: 2026-11-11 16:02:00 · 박희정
     * 함수: hear
     * 목적: 운전자가 한 말 한마디를 받는다. 대화 기록에 남기고, 알아들으면
     *       `handle()`로, 못 알아들으면 그렇다고 말한다. 화면 입력칸과 데모
     *       `say`(talk.ps1)가 함께 쓴다.
     * 인자: text — 운전자가 한 말
     *       source — 어디서 들어온 말인지(화면, 데모)
     */
    fun hear(text: String, source: Source) {
        val said = text.trim() // 앞뒤 빈칸을 뺀 말
        if (said.isEmpty()) return
        addChat(ChatLine(fromDriver = true, text = said))
        val action = PhraseMatcher.match(said, source) // 알아들은 요청, 못 알아들으면 null
        // → 로직 이동: AppGraph.handle(), AppGraph.notUnderstood()
        if (action != null) handle(action) else notUnderstood()
    }

    /**
     * 작성: 2026-11-09 19:11:42 · 박건준
     * 수정: 2026-11-11 16:02:00 · 박희정
     * 함수: notUnderstood
     * 목적: 말을 알아듣지 못했다고 말한다. `hear()`와 대답할 것이 없는 대답이
     *       쓴다.
     */
    private fun notUnderstood() = speaker.say(app.getString(R.string.voice_not_understood)) // → 로직 이동: Speaker.say()

    /**
     * 작성: 2026-11-11 16:02:00 · 박희정
     * 함수: addChat
     * 목적: 대화 기록에 한 줄을 더하고 최근 `CHAT_KEEP`줄만 남긴다.
     * 인자: line — 더할 줄
     */
    private fun addChat(line: ChatLine) = chatLines.update { (it + line).takeLast(CHAT_KEEP) }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: restartTrip
     * 목적: 시연을 처음부터 다시 할 수 있게 지운다.
     *       1. 켜진 루틴을 되돌린다(차 값을 원래대로)
     *       2. 길안내 중이면 취소한다. 안내가 없을 때 취소를 보내면 지도 앱이
     *          앞으로 나와 앱 화면을 가린다
     *       3. 대화 기록과 권하기 기록(10분에 한 번)을 지운다
     */
    suspend fun restartTrip() {
        runner.activeIds().forEach { runner.undo(it).join() } // → 로직 이동: RoutineRunner.undo()
        if (navi?.routing?.value == true) navi.cancelRoute() // → 로직 이동: PleosNavi.cancelRoute()
        runner.forgetSuggestions() // → 로직 이동: RoutineRunner.forgetSuggestions()
        chatLines.value = emptyList()
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: fixedPlaces
     * 목적: 시연 장소 고정 목록(assets/places.json)을 읽어 가짜 내비가 찾을 장소로
     *       만든다. 읽지 못하면 빈 목록(목적지를 "찾지 못했어요"로 답한다).
     * 반환: 장소들
     * 미구현: 프로토타입이라 장소 검색 API 대신 고정 목록을 쓴다. Pleos 차는 같은
     *       파일을 `PlaceWatcher`가 읽는다
     */
    private fun fixedPlaces(): List<PlaceHit> = runCatching {
        val json = JSONObject(app.assets.open(PLACES_FILE).bufferedReader().use { it.readText() }) // 파일 전체
        val array = json.getJSONArray("places") // 장소 배열
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i) // 장소 하나
            PlaceHit(o.getString("name"), GeoPoint(o.getDouble("lat"), o.getDouble("lng")))
        }
    }.onFailure { Log.w(TAG, "시연 장소 읽기 실패: ${it.message}") }.getOrDefault(emptyList())

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
