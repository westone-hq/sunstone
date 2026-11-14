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
import com.westone.sunstone.demo.ScenePlayer
import com.westone.sunstone.info.ApiKeys
import com.westone.sunstone.info.FixedInfo
import com.westone.sunstone.info.KakaoPlaces
import com.westone.sunstone.info.LiveInfo
import com.westone.sunstone.info.OpenMeteo
import com.westone.sunstone.info.OpinetStations
import com.westone.sunstone.info.WeatherConversation
import com.westone.sunstone.info.WeatherLocation
import com.westone.sunstone.navi.SelectedStation
import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.navi.PlaceWatcher
import com.westone.sunstone.navi.PleosNavi
import com.westone.sunstone.navi.PleosNaviPort
import com.westone.sunstone.navi.Proximity
import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.dialog.SeatDialog
import com.westone.sunstone.routines.dialog.TripDialog
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.fake.FakeNavi
import com.westone.sunstone.routines.knowledge.CardBook
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.script.ScriptBook
import com.westone.sunstone.routines.speech.CardLines
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.ui.SceneControls
import com.westone.sunstone.ui.panel.LeftZoneOverlay
import com.westone.sunstone.ui.panel.PanelPresenter
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

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
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: onCreate
     * 목적: 조립부를 만든다. 시연 모드는 조립부가 만든다.
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
 * 작성: 2026-11-11 16:02:00 · 박희정
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: ChatLine
 * 목적: 대화 기록 한 줄. 시연 모드가 앱이 답한 말 수를 세는 데 쓴다.
 * 인자: fromDriver — 운전자가 한 말이면 true, Sunstone이 한 말이면 false
 *       text — 말
 */
data class ChatLine(val fromDriver: Boolean, val text: String)

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: AppGraph
 * 목적: 기능 구현(routines)과 앱 연결을 한곳에서 잇는 조립부. 의존성 주입
 *       라이브러리 없이 손으로 만든다.
 *       - 차는 Pleos를 쓰고, 데모 설정(`fake on`)이거나 Pleos 연결에
 *         실패하면 가짜 차를 쓴다
 *       - 말·Gleo·화면·데모 입력은 모두 `handle()`로 들어온다
 *       - 장소는 근접 알림(`PlaceWatcher`)이 바꾸고, 내비 도착은 logcat에만
 *         남긴다(routines 창구에 아직 잇지 않음)
 *       - Pleos 차면 화면이 뜰 때 `KeepAwakeService`를 켜 두어 앱이 얼지 않게
 *         한다
 *       - 질문·토스트(`Asking`)는 길안내 대화와 루틴 실행기가 함께 쓰고,
 *         `PanelPresenter`가 왼쪽 구역 패널로 그린다(`LeftZoneOverlay`)
 * 인자: app — 앱 Context
 * 미구현: 프로토타입이라 Pleos 연결에 실패하면 알리지 않고 가짜 차로 진행한다
 *       (상태 줄에 "가짜 차"). 실제 차에서는 연결 실패를 안내해야 한다.
 *       가짜 차일 때 길안내 대화는 시연 장소 고정 목록만 찾는 가짜 내비를 쓴다
 *       (지도 화면은 바뀌지 않는다)
 */
class AppGraph(val app: Context) {

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
         * 작성: 2026-11-11 16:02:00 · 박희정
         * 변수: CHAT_KEEP
         * 목적: 대화 기록에 남길 최근 줄 수.
         * 근거: 미정 — 시나리오 하나(10줄 안팎)가 다 보이게 넉넉히 잡았다
         */
        const val CHAT_KEEP = 30

