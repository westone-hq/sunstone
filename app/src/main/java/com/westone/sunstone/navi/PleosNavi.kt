package com.westone.sunstone.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 작성: 2026-11-11 12:08:00 · 박건준
 * 클래스: PleosNavi
 * 목적: Pleos 내비(NaviHelper)와 위치(FusedLocation 근접 알림)를 감싼 내비
 *       창구. 경로 시작, 경유지 더하기, 남은 거리, 안내 취소, 도착 알림을
 *       준다. routines 창구에는 아직 잇지 않는다(창구 초안은
 *       relay/TO_CLOUD.md).
 *       - 경로를 시작하거나 경유지를 더하면 그 좌표에 근접 알림을 걸어,
 *         들어가면 `arrivals`로 도착을 흘린다. 내비가 주는 도착 이벤트도
 *         같이 흘리고, 같은 곳 도착이 짧은 사이에 두 번 오면 한 번만 흘린다
 * 인자: context — 앱 Context. 내비 서비스 연결에 쓴다
 *       proximity — 근접 알림 창구
 * 근거: 측정 — 에뮬레이터에서 경로·경유지·남은 거리·통행료·취소는 되고
 *       내비 도착 이벤트는 오지 않았다(내비 위치가 움직이지 않음).
 *       근접 알림은 위치를 따라왔다(docs/api-spike.md 길안내·근접 알림 행)
 */
