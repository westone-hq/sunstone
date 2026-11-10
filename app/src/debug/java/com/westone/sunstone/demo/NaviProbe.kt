package com.westone.sunstone.demo

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 작성: 2026-11-10 14:14:00 · 박건준
 * 클래스: NaviProbe
 * 목적: Pleos 내비(NaviHelper 3.0.5)가 에뮬레이터에서 무엇을 해 주는지
 *       잰다. 실측용이라 debug 빌드에만 있다. 단계마다 `navi,단계,이름` 줄을
 *       남기고 `STEP_MS` 기다린다(그동안 화면을 캡처한다). 리스너가 받은
 *       것은 모두 `navi,이벤트,이름,값` 줄로 남긴다.
 *       1. 현재 위치·경로 상태 읽기
 *       2. 목적지 해운대로 안내 시작
 *       3. 경유지 1·2 더하기
 *       4. 남은 거리·시간·통행료 읽기
 *       5. 경유지 3개로 경로 요청(2개 제한 확인)
 *       6. 도착 알림 기다리기(`ARRIVE_MS`, 그동안 `adb emu geo fix`로
 *          위치를 목적지로 옮겨 본다)
 *       7. 안내 취소
 * 인자: context — 앱 Context. 내비 서비스 연결에 쓴다
 * 근거: 측정 — NaviHelper 공개 함수는 `javap`로 확인했다. 이름·분류로
 *       장소를 찾는 함수는 없다(docs/api-spike.md 내비 행)
 */
