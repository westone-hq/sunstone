package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 작성: 2026-11-14 17:30:00 · 박건준
 * 클래스: OpinetStationsTest
 * 목적: 오피넷 응답을 흉내 내, 반경 검색 → 세차 여부 → 좌표 변환이 차례로
 *       이어져 가격·세차 시설 여부가 붙은 주유소가 나오는지 본다. 실제 키와
 *       가격은 쓰지 않는다.
 */
class OpinetStationsTest {

    /** 지금 위치(WGS84) */
    private val here = GeoPoint(37.4979, 127.0276)

    /**
     * 작성: 2026-11-14 17:30:00 · 박건준
     * 함수: 가격순으로 받고 세차 여부와 좌표를 붙인다
     * 목적: 지금 위치를 KATEC으로 바꿔 묻고, 싼 순서로 가격·세차 시설 여부·거리를
     *       채우며, 주유소 좌표는 WGS84로 되돌리는지 본다. 가격이 0인 곳은 뺀다.
     */
    @Test
    fun `가격순으로 받고 세차 여부와 좌표를 붙인다`() = runTest {
        val asked = mutableListOf<Map<String, String>>() // 오피넷에 보낸 값들
        val api = OpinetStations(
            key = { "unit-test-only" },
            fuelCode = { "B027" },
            convert = { p, from, _ -> if (from == "WGS84") GeoPoint(544413.0, 314213.0) else GeoPoint(37.47, 126.98) },
        ) { url, params, _ ->
            asked += params
            if (url.endsWith("aroundAll.do")) {
                JSONObject(
                    """{"RESULT":{"OIL":[
                    {"UNI_ID":"A1","OS_NM":"㈜가 주유소","PRICE":1829,"DISTANCE":4771.9,"GIS_X_COOR":310668.2,"GIS_Y_COOR":542255.0},
                    {"UNI_ID":"A2","OS_NM":"나 주유소","PRICE":1800,"DISTANCE":900.0,"GIS_X_COOR":310000.0,"GIS_Y_COOR":542000.0},
                    {"UNI_ID":"A3","OS_NM":"값 없는 곳","PRICE":0,"DISTANCE":10.0,"GIS_X_COOR":1.0,"GIS_Y_COOR":1.0}]}}""",
                )
            } else {
                val wash = if (params["id"] == "A1") "Y" else "N" // A1만 세차 시설이 있다
                JSONObject("""{"RESULT":{"OIL":[{"UNI_ID":"${params["id"]}","NEW_ADR":"주소","CAR_WASH_YN":"$wash"}]}}""")
            }
        }

        val rows = api.nearby(here)

        assertEquals(listOf("나 주유소", "가 주유소"), rows.map { it.place.name })
        assertEquals(listOf(1800, 1829), rows.map { it.priceWon })
        assertEquals(listOf(false, true), rows.map { it.carWash })
        assertTrue(rows.all { it.facilityKnown && it.place.point == GeoPoint(37.47, 126.98) })
        assertEquals(900, rows.first().distanceM)
        assertEquals("314213.0", asked.first()["x"])
        assertEquals("544413.0", asked.first()["y"])
        assertEquals("B027", asked.first()["prodcd"])
    }

    /**
     * 작성: 2026-11-14 17:30:00 · 박건준
     * 함수: 키가 없으면 부르지 않고 실패한다
     * 목적: 오피넷 키가 없으면 HTTP를 부르지 않고 예외를 내(부른 쪽이 카카오
     *       검색으로 넘어가게) 지어낸 가격을 만들지 않는지 본다.
     */
    @Test
    fun `키가 없으면 부르지 않고 실패한다`() = runTest {
        var called = false // HTTP를 불렀는지
        val api = OpinetStations({ null }, { "B027" }, { p, _, _ -> p }) { _, _, _ -> called = true; JSONObject() }
        try {
            api.nearby(here)
            fail("키가 없으면 실패해야 한다")
        } catch (_: IllegalStateException) {
            assertFalse(called)
        }
    }
}
