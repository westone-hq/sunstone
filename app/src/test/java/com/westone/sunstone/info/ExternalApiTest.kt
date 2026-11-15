package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.navi.SelectedStation
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 클래스: ExternalApiTest
 * 목적: 인증 제공자의 응답을 재현하되 실제 키·가격으로 오해하지 않게 테스트에서만 사용한다.
 */
class ExternalApiTest {
    private val origin = GeoPoint(37.4852, 127.0407)

    @Test fun `카카오 복수 후보 이름 주소와 좌표`() = runTest {
        val api = KakaoPlaces({ "unit-test-only" }) { path, params, _ ->
            assertTrue(path.endsWith("search/keyword.json"))
            assertEquals("강남역", params["query"])
            JSONObject("""{"documents":[
                {"place_name":"강남역","road_address_name":"주소 A","x":"127.0","y":"37.5"},
                {"place_name":"강남역 출구","address_name":"주소 B","x":"127.1","y":"37.6"}]}""")
        }
        val rows = api.search("강남역")
        assertEquals(2, rows.size)
        assertEquals("주소 A", rows[0].address)
        assertEquals(GeoPoint(37.6, 127.1), rows[1].point)
    }

    @Test fun `키 없으면 카카오 HTTP 미호출`() = runTest {
        var called = false
        try {
            KakaoPlaces({ null }) { _, _, _ -> called = true; JSONObject() }.search("강남역")
            fail("키 없음은 실패여야 한다")
        } catch (_: IllegalStateException) { assertFalse(called) }
    }

    @Test fun `카카오 가까운 주유소 검색 가격 시설 추정 금지`() = runTest {
        var count = 0
        val api = KakaoPlaces({ "unit-test-only" }) { path, params, _ ->
            count++
            assertTrue(path.endsWith("search/category.json"))
            assertEquals("OL7", params["category_group_code"])
            assertEquals(origin.lng.toString(), params["x"])
            assertEquals(origin.lat.toString(), params["y"])
            assertEquals("distance", params["sort"])
            JSONObject("""{"documents":[
                {"place_name":"멀리 주유소","x":"127.04","y":"37.48","distance":"400","address_name":"주소 A"},
                {"place_name":"가까운 주유소","x":"127.05","y":"37.49","distance":"150","address_name":"주소 B"},
                {"place_name":"LPG 충전소","x":"127.05","y":"37.49","distance":"100"}]}""")
        }
        val rows = api.nearbyStations(origin)
        assertEquals(2, rows.size)
        assertEquals("가까운 주유소", rows.first().place.name)
        assertEquals(150, rows.first().distanceM)
        assertTrue(rows.all { it.priceWon == null && !it.facilityKnown && !it.carWash })
        api.nearbyStations(origin)
        assertEquals(1, count)
    }

    @Test fun `카카오 주유소 검색 실패는 가상 결과 없음`() = runTest {
        val api = KakaoPlaces({ null })
        val info = LiveInfo(OpenMeteo()) { api.nearbyStations(origin) }
        assertTrue(info.gasStations().isEmpty())
        val slow = LiveInfo(OpenMeteo()) {
            kotlinx.coroutines.withTimeout(10) { kotlinx.coroutines.delay(20); emptyList() }
        }
        assertTrue(slow.gasStations().isEmpty())
    }

    @Test fun `선택 주유소 좌표 등록 교체 이전 콜백 폐기`() = runTest {
        val registered = mutableListOf<PlaceHit>()
        val callbacks = mutableListOf<Pair<() -> Unit, () -> Unit>>()
        val removed = mutableListOf<String>()
        val states = mutableListOf<Place>()
        val watcher = SelectedStation({ place, enter, exit ->
            registered += place
            callbacks += enter to exit
            "id-${registered.size}"
        }, { removed += it }, { states += it })
        val first = PlaceHit("테스트 1", origin)
        val next = PlaceHit("테스트 2", GeoPoint(35.0, 129.0))
        watcher.watch(first)
        callbacks[0].first()
        assertEquals(Place.GAS_STATION, states.last())
        watcher.watch(next)
        assertEquals(listOf("id-1"), removed)
        callbacks[1].first()
        callbacks[0].second()
        assertTrue(watcher.isInside)
        callbacks[1].second()
        assertEquals(Place.NONE, states.last())
        assertEquals(next.point, registered.last().point)
        watcher.clear()
        assertEquals(listOf("id-1", "id-2"), removed)
    }

    @Test fun `도착 등록 실패의 늦은 콜백 무시`() = runTest {
        var enter: (() -> Unit)? = null
        val states = mutableListOf<Place>()
        val watcher = SelectedStation({ _, callback, _ -> enter = callback; null }, {}, { states += it })
        try {
            watcher.watch(PlaceHit("실패 재현", origin))
            fail("등록 실패여야 한다")
        } catch (_: IllegalStateException) { }
        enter?.invoke()
        assertTrue(states.isEmpty())
        assertFalse(watcher.isInside)
    }
}
