package com.westone.sunstone.navi

import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit

/** 클래스: SelectedStation
 * 목적: 고정 시연 목록과 별도로 실제 선택한 주유소의 ENTER·EXIT를 장소 상태에 연결한다.
 */
class SelectedStation(
    private val add: suspend (PlaceHit, () -> Unit, () -> Unit) -> String?,
    private val remove: (String) -> Unit,
    private val onPlace: (Place) -> Unit,
) {
    private var id: String? = null
    @Volatile private var generation = 0L
    @Volatile private var inside = false

    /** 함수: watch
     * 목적: 재선택할 때 이전 알림을 해제하고 오래된 콜백을 무시한다.
     */
    suspend fun watch(place: PlaceHit) {
        clear()
        val token = generation
        val registered = try { add(place, {
            if (token == generation) { inside = true; onPlace(Place.GAS_STATION) }
        }, {
            if (token == generation && inside) { inside = false; onPlace(Place.NONE) }
        }) } catch (e: Exception) {
            if (token == generation) generation++
            throw e
        }
        if (token == generation) id = registered else registered?.let(remove)
        if (registered == null && token == generation) generation++
        check(registered != null) { "선택 주유소 도착 감지 등록 실패" }
    }

    /** 함수: clear
     * 목적: 경로 취소·시연 재시작 시 실제 주유소의 알림을 남기지 않는다.
     */
    fun clear() {
        generation++
        id?.let(remove)
        id = null
        if (inside) onPlace(Place.NONE)
        inside = false
    }

    /** 실제 선택한 주유소 안에 있는 동안 고정 장소 EXIT가 이를 덮지 못하게 한다. */
    val isInside: Boolean get() = inside
}