internal class NaviProbe(context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-10 14:14:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-10 14:14:00 · 박건준
         * 변수: INIT_MS, STEP_MS, ARRIVE_MS
         * 목적: 서비스 연결, 단계마다, 도착 알림을 기다리는 시간(밀리초).
         * 근거: 미정 — 이벤트가 오고 화면을 캡처할 만큼 넉넉히 잡은 값
         */
        const val INIT_MS = 5_000L // 서비스 연결
        const val STEP_MS = 10_000L // 단계마다
        const val ARRIVE_MS = 60_000L // 도착 알림

        /**
         * 작성: 2026-11-10 14:14:00 · 박건준
         * 변수: DESTINATION, STATION, REST_AREA, EXTRA
         * 목적: 목적지와 경유지 좌표(위도, 경도, 이름).
         * 근거: 미정 — 지도에서 고른 대략 좌표. 주유소는 실제 상호가 아니라
         *       서울 양재 부근 지점이다
         */
        val DESTINATION = Triple(35.1587, 129.1604, "해운대해수욕장") // 목적지
        val STATION = Triple(37.4846, 127.0340, "시험 주유소") // 경유지 1
        val REST_AREA = Triple(36.3504, 127.3845, "대전") // 경유지 2
        val EXTRA = Triple(35.8714, 128.6014, "대구") // 3번째 경유지(제한 확인)
    }

    /** 내비 창구. `run()` 안에서만 연결한다 */
    private val navi = NaviHelper(context.applicationContext)

    /** 도착 알림을 받았는지. 받기 전엔 false */
    private var arrived = false

    /** 받은 이벤트를 모두 남긴다 */
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = event("onRouteStarted", info)
        override fun onRouteEnded() = event("onRouteEnded", null)
        override fun onRoutingCompleted() = event("onRoutingCompleted", null)
        override fun onReRouteEnded() = event("onReRouteEnded", null)
        override fun onRouteCancelled() = event("onRouteCancelled", null)
        override fun onWaypointChanged(info: WaypointChangedInfo) = event("onWaypointChanged", info)
        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            arrived = true
            event("onDestinationArrived", info)
        }
        override fun onWayPointArrived(info: WaypointArrivedInfo) = event("onWayPointArrived", info)
        override fun onDestinationChanged() = event("onDestinationChanged", null)
        override fun onError(code: NaviErrorCode) = event("onError", code)
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = event("onCurrentLocationInfo", info)
        override fun onRouteStateInfo(info: RouteStateInfo) = event("onRouteStateInfo", info)
        override fun onDestinationInfo(info: DestinationInfo) = event("onDestinationInfo", info)
        override fun onWayPointInfo(list: List<WaypointInfo>) = event("onWayPointInfo", list)
        override fun onDrivingInfo(info: DrivingInfo) = event("onDrivingInfo", info)
        override fun onNavigationStatus(status: NavigationStatus) = event("onNavigationStatus", status)
    }

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: run
     * 목적: 클래스 설명의 1~7단계를 차례로 한다. 한 단계가 실패해도 다음으로
     *       넘어가고, 끝나면 리스너를 끊고 연결을 놓는다.
     */
    suspend fun run() {
        val connected = runCatching { connect() } // 서비스 연결 성공 여부
            .onFailure { Log.i(TAG, "navi,연결,실패,${clean(it.message)}") }
            .isSuccess
        if (!connected) return
        Log.i(TAG, "navi,연결,성공")
        navi.addListener(listener)
        try {
            step("현재 위치·경로 상태") {
                navi.getCurrentLocationInfo()
                navi.getRouteStateInfo()
            }
            step("목적지 ${DESTINATION.third}") { navi.requestRoute(route(DESTINATION)) }
            step("경유지 1 ${STATION.third}") { navi.addWaypoint(waypoint(STATION, WaypointIndex.FIRST)) }
            step("경유지 2 ${REST_AREA.third}") { navi.addWaypoint(waypoint(REST_AREA, WaypointIndex.SECOND)) }
            step("남은 거리·시간·통행료") {
                navi.getDestinationInfo()
                navi.getWaypointInfo()
                navi.getRouteStateInfo()
            }
            step("경유지 3개 요청") {
                val three = listOf( // 경유지 3개
                    waypoint(STATION, WaypointIndex.FIRST),
                    waypoint(REST_AREA, WaypointIndex.SECOND),
                    waypoint(EXTRA, WaypointIndex.SECOND),
                )
                navi.requestRouteWithWaypoints(RouteWithWaypointsRequest(route(DESTINATION), three))
            }
            step("경유지 3개 요청 뒤 경유지") { navi.getWaypointInfo() }
            Log.i(TAG, "navi,단계,도착 대기,${ARRIVE_MS / 1000}초")
            delay(ARRIVE_MS)
            Log.i(TAG, "navi,도착,${if (arrived) "받음" else "안 받음"}")
            step("안내 취소") {
                navi.cancelRoute()
                navi.getRouteStateInfo()
            }
        } finally {
            runCatching { navi.removeListener(listener) }
            runCatching { navi.release() }
            Log.i(TAG, "navi 끝")
        }
    }

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: connect
     * 목적: 내비 서비스에 연결하고 성공·실패 콜백을 기다린다.
     * 근거: 측정 — `initialize(onSuccess, onFail)` 두 콜백 서명(javap)
     */
    private suspend fun connect() = withTimeout(INIT_MS) {
        suspendCancellableCoroutine { cont ->
            navi.initialize({ if (cont.isActive) cont.resume(Unit) },
                { if (cont.isActive) cont.resumeWithException(it) })
        }
    }

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: step
     * 목적: 단계 이름을 남기고 호출한 뒤 `STEP_MS` 기다린다. 호출이 던지면
     *       실패로 남긴다.
     * 인자: name — 단계 이름
     *       call — 내비 호출
     */
    private suspend fun step(name: String, call: () -> Unit) {
        Log.i(TAG, "navi,단계,$name")
        runCatching(call).onFailure { Log.i(TAG, "navi,호출 실패,$name,${clean(it.toString())}") }
        delay(STEP_MS)
    }

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: event
     * 목적: 받은 이벤트를 한 줄로 남긴다. CSV로 옮기려고 값의 쉼표를 바꾼다.
     * 인자: name — 리스너 함수 이름
     *       value — 받은 값. 없으면 null
     */
    private fun event(name: String, value: Any?) {
        Log.i(TAG, "navi,이벤트,$name,${clean(value?.toString())}")
    }

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: clean
     * 목적: CSV 칸을 깨지 않게 쉼표·줄바꿈을 바꾼다.
     * 반환: 바꾼 글자. null이면 빈 글자
     */
    private fun clean(text: String?): String = text.orEmpty().replace(',', ';').replace('\n', ' ')

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: route
     * 목적: 좌표로 목적지 요청을 만든다.
     * 인자: point — (위도, 경도, 이름)
     */
    private fun route(point: Triple<Double, Double, String>) =
        RouteInfo(latitude = point.first, longitude = point.second, poiName = point.third, poiId = "",
            address = "", poiSubId = "")

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 함수: waypoint
     * 목적: 좌표로 경유지 요청을 만든다.
     * 인자: point — (위도, 경도, 이름)
     *       index — 몇 번째 경유지인지
     */
    private fun waypoint(point: Triple<Double, Double, String>, index: WaypointIndex) =
        RequestWaypointInfo(latitude = point.first, longitude = point.second, poiName = point.third,
            waypointIndex = index, poiId = "", address = "", poiSubId = "")
}
