package com.westone.sunstone.demo

import android.util.Log
import com.westone.sunstone.navi.GeoPoint
import com.westone.sunstone.navi.PlaceHit
import com.westone.sunstone.navi.PlaceWatcher
import com.westone.sunstone.navi.PleosNavi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 작성: 2026-11-10 14:14:00 · 박건준
 * 수정: 2026-11-11 12:28:00 · 박건준
 * 클래스: NaviProbe
 * 목적: 내비 창구(`PleosNavi`)를 차례로 불러 본다. 실측용이라 debug
 *       빌드에만 있다. 단계마다 `navi,단계,이름` 줄을 남기고 `STEP_MS`
 *       기다린다(그동안 화면을 캡처한다).
 *       1. 목적지 해운대로 안내 시작
 *       2. 경유지 1(시연 주유소)·2(대전) 더하기
 *       3. 남은 거리·시간 읽기
 *       4. 도착 기다리기(`ARRIVE_MS`, 그동안 `adb emu geo fix`로 위치를
 *          주유소로 옮긴다)
 *       5. 안내 취소
 * 인자: navi — 내비 창구
 *       places — 시연 장소 목록(주유소 좌표를 꺼낸다)
 * 근거: 측정 — 2026-11-10 NaviHelper를 직접 부른 실측(docs/api-spike.md
 *       내비 실측 절)
 */
internal class NaviProbe(private val navi: PleosNavi, private val places: PlaceWatcher) {

    companion object {
        /**
         * 작성: 2026-11-10 14:14:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        private const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-10 14:14:00 · 박건준
         * 수정: 2026-11-11 12:28:00 · 박건준
         * 변수: STEP_MS, ARRIVE_MS
         * 목적: 단계마다, 도착을 기다리는 시간(밀리초).
         * 근거: 미정 — 화면을 캡처하고 위치를 옮길 만큼 넉넉히 잡은 값
         */
        private const val STEP_MS = 8_000L // 단계마다
        private const val ARRIVE_MS = 30_000L // 도착 기다리기

        /**
         * 작성: 2026-11-10 14:14:00 · 박건준
         * 수정: 2026-11-11 12:28:00 · 박건준
         * 변수: DESTINATION, REST_AREA
         * 목적: 목적지와 두 번째 경유지.
         * 근거: 미정 — 지도에서 고른 대략 좌표
         */
        val DESTINATION = PlaceHit("해운대해수욕장", GeoPoint(35.1587, 129.1604)) // 목적지
        private val REST_AREA = PlaceHit("대전", GeoPoint(36.3504, 127.3845)) // 경유지 2

        /**
         * 작성: 2026-11-11 12:28:00 · 박건준
         * 함수: startDemo
         * 목적: 시나리오 ①처럼 해운대 안내를 시작하고 시연 주유소를 경유지
         *       1로 넣는다(데모 `navi start`).
         * 인자: navi — 내비 창구
         *       places — 시연 장소 목록
         */
        suspend fun startDemo(navi: PleosNavi, places: PlaceWatcher) {
            Log.i(TAG, "navi,시작,${navi.startRoute(DESTINATION)}") // → 로직 이동: PleosNavi.startRoute()
            val gas = places.find("gas")?.hit ?: return // 시연 주유소
            Log.i(TAG, "navi,경유지,${navi.addWaypoint(gas, 1)}") // → 로직 이동: PleosNavi.addWaypoint()
        }
    }

    /**
     * 작성: 2026-11-10 14:14:00 · 박건준
     * 수정: 2026-11-11 12:28:00 · 박건준
     * 함수: run
     * 목적: 클래스 설명의 1~5단계를 차례로 한다. 도착은 따로 모아 남긴다.
     */
    suspend fun run() = coroutineScope {
        val arrivals = launch { navi.arrivals.collect { Log.i(TAG, "navi,도착,${it.name},${it.isDestination}") } }
        step("목적지 ${DESTINATION.name}") { navi.startRoute(DESTINATION) } // → 로직 이동: PleosNavi.startRoute()
        val gas = places.find("gas")?.hit // 시연 주유소, 목록에 없으면 null
        if (gas != null) step("경유지 1 ${gas.name}") { navi.addWaypoint(gas, 1) } // → 로직 이동: PleosNavi.addWaypoint()
        step("경유지 2 ${REST_AREA.name}") { navi.addWaypoint(REST_AREA, 2) }
        step("남은 거리·시간") { navi.remaining() } // → 로직 이동: PleosNavi.remaining()
        Log.i(TAG, "navi,단계,도착 대기,${ARRIVE_MS / 1000}초")
        delay(ARRIVE_MS)
        step("안내 취소") { navi.cancelRoute() } // → 로직 이동: PleosNavi.cancelRoute()
        arrivals.cancel()
        Log.i(TAG, "navi 끝")
    }

    /**
     * 작성: 2026-11-11 12:28:00 · 박건준
     * 함수: step
     * 목적: 단계 이름과 결과를 남기고 `STEP_MS` 기다린다.
     * 인자: name — 단계 이름
     *       call — 내비 창구 호출. 돌려준 값을 결과로 남긴다
     */
    private suspend fun step(name: String, call: suspend () -> Any?) {
        Log.i(TAG, "navi,단계,$name")
        val result = runCatching { call() }.fold({ it.toString() }, { "실패 ${it.message}" }) // 결과
        Log.i(TAG, "navi,결과,$name,${result.replace(',', ';')}")
        delay(STEP_MS)
    }
}
