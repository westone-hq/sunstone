package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * 작성: 2026-11-14 17:00:00 · 박건준
 * 클래스: OpinetStations
 * 목적: 지금 위치 근처 주유소의 가격과 세차 시설 여부를 오피넷에서 받는다.
 *       1. 지금 위치(WGS84)를 오피넷 좌표(KATEC)로 바꾼다 — 카카오 좌표 변환
 *       2. 반경 `RADIUS_M` 안 주유소를 싼 순서로 받는다 — `aroundAll.do`
 *       3. 싼 `DETAIL_COUNT`곳의 세차 시설 여부를 받는다 — `detailById.do`
 *       4. 그 주유소 좌표를 다시 WGS84로 바꾼다(내비 경유지로 넣으려고)
 * 인자: key — 오피넷 키를 주는 곳. 없으면 null
 *       fuelCode — 유종 코드를 주는 곳(휘발유 B027, 경유 D047)
 *       convert — 좌표 변환(점, 입력 좌표계, 출력 좌표계). `KakaoPlaces.convert`
 *       request — HTTP GET(주소, 값, 머리말). 테스트에서 바꾼다
 * 근거: 표준 — 오피넷 무료 API `aroundAll.do`(반경 내 주유소, x·y는 KATEC,
 *       sort 1 가격순), `detailById.do`(`CAR_WASH_YN`).
 *       측정 — 2026-11-14 강남역 근처에서 두 API 모두 값을 받았다
 *       (docs/api-spike.md 바깥 정보 표)
 * 미구현: 프로토타입이라 "경로 근처"가 아니라 지금 위치 반경에서 찾는다.
 *       세차 시설 여부는 자동 세차기가 있는지, 지금 영업하는지를 뜻하지 않는다
 */
class OpinetStations(
    private val key: () -> String?,
    private val fuelCode: () -> String,
    private val convert: suspend (GeoPoint, String, String) -> GeoPoint,
    private val request: suspend (String, Map<String, String>, Map<String, String>) -> JSONObject = ApiHttp()::get,
) {

    private companion object {
        /**
         * 작성: 2026-11-14 17:00:00 · 박건준
         * 변수: BASE, RADIUS_M, DETAIL_COUNT, WAIT_MS
         * 목적: 오피넷 API 주소, 찾을 반경(m), 세차 여부까지 받을 주유소 수,
         *       전체를 기다리는 가장 긴 시간(밀리초).
         * 근거: 표준 — 오피넷 반경 검색은 최대 5000m.
         *       미정 — 싼 곳 다섯이면 세차 되는 곳이 하나는 들어온다고 보았다.
         *       기다리는 시간은 카카오 검색(8초)에 맞췄다
         */
        const val BASE = "https://www.opinet.co.kr/api"
        const val RADIUS_M = 5000
        const val DETAIL_COUNT = 5
        const val WAIT_MS = 8_000L
    }

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: nearby
     * 목적: 클래스 설명의 1~4단계를 한다. 세차 여부와 좌표 변환은 주유소마다
     *       함께 돌린다. 한 곳이 실패하면 그곳만 뺀다.
     * 인자: point — 지금 위치(WGS84)
     * 반환: 가격과 세차 시설 여부가 붙은 주유소들, 싼 순서. 키가 없거나
     *       실패하면 예외(부른 쪽이 카카오 검색으로 넘어간다)
     */
    suspend fun nearby(point: GeoPoint): List<GasStation> = withTimeout(WAIT_MS) {
        val code = key()?.takeIf { it.isNotBlank() } ?: error("오피넷 키 미설정") // 오피넷 키
        val here = convert(point, "WGS84", "KTM") // 지금 위치의 KATEC 좌표(lat에 y, lng에 x)
        val rows = request(
            "$BASE/aroundAll.do",
            mapOf(
                "code" to code, "out" to "json", "x" to here.lng.toString(), "y" to here.lat.toString(),
                "radius" to RADIUS_M.toString(), "prodcd" to fuelCode(), "sort" to "1",
            ),
            emptyMap(),
        ).getJSONObject("RESULT").getJSONArray("OIL") // 반경 안 주유소, 싼 순서
        val cheap = (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.optInt("PRICE") > 0 }
            .sortedBy { it.optInt("PRICE") }
            .take(DETAIL_COUNT) // 세차 여부까지 받을 싼 곳들
        coroutineScope {
            cheap.map { row -> async { runCatching { station(code, row) }.getOrNull() } }.awaitAll().filterNotNull()
        }
    }

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: station
     * 목적: 주유소 하나의 세차 시설 여부를 받고 좌표를 WGS84로 바꾼다.
     * 인자: code — 오피넷 키
     *       row — 반경 검색 결과 한 줄(`UNI_ID`, `OS_NM`, `PRICE`, `DISTANCE`,
     *       `GIS_X_COOR`, `GIS_Y_COOR`)
     * 반환: 주유소
     */
    private suspend fun station(code: String, row: JSONObject): GasStation {
        val detail = request(
            "$BASE/detailById.do", mapOf("code" to code, "out" to "json", "id" to row.getString("UNI_ID")), emptyMap(),
        ).getJSONObject("RESULT").getJSONArray("OIL").getJSONObject(0) // 상세 정보
        val katec = GeoPoint(row.getDouble("GIS_Y_COOR"), row.getDouble("GIS_X_COOR")) // 주유소 KATEC 좌표
        val place = PlaceHit(
            row.getString("OS_NM").removePrefix("㈜").removePrefix("(주)").trim(),
            convert(katec, "KTM", "WGS84"),
            kind = Place.GAS_STATION,
            address = detail.optString("NEW_ADR"),
            source = "오피넷",
        ) // 내비에 넘길 장소
        return GasStation(
            place,
            priceWon = row.getInt("PRICE"),
            carWash = detail.optString("CAR_WASH_YN") == "Y",
            facilityKnown = detail.has("CAR_WASH_YN"),
            distanceM = row.optDouble("DISTANCE").takeIf { it.isFinite() }?.toInt(),
        )
    }
}
