package com.westone.sunstone.routines.fake

import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.RouteSummary
import com.westone.sunstone.routines.port.NaviPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: FakeNavi
 * 목적: 메모리 속 가짜 내비. 단위 테스트와 차 없는 시연에 쓴다. 장소는
 *       [places]에서 이름으로 찾고, 경로는 [route]를 그대로 돌려준다.
 * 인자: places — 찾을 수 있는 장소들
 *       route — 경로를 시작하거나 경유지를 넣으면 돌려줄 요약. null이면 실패
 */
class FakeNavi(
    private val places: List<PlaceHit>,
    var route: RouteSummary? = RouteSummary(distanceM = 382_000, durationSec = 17_820, tollWon = 16_010),
) : NaviPort {

    /** 지금 경로의 목적지. 경로를 시작하지 않았으면 null */
    var destination: PlaceHit? = null
        private set

    /** 넣은 경유지들, 넣은 순서대로 */
    val waypoints = mutableListOf<PlaceHit>()

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: findPlace
     * 목적: 이름이 겹치는 장소를 찾는다. 띄어쓰기는 무시한다.
     */
    override suspend fun findPlace(query: String): PlaceHit? {
        val key = query.replace(" ", "") // 띄어쓰기를 뺀 검색어
        return places.firstOrNull { it.name.replace(" ", "").contains(key) || key.contains(it.name.replace(" ", "")) }
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: startRoute
     * 목적: 목적지를 정하고 경유지를 비운다. [route]가 null이면 실패한다.
     */
    override suspend fun startRoute(destination: PlaceHit): RouteSummary? {
        val r = route ?: return null // 돌려줄 경로, 실패면 null
        this.destination = destination
        waypoints.clear()
        return r
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: addWaypoint
     * 목적: 경유지를 더한다. 경로가 없거나 [route]가 null이면 실패한다.
     */
    override suspend fun addWaypoint(place: PlaceHit): RouteSummary? {
        if (destination == null) return null
        val r = route ?: return null // 돌려줄 경로, 실패면 null
        waypoints += place
        return r
    }
}
