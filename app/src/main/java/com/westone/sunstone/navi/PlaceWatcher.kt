package com.westone.sunstone.navi

import android.content.Context
import android.util.Log
import com.westone.sunstone.routines.model.Place
import org.json.JSONObject

/**
 * 작성: 2026-11-11 12:04:00 · 박건준
 * 클래스: PlaceWatcher
 * 목적: 시연 장소(`assets/places.json`)마다 근접 알림을 걸어, 들어가면 그
 *       장소를, 나가면 장소 없음을 알린다. 데모 `place` 없이 위치만으로
 *       장소가 바뀌게 한다.
 * 인자: context — 앱 Context. assets를 읽는다
 *       proximity — 근접 알림 창구
 *       onPlace — 장소가 바뀔 때 부를 함수(지금 장소)
 * 근거: 측정 — FusedLocation 근접 알림이 `geo fix`를 따라 ENTER·EXIT를
 *       줬다(docs/api-spike.md 근접 알림 행)
 * 미구현: 프로토타입이라 바깥 API(장소 검색) 대신 고정 목록을 쓴다. 바깥 API를
 *       붙이면 경로 둘레 주유소·세차장을 찾아 같은 방식으로 알림을 건다
 */
class PlaceWatcher(
    private val context: Context,
    private val proximity: Proximity,
    private val onPlace: (Place) -> Unit,
) {

    private companion object {
        /**
         * 작성: 2026-11-11 12:04:00 · 박건준
         * 변수: TAG
         * 목적: 위치 관련 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-11 12:04:00 · 박건준
         * 변수: FILE
         * 목적: 고정 장소 목록 파일(assets).
         */
        const val FILE = "places.json"
    }

    /**
     * 작성: 2026-11-11 12:04:00 · 박건준
     * 클래스: Spot
     * 목적: 고정 장소 하나.
     * 인자: id — 목록 안 이름(데모 명령에서 부른다)
     *       hit — 이름·좌표
     *       place — 루틴이 보는 장소. 맞는 것이 없으면(집) NONE
     */
    data class Spot(val id: String, val hit: PlaceHit, val place: Place)

    /** 고정 장소 목록과 반경(m). 처음 쓸 때 assets에서 읽는다 */
    private val loaded: Pair<List<Spot>, Float> by lazy(::load)

    /** 고정 장소 목록 */
    val spots: List<Spot> get() = loaded.first

    /** 지금 들어가 있는 장소. 없으면 null */
    @Volatile
    private var inside: Spot? = null

    /**
     * 작성: 2026-11-11 12:04:00 · 박건준
     * 함수: start
     * 목적: 장소마다 근접 알림을 건다. 들어가면 그 장소, 그 장소에서 나가면
     *       장소 없음을 `onPlace`로 알린다.
     * 반환: 알림을 건 장소 수
     */
    suspend fun start(): Int {
        val (list, radius) = loaded // 장소 목록, 반경(m)
        var count = 0 // 알림을 건 장소 수
        list.forEach { spot ->
            val id = proximity.add( // 알림 id, 실패하면 null
                spot.hit.point, radius,
                onEnter = { enter(spot) },
                onExit = { exit(spot) },
            )
            Log.i(TAG, "장소 알림,${spot.id},${spot.place},${id ?: "실패"}")
            if (id != null) count++
        }
        return count
    }

    /**
     * 작성: 2026-11-11 12:04:00 · 박건준
     * 함수: find
     * 목적: 목록 안 이름으로 장소를 찾는다.
     * 인자: id — 목록 안 이름(`gas`, `kids`, `home`)
     * 반환: 장소. 없으면 null
     */
    fun find(id: String): Spot? = spots.firstOrNull { it.id == id }

    /**
     * 작성: 2026-11-11 12:04:00 · 박건준
     * 함수: enter
     * 목적: 장소에 들어갔을 때. 그 장소를 지금 장소로 알린다.
     */
    private fun enter(spot: Spot) {
        inside = spot
        Log.i(TAG, "장소 도착,${spot.id},${spot.place}")
        onPlace(spot.place)
    }

    /**
     * 작성: 2026-11-11 12:04:00 · 박건준
     * 함수: exit
     * 목적: 장소에서 나왔을 때. 지금 들어가 있는 장소에서 나온 것이면 장소
     *       없음을 알린다. 다른 장소의 나감은 무시한다.
     */
    private fun exit(spot: Spot) {
        Log.i(TAG, "장소 나감,${spot.id}")
        if (inside != spot) return
        inside = null
        onPlace(Place.NONE)
    }

    /**
     * 작성: 2026-11-11 12:04:00 · 박건준
     * 함수: load
     * 목적: assets의 고정 장소 목록을 읽는다. 모르는 장소 이름은 NONE으로
     *       본다.
     * 반환: (장소 목록, 반경 m)
     */
    private fun load(): Pair<List<Spot>, Float> {
        val json = JSONObject(context.assets.open(FILE).bufferedReader().use { it.readText() }) // 파일 전체
        val array = json.getJSONArray("places") // 장소 배열
        val list = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i) // 장소 하나
            val place = Place.entries.firstOrNull { it.name == o.getString("place") } ?: Place.NONE // 루틴 장소
            val point = GeoPoint(o.getDouble("lat"), o.getDouble("lng")) // 좌표
            Spot(o.getString("id"), PlaceHit(o.getString("name"), point), place)
        }
        return list to json.getDouble("radius_m").toFloat()
    }
}
