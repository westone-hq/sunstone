package com.westone.sunstone.navi

import com.westone.sunstone.routines.model.GeoPoint as RoutineGeoPoint
import com.westone.sunstone.routines.model.PlaceHit as RoutinePlaceHit
import com.westone.sunstone.routines.model.RouteSummary as RoutineRouteSummary
import com.westone.sunstone.routines.port.NaviPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: PleosNaviPort
 * 목적: 기능 쪽 내비 창구(`NaviPort`)를 Pleos 내비(`PleosNavi`)로 채운다.
 *       값 모양만 바꿔 넘기고 판단은 하지 않는다.
 * 인자: navi — Pleos 내비
 *       places — 이름으로 찾을 수 있는 시연 장소(`PlaceWatcher.spots`)
 * 미구현: 프로토타입이라 장소 찾기는 바깥 API(카카오 로컬) 대신 시연 장소 고정
 *       목록(assets/places.json)에서 이름으로 찾는다. 목록에 없는 목적지는
 *       `TripDialog`가 "찾지 못했어요"로 안내한다
 */
class PleosNaviPort(
    private val navi: PleosNavi,
    private val places: () -> List<PlaceHit>,
) : NaviPort {

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: findPlace
     * 목적: 시연 장소 목록에서 이름이 겹치는 곳을 찾는다. 띄어쓰기는 무시한다.
     * 인자: query — 운전자가 말한 목적지
     * 반환: 찾은 장소. 없으면 null
     */
    override suspend fun findPlace(query: String): RoutinePlaceHit? {
        val key = query.replace(" ", "") // 띄어쓰기를 뺀 검색어
        val hit = places().firstOrNull { // 이름이 겹치는 첫 장소
            val name = it.name.replace(" ", "")
            name.contains(key) || key.contains(name)
        } ?: return null
        return RoutinePlaceHit(hit.name, RoutineGeoPoint(hit.point.lat, hit.point.lng))
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: startRoute
     * 목적: Pleos 내비로 경로를 시작한다.
     * 인자: destination — 목적지
     * 반환: 경로 요약. 실패하면 null
     * 근거: 측정 — `requestRoute` 뒤 `onRouteStarted`까지 1~10초 걸린다
     *       (docs/api-spike.md 앱 얼림 대응 절)
     */
    override suspend fun startRoute(destination: RoutinePlaceHit): RoutineRouteSummary? =
        navi.startRoute(destination.toNavi())?.toRoutine() // → 로직 이동: PleosNavi.startRoute()

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: addWaypoint
     * 목적: 지금 경로에 첫 번째 경유지를 넣는다.
     * 인자: place — 경유지
     * 반환: 경로 요약. 실패하면 null
     */
    override suspend fun addWaypoint(place: RoutinePlaceHit): RoutineRouteSummary? =
        navi.addWaypoint(place.toNavi(), 1)?.toRoutine() // → 로직 이동: PleosNavi.addWaypoint()

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: toNavi
     * 목적: 기능 쪽 장소를 Pleos 내비 장소로 바꾼다.
     */
    private fun RoutinePlaceHit.toNavi() = PlaceHit(name, GeoPoint(point.lat, point.lng))

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: toRoutine
     * 목적: Pleos 내비 경로 요약을 기능 쪽 값으로 바꾼다.
     */
    private fun RouteSummary.toRoutine() = RoutineRouteSummary(distanceM, durationSec, tollWon)
}