        /**
         * 작성: 2026-11-11 17:40:00 · 박희정
         * 수정: 2026-11-13 10:00:00 · 박희정
         * 수정: 2026-11-14 03:00:00 · 박건준
         * 변수: FAKE_UNSUPPORTED
         * 목적: 가짜 차가 지원하지 않는 대상(선루프 두 개, 뒷유리 블라인드).
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0에는 선루프와 뒷유리 블라인드가
         *       없다(docs/api-spike.md 세차 선루프·차일드락 블라인드 행). 안전
         *       경로도 실제 경로와 같은 말을 해야 한다(시연 통과 조건)
         */
        val FAKE_UNSUPPORTED =
            setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2), Target.RearBlind)

        /**
         * 작성: 2026-11-11 17:40:00 · 박희정
         * 변수: PLACES_FILE
         * 목적: 시연 장소 고정 목록 파일(assets). 가짜 내비가 목적지를 여기서 찾는다.
         */
        const val PLACES_FILE = "places.json"

        /**
         * 작성: 2026-11-14 17:00:00 · 박건준
         * 변수: KEYS_FILE, FUEL_GASOLINE
         * 목적: 바깥 API 키 설정 파일 이름과, 유종을 정하지 않았을 때 쓰는
         *       오피넷 유종 코드(휘발유).
         * 근거: 표준 — 오피넷 제품 코드 B027 휘발유, D047 경유
         * 미구현: 프로토타입이라 차의 유종을 읽지 않고(에뮬레이터 차는 전기차)
         *       설정 값이나 휘발유로 본다
         */
        const val KEYS_FILE = "api.properties"
        const val FUEL_GASOLINE = "B027"

        /**
         * 작성: 2026-11-14 13:41:10 · 박희정
         * 변수: STATION_RADIUS_M
         * 목적: 실제로 고른 주유소에 거는 근접 알림 반경(m).
         * 근거: 측정 — 시연 장소 근접 알림과 같은 반경에서 들어감·나감이 왔다
         *       (docs/api-spike.md 근접 알림 행)
         */
        const val STATION_RADIUS_M = 150f

        /**
         * 작성: 2026-11-14 17:00:00 · 박건준
         * 변수: PLACE_NUMBER
         * 목적: 목적지 후보를 고르는 말("2", "2번", "2번으로")에서 번호를 뽑는다.
         */
        val PLACE_NUMBER = Regex("""\s*(\d)\s*(?:번(?:으로)?)?\s*""")
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
     * 선루프·뒷유리 블라인드가 없어서 대사집과 같은 말을 한다. Pleos를 쓰면 null
     */
    val fake: FakeCar? = if (pleos == null) FakeCar().apply { unsupported = FAKE_UNSUPPORTED } else null

    /** 루틴이 쓰는 차. Pleos 차, 없으면 가짜 차 */
    val car: CarPort = pleos ?: fake!!

    /** 음성 안내(녹음본·TTS를 한 줄로 낸다) */
    private val voice = AndroidSpeaker(app)

    /** 대화 기록. 운전자 말과 Sunstone 말을 차례로 쌓는다. 최근 `CHAT_KEEP`줄만 둔다 */
    private val chatLines = MutableStateFlow<List<ChatLine>>(emptyList())

    /** 시연 모드가 보는 대화 기록(값이 바뀌면 알려주는 상태) */
    val chat: StateFlow<List<ChatLine>> = chatLines

    /**
     * 말하기. 대화 기록에 남기고 음성으로 말한다. 루틴·길안내 대화가 모두 이것으로 말한다. 그때 떠 있는
     * 질문을 말에 붙여, 패널이 그 말이 시작될 때 뜨게 한다
     */
    val speaker = Speaker { text ->
        addChat(ChatLine(fromDriver = false, text = text))
        voice.say(text, asking.question.value) // → 로직 이동: AndroidSpeaker.say()
    }

    /** 사람별 시트 자세 저장소 */
    val profiles = PrefsSeatProfiles(app)

    /** 사용 기록(마지막 세차일). 데모 `washed N`으로 바꿀 수 있다 */
    val memory = PrefsMemory(app)

    /** 세차 예약. 길안내 대화가 주유소에 예약하고 세차 모드가 본다. 둘이 같은 것을 쓴다 */
    private val booking = WashBooking()

    /**
     * 지금 묻는 질문과 토스트. 길안내 대화·세차 모드·루틴 실행기가 함께 쓰고 패널이 그린다. 거절한 권유는
     * 그날 다시 묻지 않게 기억에 남긴다
     */
    val asking = Asking(memory) { LocalDate.now() }

    /** 지금 길안내 목적지. 길안내 대화가 넣고 차일드락이 하원길·도착을 본다. 둘이 같은 것을 쓴다 */
    private val route = CurrentRoute()

    /** 시트 자세 루틴. 자세 저장(`capture`)에도 써서 따로 둔다. 다 맞추면 토스트를 낸다 */
    private val seatRoutine = SeatMemoryRoutine(profiles, asking)

    /** 루틴 실행기. 만들 때 상황 감지를 시작한다 */
    val runner = RoutineRunner(
        car = car,
        routines = listOf(
            WashRoutine(booking, memory, asking) { LocalDate.now() },
            ChildLockRoutine(route, memory, asking) { LocalDate.now() },
            seatRoutine,
        ),
        speaker = speaker,
        scope = scope,
        asking = asking,
    ).also { it.start() } // → 로직 이동: RoutineRunner.start()

    /** 시트 맞추기 대화(시나리오 ③). 저장된 자세로 맞출 때는 실행기에 시트 메모리를 요청한다 */
    val seat = SeatDialog(car, profiles, asking, speaker) { runner.request(it) } // → 로직 이동: RoutineRunner.request()

    /**
     * 시연 모드 장면 실행기. 대사집 시나리오 ①②③을 넣는다. 부르는 카드가 시연 줄로 쓴다. 프로토타입이라
     * 차 상황을 스스로 만들 수 없어 제출(release) 빌드에도 둔다
     */
    val scenes: SceneControls = ScenePlayer(this, ScriptBook.all)

    /** 고르기 카드에 보일 목적지 후보를 넣는 곳. 길안내 대화가 뒤에 만들어져 `init`에서 이어 준다 */
    private var placesShown: MutableStateFlow<List<PlaceHit>>? = null

    /**
     * 왼쪽 구역 카드·듣는 중 줄 값. 버튼은 대답(`answer()`)과 루틴 끝내기로, 부르는 카드 루틴 줄은
     * `switchRoutine()`으로 가고, 마이크 칸 길게 누르기는 시연 모드 다음 장면으로 간다
     */
    val panels = PanelPresenter(
        context = app,
        scope = scope,
        asking = asking,
        runner = runner.ui,
        speaking = voice.speaking,
        starts = voice.starts,
        scenes = scenes,
        onAnswer = ::answer,
        onFinish = { id -> runner.undo(id) }, // → 로직 이동: RoutineRunner.undo()
        onRoutine = ::switchRoutine,
        onHold = { scenes.next() }, // → 로직 이동: SceneControls.next()
        places = MutableStateFlow<List<PlaceHit>>(emptyList()).also { shown ->
            // 길안내 대화는 아래에서 만들어지므로 후보는 만든 뒤에 이어 준다(`init`)
            placesShown = shown
        },
        onPlace = ::choosePlace,
        onExpire = ::expire,
    )

    /** 왼쪽 구역 창. 화면이 뜰 때 `showPanels()`가 띄운다 */
    private val overlay = LeftZoneOverlay(app, scope, panels)

    /** 왼쪽 구역 창이 떠 있는지 바꾸는 곳 */
    private val overlayOn = MutableStateFlow(false)

    /** 왼쪽 구역 창이 떠 있는지(값이 바뀌면 알려주는 상태). false면 앱 화면 안에 패널을 그린다 */
    val overlayShown: StateFlow<Boolean> = overlayOn

    /** 속도 센서. 값을 Pleos 차 창구에 넣는다. 가짜 차일 때는 버린다 */
    private val speed = AaosSpeedSource(app) { kmh -> pleos?.onSensorSpeed(kmh) }

    /** 근접 알림 창구. Pleos 차일 때만 만든다 */
    private val proximity: Proximity? = pleos?.let { Proximity(app) }

    /** 내비 창구. Pleos 차일 때만 만든다. 처음 쓸 때 내비 서비스에 연결한다 */
    val navi: PleosNavi? = proximity?.let { PleosNavi(app, it) }

    /**
     * 실제 검색으로 고른 주유소의 근접 알림(반경 `STATION_RADIUS_M`). 경유지로 넣으면 걸고, 들어가고 나가면
     * 차 장소를 주유소로 바꾼다. 시연 장소 목록에 없는 주유소에서도 세차 입구를 알아채려고 둔다
     */
    private val selectedStation: SelectedStation? = proximity?.let { p ->
        SelectedStation(
            add = { place, enter, exit ->
                // → 로직 이동: Proximity.add()
                p.add(com.westone.sunstone.navi.GeoPoint(place.point.lat, place.point.lng), STATION_RADIUS_M, enter, exit)
            },
            remove = p::remove,
            onPlace = { setPlace(it) },
        )
    }

    /**
     * 시연 장소 근접 알림. 들어가고 나가면 Pleos 차 장소를 바꾼다. 위치 권한을 받은 뒤 시작한다. 실제로 고른
     * 주유소 안에 있는 동안은 시연 장소의 나감 알림이 장소를 지우지 못하게 한다
     */
    val places: PlaceWatcher? = proximity?.let { p ->
        PlaceWatcher(app, p) { place -> if (selectedStation?.isInside != true) setPlace(place) }
    }

    /** 날씨 예보(Open-Meteo, 키 없음) */
    private val weather = OpenMeteo()

    /** 지금 위치(FusedLocation). 실제 주유소·날씨를 찾을 때 쓴다 */
    private val weatherLocation = WeatherLocation(app)

    /**
     * 바깥 API 키. 앱 비공개 폴더나 앱 바깥 저장소 폴더의 `api.properties`에서 읽는다
     * (`tools/configure-api.ps1`이 넣는다). 제출 APK에는 키가 없다
     */
    private val apiKeys = ApiKeys(
        java.io.File(app.filesDir, KEYS_FILE),
        app.getExternalFilesDir(null)?.let { java.io.File(it, KEYS_FILE) },
    )

    /** 카카오 로컬(목적지 후보, 근처 주유소, 좌표 변환) */
    private val kakao = KakaoPlaces({ apiKeys.read("sunstone.kakao.restKey") })

    /** 오피넷(근처 주유소 가격과 세차 시설 여부). 좌표 변환은 카카오에 맡긴다 */
    private val opinet = OpinetStations(
        key = { apiKeys.read("sunstone.opinet.key") },
        fuelCode = { apiKeys.read("sunstone.fuel.code") ?: FUEL_GASOLINE },
        convert = kakao::convert,
    )

    /** "오늘 비 와?" 같은 날씨 질문에 답하는 대화. 처음 쓸 때 만든다 */
    private val weatherConversation by lazy { WeatherConversation(weather, weatherLocation, speaker, scope) }

    /** 실제 바깥 정보. 주유소는 오피넷(가격·세차 여부), 안 되면 카카오(장소만). 날씨는 Open-Meteo */
    private val liveInfo: InfoPort = LiveInfo(weather, location = { weatherLocation.current() }) { liveStations() }

    /** 시연 준비 값(주유소 세 곳, 7일 동안 비 없음). 시연 모드가 도는 동안 쓴다 */
    private val demoInfo: InfoPort = FixedInfo()

    /** 시연 밖에서 실제 값 대신 쓸 정보. 데모 `weather`(debug)가 넣는다. null이면 실제 값 */
    @Volatile
    var tripInfo: InfoPort? = null

    /**
     * 길안내 대화가 쓰는 바깥 정보. 부를 때마다 고른다: 시연 중이면 준비 값, 데모 `weather`가 넣은 값이
     * 있으면 그 값, 아니면 실제 값
     */
    private val info = object : InfoPort {
        private val now: InfoPort get() = if (scenes.current.value != null) demoInfo else tripInfo ?: liveInfo
        override val nearbyStations: Boolean get() = now.nearbyStations
        override suspend fun gasStations(): List<GasStation> = now.gasStations()
        override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? = now.rain(point, days)
    }

    /**
     * 길안내 대화(시나리오 ① #1~#2). Pleos 차면 Pleos 내비, 가짜 차면 장소가 없는 가짜 내비를 쓴다.
     * 목적지는 실제 값을 쓸 때 카카오에서 찾고(시연 중이거나 못 찾으면 시연 장소 목록), 고른 주유소에는
     * 근접 알림을 건다
     */
    val trip = TripDialog(
        car = car,
        navi = navi?.let { n ->
            PleosNaviPort(
                n,
                places = { places?.spots.orEmpty() },
                search = { query -> kakao.search(query) }, // → 로직 이동: KakaoPlaces.search()
                searchEnabled = { info.nearbyStations },
                selectedStation = { place -> selectedStation?.watch(place) }, // → 로직 이동: SelectedStation.watch()
                clearStation = { selectedStation?.clear() },
            )
        } ?: FakeNavi(fixedPlaces()),
        info = info,
        memory = memory,
        booking = booking,
        asking = asking,
        speaker = speaker,
        today = { LocalDate.now() },
        currentRoute = route,
        // → 로직 이동: RoutineRunner.request()
        runWash = { runner.request(RunRequest(RoutineId.WASH, source = Source.VOICE)) },
        log = { Log.w(TAG, it) },
    )

    /** AAOS 에너지 속성과 연료 종류. 값을 Pleos 차 창구에 넣는다. 가짜 차일 때는 버린다 */
    private val energy = AaosEnergySource(
        app,
        onValue = { id, value -> pleos?.onAaosEnergy(id, value) }, // → 로직 이동: PleosCarPort.onAaosEnergy()
        onFuelTypes = { types -> pleos?.onFuelTypes(types) }, // → 로직 이동: PleosCarPort.onFuelTypes()
    )

    init {
        watchArrivals()
        scope.launch { trip.destinations.collect { placesShown?.value = it } }
    }

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: liveStations
     * 목적: 지금 위치 근처 주유소를 실제로 찾는다. 오피넷(가격·세차 시설 여부)을
     *       먼저 쓰고, 키가 없거나 실패하거나 비었으면 카카오 주유소 검색(장소만,
     *       가까운 순)으로 넘어간다.
     * 반환: 주유소들. 위치를 모르면 예외(`LiveInfo`가 빈 목록으로 바꾼다)
     * 근거: 인용 — docs/SPEC.md 4장 주유소 고르기 "가장 싼 곳"
     */
    private suspend fun liveStations(): List<GasStation> {
        val here = weatherLocation.current() ?: error("지금 위치를 모름") // → 로직 이동: WeatherLocation.current()
        val priced = runCatching { opinet.nearby(here) } // → 로직 이동: OpinetStations.nearby()
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .onFailure { Log.w(TAG, "오피넷 주유소 실패, 카카오로 찾음: ${it.message}") }
            .getOrDefault(emptyList()) // 가격이 붙은 주유소
        return priced.ifEmpty { kakao.nearbyStations(here) } // → 로직 이동: KakaoPlaces.nearbyStations()
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
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: handle
     * 목적: 말·Gleo·화면·데모 입력을 처리한다.
     *       - `Run` — 루틴 실행을 요청한다. 이름이 있는 시트 메모리("엄마
     *         자세로")는 시트 대화로 넘긴다(없는 이름이면 같이 맞출지 묻는다).
     *         세차 모드는 길안내 대화가 먼저 본다(주유 카드의 대답, 비 소식)
     *       - `Undo` — 시트 맞추는 중이면 그것을 되돌리고, 아니면 이름이 없으면
     *         가장 최근에 켜진 루틴을 되돌린다
     *       - `SaveSeat` — 지금 시트 자세를 이름으로 저장한다
     *       - `Navigate` — 길안내 대화로 목적지를 넘기고, 길안내를 시작한 뒤
     *         루틴 권하기를 다시 본다(하원길 차일드락)
     *       - `Ask` — 기능 카드로 답한다. 차는 바꾸지 않는다
     *       - `Answer` — 길안내 대화의 주유 제안, 그다음 루틴 권하기 순서로
     *         답한다. 둘 다 없으면 못 알아들은 것으로 본다
     *       - `SeatStart`·`SeatAdjust`·`SeatRecall`·`Name` — 시트 맞추기 대화
     * 인자: action — 알아들은 요청
     */
    fun handle(action: VoiceAction) {
        when (action) {
            is VoiceAction.Run -> {
                val r = action.request // 실행 요청
                val name = r.profile.takeIf { r.id == RoutineId.SEAT_MEMORY } // 시트 자세 이름, 아니면 null
                // → 로직 이동: SeatDialog.recall(), TripDialog.wantWash(), RoutineRunner.request()
                when {
                    name != null -> scope.launch { seat.recall(name, r.seat) }
                    // 먼저 세차하겠다고 하면 주유 카드의 대답인지, 곧 비가 오는지부터 본다(docs/SPEC.md 4장)
                    r.id == RoutineId.WASH -> scope.launch { if (!trip.wantWash()) runner.request(r) }
                    else -> runner.request(r)
                }
            }
            is VoiceAction.Undo -> {
                if (seat.active) {
                    scope.launch { seat.answer(Reply.NO) } // → 로직 이동: SeatDialog.answer()
                    return
                }
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
            // → 로직 이동: TripDialog.navigate(), RoutineRunner.recheck()
            is VoiceAction.Navigate -> scope.launch {
                trip.navigate(action.destination)
                runner.recheck()
            }
            // → 로직 이동: CardBook.byId(), CardLines.answer(), Speaker.say()
            is VoiceAction.Ask -> speaker.say(
                action.cardId?.let(CardBook::byId)?.let(CardLines::answer) ?: CardLines.NO_CARD,
            )
            is VoiceAction.Answer -> answer(action.reply) // → 로직 이동: AppGraph.answer()
            // → 로직 이동: SeatDialog.start(), SeatDialog.adjust(),
            //   SeatDialog.recall(), SeatDialog.name()
            is VoiceAction.SeatStart -> scope.launch { seat.start(action.seat) }
            is VoiceAction.SeatAdjust -> scope.launch { seat.adjust(action.axis, action.percent) }
            is VoiceAction.SeatRecall -> scope.launch { seat.recall(action.name, action.seat) }
            is VoiceAction.Name -> scope.launch { if (!seat.name(action.name)) notUnderstood() }
        }
    }

    /**
     * 작성: 2026-11-11 16:02:00 · 박희정
     * 수정: 2026-11-14 17:00:00 · 박건준
     * 함수: hear
     * 목적: 운전자가 한 말 한마디를 받는다. 대화 기록과 듣는 중 줄(HEARD)에
     *       남기고(아래 줄에 앱이 이어서 할 일), 알아들으면 `handle()`로, 못
     *       알아들으면 그렇다고 말한다.
     *       시트 자세를 기억할 이름을 기다리는 중이면 들은 말을 이름으로 넘긴다.
     *       목적지 후보를 고르는 중이면 번호("2번")와 그만두는 말을 먼저 보고,
     *       날씨를 묻는 말("오늘 비 와?")은 날씨 대화가 답한다.
     *       시연 운전자 대사와 데모 `say`(talk.ps1)가 함께 쓴다.
     * 인자: text — 운전자가 한 말
     *       source — 어디서 들어온 말인지(화면, 데모)
     */
    fun hear(text: String, source: Source) {
        val said = text.trim() // 앞뒤 빈칸을 뺀 말
        if (said.isEmpty()) return
        addChat(ChatLine(fromDriver = true, text = said))
        if (!seat.awaitingName && trip.destinations.value.isNotEmpty()) {
            // 목적지 후보를 고르는 중이면 "2번"·"취소"를 먼저 본다
            val index = PLACE_NUMBER.matchEntire(said)?.groupValues?.get(1)?.toIntOrNull()?.minus(1) // 고른 번호
            val cancel = PhraseMatcher.match(said, source) == VoiceAction.Answer(Reply.NO) // 그만두는 말인지
            if (index != null && index in trip.destinations.value.indices || cancel) {
                panels.heard(said, null) // → 로직 이동: PanelPresenter.heard()
                choosePlace(if (cancel) -1 else index!!) // → 로직 이동: AppGraph.choosePlace()
                return
            }
        }
        // 시연 중에는 대사가 날씨 질문으로 새지 않게 날씨 대화를 쓰지 않는다
        if (scenes.current.value == null && !seat.awaitingName && weatherConversation.hear(said)) {
            panels.heard(said, app.getString(R.string.next_weather))
            return
        }
        weatherConversation.cancel() // → 로직 이동: WeatherConversation.cancel()
        val action = PhraseMatcher.match(said, source) // 알아들은 요청, 못 알아들으면 null
        if (seat.awaitingName && action !is VoiceAction.Answer) {
            // 시트 자세를 기억할 이름을 기다리는 중이면 들은 말을 이름으로 본다(대답 "괜찮아"는 제외)
            panels.heard(said, null) // → 로직 이동: PanelPresenter.heard()
            scope.launch { if (!seat.name(said)) notUnderstood() } // → 로직 이동: SeatDialog.name()
            return
        }
        panels.heard(said, nextOf(action)) // → 로직 이동: PanelPresenter.heard()
        // → 로직 이동: AppGraph.handle(), AppGraph.notUnderstood()
        if (action != null) handle(action) else notUnderstood()
    }

    /**
     * 작성: 2026-11-12 19:06:00 · 박건준
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: answer
     * 목적: 앱이 물은 것에 대답한다. 말("응, 세차도 할게", "취소")과 카드
     *       버튼·닫기가 모두 여기로 온다. 카드에 고른 버튼을 먼저 표시하고,
     *       루틴 권하기(세차 입구) → 길안내 대화의 질문 → 시트 대화 순서로
     *       본다. 셋 다 아니면 못 알아들은 것으로 본다.
     * 인자: reply — 대답 값
     */
    fun answer(reply: Reply) {
        panels.picked(reply) // → 로직 이동: PanelPresenter.picked()
        scope.launch {
            // → 로직 이동: RoutineRunner.answer(), TripDialog.answer(),
            //   AppGraph.notUnderstood()
            if (!runner.answer(reply) && !trip.answer(reply) && !seat.answer(reply)) notUnderstood()
        }
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 17:00:00 · 박건준
     * 함수: choosePlace
     * 목적: 목적지 후보 중 하나를 골랐을 때(카드 줄, "2번"). 그 목적지로
     *       길안내를 시작하고 루틴 권하기를 다시 본다. -1이면 고르기를 그만둔다.
     * 인자: index — 후보 번호(0부터). 그만두면 -1
     */
    fun choosePlace(index: Int) {
        if (index < 0) {
            trip.invalidate() // → 로직 이동: TripDialog.invalidate()
            return
        }
        scope.launch {
            trip.selectDestination(index) // → 로직 이동: TripDialog.selectDestination()
            runner.recheck() // → 로직 이동: RoutineRunner.recheck()
        }
    }

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: expire
     * 목적: 대답 없이 시간이 지난 질문을 닫는다. 거절로 남기지 않고, 권하기와
     *       주유 제안만 버린다.
     * 인자: kind — 닫을 질문 종류
     * 근거: 인용 — docs/SPEC.md 3장 대답이 없을 때
     */
    private fun expire(kind: QuestionKind) {
        Log.i(TAG, "대답 없어 닫음: $kind")
        runner.expire() // → 로직 이동: RoutineRunner.expire()
        trip.expire() // → 로직 이동: TripDialog.expire()
        asking.expire(kind) // → 로직 이동: Asking.expire()
    }

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 함수: switchRoutine
     * 목적: 부르는 카드의 루틴 줄을 눌렀을 때. 켜져 있으면 원래대로 돌리고,
     *       아니면 켠다. 시트 맞추기는 말로 맞추는 대화("시트 좀 맞춰 줘")를
     *       시작한다.
     * 인자: id — 루틴
     *       on — 지금 켜져 있으면 true
     */
    private fun switchRoutine(id: RoutineId, on: Boolean) {
        when {
            on -> runner.undo(id) // → 로직 이동: RoutineRunner.undo()
            // → 로직 이동: AppGraph.handle()
            id == RoutineId.SEAT_MEMORY -> handle(VoiceAction.SeatStart(Seat.DRIVER))
            else -> handle(VoiceAction.Run(RunRequest(id, source = Source.SCREEN)))
        }
    }

    /**
     * 작성: 2026-11-12 19:06:00 · 박건준
     * 함수: showPanels
     * 목적: 왼쪽 구역 창을 띄운다. 화면이 뜰 때 `MainActivity`가 부른다. 허가가
     *       없으면 `overlayShown`이 false로 남아 앱 화면 안에 패널을 그린다.
     */
    fun showPanels() {
        overlayOn.value = overlay.show() // → 로직 이동: LeftZoneOverlay.show()
    }

    /**
     * 작성: 2026-11-12 19:56:00 · 박건준
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: nextOf
     * 목적: 들은 말 아래에 보일, 앱이 이어서 하는 일을 짧게 고른다. 대답이면
     *       지금 묻는 질문 종류로, 길안내면 길안내로 정한다. 실행 결정은 하지
     *       않고 보여 줄 글자만 고른다.
     * 인자: action — 알아들은 요청. 못 알아들었으면 null
     * 반환: 안내 글자. 고를 것이 없으면 null(줄을 비운다)
     * 근거: 인용 — 시안 sunstone-ui.html 들음 상태 "세차 모드를 켤게요"
     */
    private fun nextOf(action: VoiceAction?): String? {
        val kind = asking.question.value?.kind // 지금 묻는 질문 종류, 없으면 null
        val res = when { // 고른 문구
            action is VoiceAction.Navigate -> R.string.next_navigate
            action !is VoiceAction.Answer || kind == null -> return null
            asking.question.value?.accepts(action.reply) != true -> R.string.next_declined
            kind == QuestionKind.WASH_MODE -> R.string.next_wash_mode
            kind == QuestionKind.CHILD_LOCK || kind == QuestionKind.CHILD_LOCK_AGAIN -> R.string.next_child_lock
            kind == QuestionKind.SEAT_ADJUST -> R.string.next_seat_done
            kind == QuestionKind.SEAT_NEW_PERSON -> R.string.next_seat_together
            kind != QuestionKind.FUEL_STOP -> return null
            asking.question.value?.pick(action.reply)?.reply == Reply.ALL -> R.string.next_fuel_wash
            else -> R.string.next_fuel_stop
        }
        return app.getString(res)
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
     * 작성: 2026-11-12 15:00:00 · 박건준
     * 함수: stopVoice
     * 목적: 나오던 말을 끊고 기다리던 말을 버린다. 시연에서 장면을 건너뛸 때
     *       앞 장면 말이 뒤늦게 이어 나오지 않게 한다.
     */
    fun stopVoice() = voice.stop() // → 로직 이동: AndroidSpeaker.stop()

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-14 17:00:00 · 박건준
     * 함수: restartTrip
     * 목적: 시연을 처음부터 다시 할 수 있게 지운다.
     *       1. 나오던 말을 끊고, 기다리던 말과 떠 있는 질문·거절 기록(오늘 거절한
     *          권유 포함)·목적지 후보·고른 주유소 알림·날씨 대화를 버린다
     *       2. 켜진 루틴을 되돌린다(차 값을 원래대로)
     *       3. 길안내 중이면 취소한다. 안내가 없을 때 취소를 보내면 지도 앱이
     *          앞으로 나와 앱 화면을 가린다
     *       4. 대화 기록, 권하기 기록(10분에 한 번), 끝난 실행 기록, 길안내
     *          목적지, 시트 대화를 지운다
     *       되돌리는 동안 나오는 말("원래대로 돌렸어요")은 그대로 들린다
     */
    suspend fun restartTrip() {
        selectedStation?.clear() // → 로직 이동: SelectedStation.clear()
        weatherConversation.cancel() // → 로직 이동: WeatherConversation.cancel()
        trip.invalidate() // → 로직 이동: TripDialog.invalidate()
        voice.stop() // → 로직 이동: AndroidSpeaker.stop()
        asking.newTrip() // → 로직 이동: Asking.newTrip()
        asking.forgetDay() // → 로직 이동: Asking.forgetDay()
        runner.activeIds().forEach { runner.undo(it).join() } // → 로직 이동: RoutineRunner.undo()
        if (navi?.routing?.value == true) navi.cancelRoute() // → 로직 이동: PleosNavi.cancelRoute()
        runner.forgetSuggestions() // → 로직 이동: RoutineRunner.forgetSuggestions()
        runner.forgetFinished() // → 로직 이동: RoutineRunner.forgetFinished()
        route.clear()
        seat.reset()
        chatLines.value = emptyList()
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: fixedPlaces
     * 목적: 시연 장소 고정 목록(assets/places.json)을 읽어 가짜 내비가 찾을 장소로
     *       만든다. 장소의 성격(집·유치원)도 읽는다. 읽지 못하면 빈
     *       목록(목적지를 "찾지 못했어요"로 답한다).
     * 반환: 장소들
     * 미구현: 프로토타입이라 장소 검색 API 대신 고정 목록을 쓴다. Pleos 차는 같은
     *       파일을 `PlaceWatcher`가 읽는다
     */
    private fun fixedPlaces(): List<PlaceHit> = runCatching {
        val json = JSONObject(app.assets.open(PLACES_FILE).bufferedReader().use { it.readText() }) // 파일 전체
        val array = json.getJSONArray("places") // 장소 배열
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i) // 장소 하나
            val kind = Place.entries.firstOrNull { it.name == o.optString("place") } ?: Place.NONE // 장소 성격
            PlaceHit(o.getString("name"), GeoPoint(o.getDouble("lat"), o.getDouble("lng")), kind)
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
     * 수정: 2026-11-12 15:00:00 · 박건준
     * 함수: keepAwake
     * 목적: Pleos 차일 때 `KeepAwakeService`를 켜 앱이 얼지 않게 한다. 앱
     *       화면이 뜰 때마다 `MainActivity`가 부르고, 이미 켜져 있으면 그대로
     *       둔다. 끄지 않는다(상황을 알아채려면 늘 깨어 있어야 한다). 가짜 차는
     *       켜지 않는다.
     * 근거: 측정 — 길안내·루틴이 있을 때만 켜던 때는 지도 앱이 앞에 있고 둘 다
     *       없으면 앱이 얼어 주유소 근접 알림을 못 받았다. 앱이 뒤에 있을 때는
     *       서비스를 새로 켤 수 없어서(Android 12+) 화면이 뜰 때 켠다
     *       (docs/api-spike.md 앱 얼림 대응 절)
     */
    fun keepAwake() {
        if (pleos != null) KeepAwakeService.start(app) // → 로직 이동: KeepAwakeService.start()
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
