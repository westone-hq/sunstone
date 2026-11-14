package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.time.Instant

/**
 * 작성: 2026-11-14 13:41:10 · 박희정
 * 수정: 2026-11-14 17:00:00 · 박건준
 * 클래스: KakaoPlaces
 * 목적: 카카오 로컬 API로 목적지 후보와 지금 위치 근처 주유소를 찾고, 좌표계를
 *       바꾼다. 주유소 검색은 가격·세차 시설을 주지 않아 지어내지 않는다
 *       (`priceWon` null, `facilityKnown` false).
 * 인자: key — 카카오 REST 키를 주는 곳. 없으면 null
 *       request — HTTP GET(주소, 값, 머리말). 테스트에서 바꾼다
 * 근거: 표준 — 카카오 로컬 키워드 검색, 카테고리 검색(주유소 OL7), 좌표계
 *       변환(`geo/transcoord`, KTM이 오피넷의 KATEC이다)
 */
class KakaoPlaces(
    private val key: () -> String?,
    private val request: suspend (String, Map<String, String>, Map<String, String>) -> JSONObject = ApiHttp()::get,
) {
    private val stationLock = Mutex()
    private var cachedPoint: GeoPoint? = null
    private var cachedAt: Instant = Instant.EPOCH
    private var cachedStations = emptyList<GasStation>()

    /** 함수: search
     * 목적: 이름·주소·좌표 후보를 반환하고 사용자에게 선택받는다.
     */
    suspend fun search(query: String): List<PlaceHit> = withTimeout(8_000) {
        require(query.isNotBlank()) { "목적지 이름 필요" }
        val rows = get("search/keyword", mapOf("query" to query, "size" to "5")).getJSONArray("documents")
        (0 until rows.length()).map { index -> place(rows.getJSONObject(index)) }
            .distinctBy { it.name to it.point }
    }

    /** 함수: nearbyStations
     * 목적: 현재 위치 반경 5km의 가까운 주유소를 조회한다. 유가·세차 시설은 확인 불가다.
     */
    suspend fun nearbyStations(point: GeoPoint): List<GasStation> = withTimeout(8_000) {
        require(valid(point)) { "현재 위치 좌표 오류" }
        stationLock.withLock {
            val now = Instant.now() // 같은 위치의 반복 검색은 5분 동안 줄인다
            if (cachedPoint == point && now.epochSecond - cachedAt.epochSecond in 0..299) {
                return@withLock cachedStations
            }
            val rows = get("search/category", mapOf(
                "category_group_code" to "OL7", "x" to point.lng.toString(), "y" to point.lat.toString(),
                "radius" to "5000", "sort" to "distance", "size" to "15",
            )).getJSONArray("documents")
            val result = (0 until rows.length()).mapNotNull { index ->
                val row = rows.getJSONObject(index)
                val description = row.optString("category_name") + row.optString("place_name")
                if (listOf("LPG", "전기차", "수소").any { it in description }) return@mapNotNull null
                val distance = row.optString("distance").toIntOrNull()?.takeIf { it in 0..5000 }
                    ?: return@mapNotNull null
                GasStation(place(row).copy(kind = Place.GAS_STATION), priceWon = null, carWash = false,
                    facilityKnown = false, distanceM = distance)
            }.distinctBy { it.place.name to it.place.point }.sortedBy { it.distanceM }.take(5)
            cachedPoint = point
            cachedAt = now
            cachedStations = result
            result
        }
    }

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: convert
     * 목적: 좌표계를 바꾼다. 오피넷이 쓰는 KATEC(카카오 이름 KTM)과 WGS84
     *       사이를 오갈 때 쓴다.
     * 인자: point — 바꿀 점. WGS84가 아니면 lat에 y, lng에 x를 넣는다
     *       from — 입력 좌표계("WGS84", "KTM")
     *       to — 출력 좌표계
     * 반환: 바꾼 점(lat에 y, lng에 x)
     */
    suspend fun convert(point: GeoPoint, from: String, to: String): GeoPoint {
        val row = get(
            "geo/transcoord",
            mapOf("x" to point.lng.toString(), "y" to point.lat.toString(), "input_coord" to from, "output_coord" to to),
        ).getJSONArray("documents").getJSONObject(0) // 바꾼 좌표
        return GeoPoint(row.getDouble("y"), row.getDouble("x"))
    }

    /** 함수: place
     * 목적: 카카오 x는 경도, y는 위도다. 잘못된 좌표는 내비에 넘기지 않는다.
     */
    private fun place(row: JSONObject): PlaceHit {
        val point = GeoPoint(row.getString("y").toDouble(), row.getString("x").toDouble())
        require(valid(point)) { "카카오 좌표 오류" }
        return PlaceHit(row.getString("place_name"), point,
            address = row.optString("road_address_name").ifBlank { row.optString("address_name") },
            source = "카카오 장소 검색")
    }

    /** 함수: get
     * 목적: 키는 인증 헤더로만 보낸다. 미설정·실패 때 가짜 검색 결과를 만들지 않는다.
     */
    private suspend fun get(path: String, params: Map<String, String>): JSONObject {
        val credential = key()?.takeIf { it.isNotBlank() } ?: error("카카오 로컬 키 미설정")
        return request("https://dapi.kakao.com/v2/local/$path.json", params,
            mapOf("Authorization" to "KakaoAK $credential"))
    }

    /** 함수: valid
     * 목적: WGS84 범위 밖 좌표를 차단한다.
     */
    private fun valid(p: GeoPoint) = p.lat.isFinite() && p.lng.isFinite() &&
        p.lat in -90.0..90.0 && p.lng in -180.0..180.0
}
