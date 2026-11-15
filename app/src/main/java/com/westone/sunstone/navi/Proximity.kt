package com.westone.sunstone.navi

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: Proximity
 * 목적: Pleos FusedLocation 근접 알림을 감싼다. 한 점 둘레에 들어가고
 *       나갈 때 부를 함수를 등록한다. 장소 도착(`PlaceWatcher`)과 내비
 *       목적지 도착(`PleosNavi`)이 함께 쓴다.
 * 인자: context — 앱 Context. 위치 서비스 연결에 쓴다
 * 근거: 측정 — `geo fix`로 옮긴 위치에 ENTER·EXIT가 1초 안에 왔다
 *       (docs/api-spike.md 근접 알림 행). `pleos.car.permission.FUSED_LOCATION`
 *       (dangerous)이 있어야 한다
 */
class Proximity(context: Context) : AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-11 12:00:00 · 박건준
         * 변수: TAG
         * 목적: 위치 관련 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-11 12:00:00 · 박건준
         * 변수: CONNECT_MS, REGISTER_MS
         * 목적: 서비스 연결과 알림 등록 확인을 기다리는 시간(밀리초).
         * 근거: 측정 — `initialize()`가 연결을 기다리지 않고 돌아와서, 실측
         *       (`location`)에서 1초 뒤 부른 호출은 모두 됐다. 등록 확인은
         *       바로 왔다
         */
        const val CONNECT_MS = 1_000L // 서비스 연결
        const val REGISTER_MS = 3_000L // 등록 확인
    }

    /** 위치 창구. 처음 등록할 때 연결한다 */
    private val fused = FusedLocation(context.applicationContext)

    /** 연결했는지. 처음엔 false */
    private var connected = false

    /** 연결을 한 번만 하게 막는 잠금 */
    private val lock = Mutex()

    /** 등록한 알림 id. `close()`에서 모두 지운다 */
    private val ids = mutableSetOf<String>()

    /**
     * 작성: 2026-11-11 12:00:00 · 박건준
     * 함수: add
     * 목적: 한 점 둘레에 근접 알림을 등록한다.
     * 인자: point — 가운데 좌표
     *       radiusM — 반경(m)
     *       onEnter — 들어갈 때 부를 함수
     *       onExit — 나갈 때 부를 함수
     * 반환: 알림 id. 권한이 없거나 등록이 확인되지 않으면 null
     */
    suspend fun add(point: GeoPoint, radiusM: Float, onEnter: () -> Unit, onExit: () -> Unit = {}): String? {
        connect()
        val request = ProximityAlertRequest( // 근접 알림 요청
            point.lat, point.lng, radiusM, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
        )
        return withTimeoutOrNull(REGISTER_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : ProximityAlertListener { // 등록 확인과 들어감·나감
                    override fun onRegistered(alertId: String) {
                        if (!cont.isActive) {
                            runCatching { fused.removeProximityAlert(alertId) }
                            return
                        }
                        synchronized(ids) { ids += alertId }
                        if (cont.isActive) cont.resume(alertId)
                    }
                    override fun onTransition(event: ProximityAlertEvent) {
                        if (event.transition == ProximityTransition.ENTER) onEnter() else onExit()
                    }
                    override fun onError(e: Exception) {
                        Log.w(TAG, "근접 알림 오류: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                }
                runCatching { fused.addProximityAlert(request, listener) }.onFailure {
                    Log.w(TAG, "근접 알림 등록 실패: ${it.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /**
     * 작성: 2026-11-11 12:00:00 · 박건준
     * 함수: remove
     * 목적: 등록한 알림 하나를 지운다.
     * 인자: id — `add()`가 돌려준 알림 id
     */
    fun remove(id: String) {
        runCatching { fused.removeProximityAlert(id) }
        synchronized(ids) { ids -= id }
    }

    /**
     * 작성: 2026-11-11 12:00:00 · 박건준
     * 함수: close
     * 목적: 알림을 모두 지우고 연결을 놓는다.
     */
    override fun close() {
        synchronized(ids) { ids.toList() }.forEach(::remove)
        runCatching { fused.release() }
    }

    /**
     * 작성: 2026-11-11 12:00:00 · 박건준
     * 함수: connect
     * 목적: 처음 한 번만 서비스에 연결하고 `CONNECT_MS` 기다린다.
     */
    private suspend fun connect() = lock.withLock {
        if (connected) return@withLock
        fused.initialize()
        delay(CONNECT_MS)
        connected = true
    }
}
