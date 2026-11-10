package com.westone.sunstone.demo

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 작성: 2026-11-10 14:34:00 · 박건준
 * 클래스: LocationProbe
 * 목적: Pleos FusedLocation이 에뮬레이터 위치(`adb emu geo fix`)를 따라가는지,
 *       근접 알림(들어감·나감)이 오는지 잰다. 실측용이라 debug 빌드에만
 *       있다. 결과는 `location,` 줄로 남긴다.
 *       1. 지원 여부·마지막 위치
 *       2. 위치 콜백과 근접 알림(`TARGET` 반경 `RADIUS_M`)을 등록하고
 *          `LISTEN_MS` 기다린다. 그동안 밖에서 `geo fix`로 위치를 옮긴다
 * 인자: context — 앱 Context. 위치 서비스 연결에 쓴다
 * 근거: 측정 — `geo fix` 뒤 FusedLocation 서비스 로그
 *       `onLocationCallback FusedLocationData(…)`가 새 좌표로 바뀌었다
 */
internal class LocationProbe(context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-10 14:34:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-10 14:34:00 · 박건준
         * 변수: CONNECT_MS, LISTEN_MS
         * 목적: 서비스 연결과 위치를 옮겨 보는 동안 기다리는 시간(밀리초).
         * 근거: 미정 — `initialize()`가 연결을 기다리지 않아 넉넉히 잡은 값
         */
        const val CONNECT_MS = 1_000L // 서비스 연결
        const val LISTEN_MS = 60_000L // 위치 옮겨 보기

        /**
         * 작성: 2026-11-10 14:34:00 · 박건준
         * 변수: TARGET, RADIUS_M
         * 목적: 근접 알림 지점(해운대해수욕장, 내비 실측과 같은 좌표)과
         *       반경(m).
         * 근거: 미정 — 세차장 하나 크기를 넉넉히 잡은 값
         */
        val TARGET = 35.1587 to 129.1604 // (위도, 경도)
        const val RADIUS_M = 200f // 반경
    }

    /** 위치 창구. `run()` 안에서만 연결한다 */
    private val fused = FusedLocation(context.applicationContext)

    /** 받은 위치를 남긴다 */
    private val location = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            Log.i(TAG, "location,위치,${data?.latitude},${data?.longitude},${data?.confidence}")
        }
        override fun onError(e: Exception) {
            Log.i(TAG, "location,위치 실패,${e.message?.replace(',', ';')}")
        }
    }

    /** 등록된 근접 알림 id. 등록 전엔 null */
    private var alertId: String? = null

    /** 근접 알림을 남기고 id를 기억한다 */
    private val proximity = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) {
            this@LocationProbe.alertId = alertId
            Log.i(TAG, "location,근접 등록,$alertId")
        }
        override fun onTransition(event: ProximityAlertEvent) {
            Log.i(TAG, "location,근접,${event.alertId},${event.transition}")
        }
        override fun onError(e: Exception) {
            Log.i(TAG, "location,근접 실패,${e.message?.replace(',', ';')}")
        }
    }

    /**
     * 작성: 2026-11-10 14:34:00 · 박건준
     * 함수: run
     * 목적: 클래스 설명의 1~2단계를 하고, 끝나면 등록을 모두 끊는다.
     */
    suspend fun run() {
        fused.initialize()
        delay(CONNECT_MS)
        Log.i(TAG, "location,지원,${runCatching { fused.isSupported() }.getOrElse { "실패 ${it.message}" }}")
        runCatching { fused.getLastKnownLocation(location) }
            .onFailure { Log.i(TAG, "location,마지막 위치 실패,${it.message}") }
        val request = ProximityAlertRequest( // 근접 알림 요청
            TARGET.first, TARGET.second, RADIUS_M, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
        )
        try {
            runCatching { fused.registerFusedLocationCallback(location) }
                .onFailure { Log.i(TAG, "location,위치 등록 실패,${it.message}") }
            runCatching { fused.addProximityAlert(request, proximity) }
                .onFailure { Log.i(TAG, "location,근접 등록 실패,${it.message}") }
            Log.i(TAG, "location,대기,${LISTEN_MS / 1000}초")
            delay(LISTEN_MS)
        } finally {
            alertId?.let { id -> runCatching { fused.removeProximityAlert(id) } }
            runCatching { fused.unregisterFusedLocationCallback(location) }
            runCatching { fused.release() }
            Log.i(TAG, "location 끝")
        }
    }
}