class PleosNavi(context: Context, private val proximity: Proximity) : AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-11 12:08:00 · 박건준
         * 변수: TAG
         * 목적: 내비 관련 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-11 12:08:00 · 박건준
         * 수정: 2026-11-11 17:06:00 · 박희정
         * 변수: CONNECT_MS, ROUTE_MS, INFO_MS
         * 목적: 서비스 연결, 경로 찾기(경로 시작·경유지), 정보 읽기를 기다리는
         *       시간(밀리초).
         * 근거: 측정 — 연결은 10밀리초 안, 경로 찾기는 1초 안, 정보 읽기는
         *       30밀리초 안에 왔다(relay/files/20261110-1428-내비실측.csv).
         *       넉넉히 잡았다. 2026-11-11 에뮬레이터를 켠 직후 첫 경로는
         *       요청 뒤 24.5초에 시작돼 15초에서 45초로 늘렸다. 기다리다
         *       다시 요청하면 내비가 앞 계산을 취소하고 처음부터 다시 한다
         */
        const val CONNECT_MS = 5_000L // 서비스 연결
        const val ROUTE_MS = 45_000L // 경로 찾기
        const val INFO_MS = 3_000L // 정보 읽기

        /**
         * 작성: 2026-11-11 12:08:00 · 박건준
         * 변수: ARRIVE_RADIUS_M, ARRIVE_DEDUP_MS
         * 목적: 도착으로 볼 반경(m)과, 같은 곳 도착을 한 번으로 묶는 시간
         *       (밀리초).
         * 근거: 미정 — 장소 알림(assets/places.json)과 같은 반경. 묶는 시간은
         *       근접 알림과 내비 도착이 함께 와도 한 번만 흘릴 만큼
         */
        const val ARRIVE_RADIUS_M = 200f // 도착 반경
        const val ARRIVE_DEDUP_MS = 60_000L // 같은 곳 도착 묶기
    }

    /** 내비 창구. `connect()`에서 연결한다 */
    private val helper = NaviHelper(context.applicationContext)

    /** 연결했는지. 처음엔 false */
    private var connected = false

    /** 연결을 한 번만 하게 막는 잠금 */
    private val lock = Mutex()

    /** 기다리는 경로 시작 이벤트. 기다리지 않을 땐 null */
    @Volatile
    private var routeStarted: CompletableDeferred<RouteStartInfo>? = null

    /** 기다리는 경유지 추가 이벤트. 기다리지 않을 땐 null */
    @Volatile
    private var waypointAdded: CompletableDeferred<RouteStartInfo>? = null

    /** 기다리는 목적지 정보. 기다리지 않을 땐 null */
    @Volatile
    private var destinationInfo: CompletableDeferred<DestinationInfo>? = null

    /** 기다리는 경유지 정보. 기다리지 않을 땐 null */
    @Volatile
    private var waypointInfo: CompletableDeferred<List<WaypointInfo>>? = null

    /** 도착 근접 알림 id(목적지·경유지). 안내를 취소하면 지운다 */
    private val arrivalAlerts = mutableListOf<String>()

    /** 마지막으로 흘린 도착(이름, 시각). 같은 곳 도착을 묶는 데 쓴다 */
    private var lastArrival: Pair<String, Long>? = null

    /** 도착 알림을 흘리는 곳 */
    private val arrived = MutableSharedFlow<Arrival>(extraBufferCapacity = 8)

    /** 목적지·경유지 도착 */
    val arrivals: SharedFlow<Arrival> = arrived

    /** 길안내 중인지 바꾸는 곳 */
    private val guiding = MutableStateFlow(false)

    /** 길안내 중인지(내비 상태가 경로 안내면 true) */
    val routing: StateFlow<Boolean> = guiding

    /** 내비 이벤트를 받아 기다리는 곳에 넘긴다 */
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            routeStarted?.complete(info)
        }
        override fun onWaypointChanged(info: WaypointChangedInfo) {
            if (info.cmdType == WaypointCmdType.ADD) waypointAdded?.complete(info.routeStartInfo)
        }
        override fun onDestinationInfo(info: DestinationInfo) {
            destinationInfo?.complete(info)
        }
        override fun onWayPointInfo(list: List<WaypointInfo>) {
            waypointInfo?.complete(list)
        }
        override fun onNavigationStatus(status: NavigationStatus) {
            guiding.value = status == NavigationStatus.ROUTE_DRIVE
        }
        override fun onRouteCancelled() {
            guiding.value = false
        }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = arrive(info.name, true)
        override fun onWayPointArrived(info: WaypointArrivedInfo) = arrive(info.name, false)
        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "내비 오류: $code")
            routeStarted?.cancel()
            waypointAdded?.cancel()
        }
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: findPlace
     * 목적: 이름·분류로 장소를 찾는다.
     * 인자: query — 찾을 말("주유소", "OO리조트")
     *       near — 이 근처에서 찾는다. null이면 지금 위치 근처
     *       limit — 최대 개수
     * 반환: 찾은 장소. 지금은 늘 빈 목록
     * 미구현: 바깥 API 작업 때 만든다. Pleos SDK에는 장소 검색이 없다
     *       (docs/api-spike.md 장소 검색 행). 그때까지 시연 장소는
     *       `PlaceWatcher` 고정 목록을 쓴다
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun findPlace(query: String, near: GeoPoint? = null, limit: Int = 5): List<PlaceHit> {
        Log.w(TAG, "장소 찾기는 아직 없음: $query")
        return emptyList()
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: startRoute
     * 목적: 목적지로 안내를 시작하고, 목적지에 도착 근접 알림을 건다.
     *       요청 전에 `routing`을 먼저 true로 올린다. 요청하면 내비 화면이
     *       앞으로 와서 앱이 뒤로 가는데, 그 뒤에는 깨어 있기 서비스를 켤 수
     *       없어서다(Android 12+ 뒤에서 포그라운드 서비스 시작 제한).
     *       경로를 못 찾으면 다시 내린다
     * 인자: destination — 목적지
     * 반환: 경로 요약. 연결·경로 찾기에 실패하면 null
     */
    suspend fun startRoute(destination: PlaceHit): RouteSummary? {
        if (!connect()) return null
        val wait = CompletableDeferred<RouteStartInfo>().also { routeStarted = it } // 경로 시작 이벤트
        guiding.value = true
        val sent = runCatching { // 요청을 보냈는지
            helper.requestRoute(
                RouteInfo(
                    latitude = destination.point.lat, longitude = destination.point.lng,
                    poiName = destination.name, poiId = "", address = destination.address, poiSubId = "",
                ),
            )
        }.onFailure { Log.w(TAG, "경로 요청 실패: ${it.message}") }.isSuccess
        val info = if (sent) withTimeoutOrNull(ROUTE_MS) { runCatching { wait.await() }.getOrNull() } else null // 경로
        routeStarted = null
        if (info == null) {
            guiding.value = false
            return null
        }
        clearArrivals()
        watchArrival(destination, isDestination = true)
        Log.i(TAG, "경로 시작,${destination.name},${info.distance}m,${info.duration}초,${info.tollFare}원")
        return RouteSummary(info.distance, info.duration, info.tollFare)
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: addWaypoint
     * 목적: 경유지를 더하고, 그곳에 도착 근접 알림을 건다.
     * 인자: place — 경유지
     *       index — 몇 번째 경유지인지(1·2). 그 밖이면 더하지 않는다
     * 반환: 그 경유지까지 경로 요약. 실패하면 null
     * 근거: 측정 — `WaypointIndex`가 FIRST·SECOND뿐이라 이 함수로는 2개까지
     *       (docs/api-spike.md 경유지 행)
     */
    suspend fun addWaypoint(place: PlaceHit, index: Int): RouteSummary? {
        val slot = when (index) { // SDK 경유지 자리
            1 -> WaypointIndex.FIRST
            2 -> WaypointIndex.SECOND
            else -> return null
        }
        if (!connect()) return null
        val wait = CompletableDeferred<RouteStartInfo>().also { waypointAdded = it } // 경유지 추가 이벤트
        val sent = runCatching { // 요청을 보냈는지
            helper.addWaypoint(
                RequestWaypointInfo(
                    latitude = place.point.lat, longitude = place.point.lng, poiName = place.name,
                    waypointIndex = slot, poiId = "", address = place.address, poiSubId = "",
                ),
            )
        }.onFailure { Log.w(TAG, "경유지 요청 실패: ${it.message}") }.isSuccess
        val info = if (sent) withTimeoutOrNull(ROUTE_MS) { runCatching { wait.await() }.getOrNull() } else null // 경로
        waypointAdded = null
        if (info == null) return null
        watchArrival(place, isDestination = false)
        Log.i(TAG, "경유지,${place.name},${info.distance}m,${info.duration}초,${info.tollFare}원")
        return RouteSummary(info.distance, info.duration, info.tollFare)
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: remaining
     * 목적: 목적지와 경유지마다 남은 거리·시간을 읽는다.
     * 반환: 남은 거리. 안내 중이 아니거나 읽지 못하면 null
     */
    suspend fun remaining(): Remaining? {
        if (!connect()) return null
        val dest = CompletableDeferred<DestinationInfo>().also { destinationInfo = it } // 목적지 정보
        val ways = CompletableDeferred<List<WaypointInfo>>().also { waypointInfo = it } // 경유지 정보
        runCatching {
            helper.getDestinationInfo()
            helper.getWaypointInfo()
        }.onFailure { Log.w(TAG, "남은 거리 읽기 실패: ${it.message}") }
        val d = withTimeoutOrNull(INFO_MS) { dest.await() } // 목적지 정보, 못 받으면 null
        val w = withTimeoutOrNull(INFO_MS) { ways.await() }.orEmpty() // 경유지 정보
        destinationInfo = null
        waypointInfo = null
        if (d == null || d.name.isEmpty()) return null
        return Remaining(
            Leg(d.name, d.remainDistance, d.remainTime),
            w.map { Leg(it.name, it.remainDistance, it.remainTime) },
        )
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: cancelRoute
     * 목적: 안내를 취소하고 도착 근접 알림을 지운다.
     */
    suspend fun cancelRoute() {
        if (connect()) runCatching { helper.cancelRoute() }.onFailure { Log.w(TAG, "안내 취소 실패: ${it.message}") }
        clearArrivals()
        guiding.value = false
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: close
     * 목적: 리스너를 끊고 내비 연결을 놓는다.
     */
    override fun close() {
        clearArrivals()
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: connect
     * 목적: 처음 한 번만 내비 서비스에 연결하고 리스너를 붙인다.
     * 반환: 연결됐으면 true
     */
    private suspend fun connect(): Boolean = lock.withLock {
        if (connected) return@withLock true
        connected = withTimeoutOrNull(CONNECT_MS) {
            suspendCancellableCoroutine { cont ->
                helper.initialize(
                    { if (cont.isActive) cont.resume(true) },
                    { Log.w(TAG, "내비 연결 실패: ${it.message}"); if (cont.isActive) cont.resume(false) },
                )
            }
        } == true
        if (connected) helper.addListener(listener)
        connected
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: watchArrival
     * 목적: 목적지·경유지 좌표에 도착 근접 알림을 건다.
     * 인자: place — 목적지나 경유지
     *       isDestination — 목적지면 true
     */
    private suspend fun watchArrival(place: PlaceHit, isDestination: Boolean) {
        val id = proximity.add(place.point, ARRIVE_RADIUS_M, onEnter = { arrive(place.name, isDestination) }) // 알림 id
        if (id != null) synchronized(arrivalAlerts) { arrivalAlerts += id }
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: clearArrivals
     * 목적: 도착 근접 알림을 모두 지운다.
     */
    private fun clearArrivals() {
        synchronized(arrivalAlerts) { arrivalAlerts.toList().also { arrivalAlerts.clear() } }.forEach(proximity::remove)
    }

    /**
     * 작성: 2026-11-11 12:08:00 · 박건준
     * 함수: arrive
     * 목적: 도착을 흘린다. 같은 곳 도착이 `ARRIVE_DEDUP_MS` 안에 다시 오면
     *       흘리지 않는다(근접 알림과 내비 도착 이벤트가 함께 올 때).
     * 인자: name — 도착한 곳 이름
     *       isDestination — 목적지면 true
     */
    @Synchronized
    private fun arrive(name: String, isDestination: Boolean) {
        val now = SystemClock.elapsedRealtime() // 지금 시각(밀리초)
        val last = lastArrival // 마지막 도착
        if (last != null && last.first == name && now - last.second < ARRIVE_DEDUP_MS) return
        lastArrival = name to now
        Log.i(TAG, "도착,$name,${if (isDestination) "목적지" else "경유지"}")
        arrived.tryEmit(Arrival(name, isDestination))
    }
}
