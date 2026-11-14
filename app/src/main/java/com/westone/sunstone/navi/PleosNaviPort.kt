package com.westone.sunstone.navi

import com.westone.sunstone.routines.model.GeoPoint as RoutineGeoPoint
import com.westone.sunstone.routines.model.PlaceHit as RoutinePlaceHit
import com.westone.sunstone.routines.model.RouteSummary as RoutineRouteSummary
import com.westone.sunstone.routines.port.NaviPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: PleosNaviPort
 * 목적: 기능 쪽 내비 창구(`NaviPort`)를 Pleos 내비(`PleosNavi`)로 채운다.
 *       값 모양만 바꿔 넘기고 판단은 하지 않는다.
 * 인자: navi — Pleos 내비
 *       places — 이름으로 찾을 수 있는 시연 장소(`PlaceWatcher.spots`). 장소의
 *       성격(집·유치원)도 함께 넘긴다
 * 목적: 실제 검색은 카카오 후보 목록으로 확장하며, 실패하면 출처를 밝힌 고정 목록으로 대체한다.
 * 미구현: 키·통신 또는 기기 내비 서비스가 없으면 실제 길안내 성공을 검증할 수 없다.
 */
class PleosNaviPort(
    private val navi: PleosNavi,
    private val places: () -> List<PlaceWatcher.Spot>,
    private val search: (suspend (String) -> List<RoutinePlaceHit>)? = null,
    private val selectedStation: suspend (RoutinePlaceHit) -> Unit = {},
    private val searchEnabled: () -> Boolean = { true },
    private val clearStation: () -> Unit = {},
) : NaviPort {

    /** 함수: findPlaces
     * 목적: 실제 검색 실패 시에만 출처를 밝힌 고정 장소로 대체한다.
     */
    override suspend fun findPlaces(query: String): List<RoutinePlaceHit> {
        if (!searchEnabled()) return listOfNotNull(findPlace(query))
        if (search != null) {
            try {
                val found = search.invoke(query)
                if (found.isNotEmpty()) return found.map { result ->
                    val known = places().firstOrNull {
                        it.hit.name == result.name &&
                            kotlin.math.abs(it.hit.point.lat - result.point.lat) < 0.001 &&
                            kotlin.math.abs(it.hit.point.lng - result.point.lng) < 0.001
                    } // 이름과 좌표가 모두 같은 기존 등록 장소의 의미만 보존한다
                    if (known == null) result else result.copy(kind = known.place)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            } catch (_: Exception) {
                android.util.Log.w("Sunstone", "실제 장소 검색 실패: 키·통신 상태 확인 필요")
            }
        }
        return listOfNotNull(findPlace(query)?.let {
            if (search == null) it else it.copy(source = "고정 목적지 대체")
        })
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: findPlace
     * 목적: 시연 장소 목록에서 이름이 겹치는 곳을 찾는다. 띄어쓰기는 무시한다.
     *       장소의 성격(집·유치원)을 붙여 넘긴다.
     * 인자: query — 운전자가 말한 목적지
     * 반환: 찾은 장소. 없으면 null
     */
    override suspend fun findPlace(query: String): RoutinePlaceHit? {
        val key = query.replace(" ", "") // 띄어쓰기를 뺀 검색어
        val spot = places().firstOrNull { // 이름이 겹치는 첫 장소
            val name = it.hit.name.replace(" ", "") // 띄어쓰기를 뺀 장소 이름
            name.contains(key) || key.contains(name)
        } ?: return null
        val hit = spot.hit // 이름·좌표
        return RoutinePlaceHit(hit.name, RoutineGeoPoint(hit.point.lat, hit.point.lng), spot.place)
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-11 17:06:00 · 박희정
     * 함수: startRoute
     * 목적: Pleos 내비로 경로를 시작한다. 다시 요청하지 않는다.
     * 인자: destination — 목적지
     * 반환: 경로 요약. 실패하면 null
     * 근거: 측정 — 에뮬레이터를 켠 직후 첫 경로는 시작까지 24.5초 걸렸고,
     *       기다리다 다시 요청하면 내비가 앞 계산을 취소(`onRouteCancelled`)하고
     *       처음부터 다시 해 더 늦어졌다. 그래서 `PleosNavi`가 길게(45초)
     *       기다리게 하고 한 번만 요청한다
     */
    override suspend fun startRoute(destination: RoutinePlaceHit): RoutineRouteSummary? {
        val result = navi.startRoute(destination.toNavi())?.toRoutine()
        if (result != null) clearStation()
        return result
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: addWaypoint
     * 목적: 지금 경로에 첫 번째 경유지를 넣는다.
     * 인자: place — 경유지
     * 반환: 경로 요약. 실패하면 null
     */
    override suspend fun addWaypoint(place: RoutinePlaceHit): RoutineRouteSummary? {
        val result = navi.addWaypoint(place.toNavi(), 1)?.toRoutine() ?: return null
        return try {
            selectedStation(place)
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            android.util.Log.w("Sunstone", "경유지 추가 성공, 도착 감지 등록 실패")
            result.copy(arrivalTracked = false)
        }
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: toNavi
     * 목적: 기능 쪽 장소를 Pleos 내비 장소로 바꾼다.
     */
    private fun RoutinePlaceHit.toNavi() = PlaceHit(name, GeoPoint(point.lat, point.lng), address)

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: toRoutine
     * 목적: Pleos 내비 경로 요약을 기능 쪽 값으로 바꾼다.
     */
    private fun RouteSummary.toRoutine() = RoutineRouteSummary(distanceM, durationSec, tollWon)
}
