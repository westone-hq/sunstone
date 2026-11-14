package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** 클래스: OpenMeteoTest
 * 목적: 서버 응답 경계·캐시·통신 실패·좌표 전달을 실제 JSON 파서로 검증한다.
 */
class OpenMeteoTest {
    @Test fun `실제 서버 지역 검색과 주유소 예보 선택 실행`() = kotlinx.coroutines.runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("SUNSTONE_LIVE_WEATHER") == "1")
        val api = OpenMeteo()
        for (name in listOf("서울", "부산", "전주", "제주")) {
            assertTrue("지역 검색 실패: $name", api.search(name).isNotEmpty())
        }
        val forecast = api.forecast(GeoPoint(37.4852, 127.0407))
        assertEquals(216, forecast.hours.size)
        val now = Instant.now()
        val selected = forecast.hours.filter { it.end > now && it.end.minusSeconds(3600) < now.plusSeconds(604800) }
        assertTrue(selected.last().end >= now.plusSeconds(604800))
        assertTrue(selected.all { it.probability != null && it.rain != null && it.showers != null && it.snow != null })
    }
    /** 함수: response
     * 목적: 현재부터 9일 예보와 단위를 재현한다.
     */
    private fun response(): JSONObject {
        val hourly = JSONObject()
        val units = JSONObject()
        val start = LocalDateTime.now(OpenMeteo.ZONE).toLocalDate().atStartOfDay()
        hourly.put("time", JSONArray((0..215).map { start.plusHours(it.toLong()).toString() }))
        mapOf("precipitation_probability" to "%", "precipitation" to "mm", "rain" to "mm",
            "showers" to "mm", "snowfall" to "cm", "weather_code" to "wmo code", "temperature_2m" to "°C")
            .forEach { (name, unit) ->
                units.put(name, unit)
                hourly.put(name, JSONArray((0..215).map { 0 }))
            }
        return JSONObject().put("timezone", "Asia/Seoul").put("hourly", hourly).put("hourly_units", units)
    }

    @Test fun `주유소 좌표 전달과 같은 위치 캐시`() = runTest {
        var count = 0
        var url = ""
        val api = OpenMeteo { request -> count++; url = request; response() }
        val point = GeoPoint(37.4852, 127.0407)
        assertEquals(216, api.forecast(point).hours.size)
        api.forecast(point)
        assertEquals(1, count)
        assertTrue(url.contains("latitude=37.4852&longitude=127.0407"))
        assertTrue(url.contains("forecast_days=9"))
    }

    @Test fun `통신 실패는 비 없음으로 바뀌지 않음`() = runTest {
        val info = LiveInfo(OpenMeteo { throw java.io.IOException("오프라인 재현") })
        assertNull(info.rainAt(GeoPoint(37.4852, 127.0407), 7))
        assertNull(info.rainWithin(7))
    }

    @Test fun `전체 응답 제한은 확인 불가`() = runTest {
        val info = LiveInfo(OpenMeteo { kotlinx.coroutines.delay(WeatherRequests.BUDGET_MS + 1_000); response() })
        assertNull(info.rainAt(GeoPoint(37.4852, 127.0407), 7))
    }

    @Test fun `일시적인 연결 끊김은 한번 재시도해 실제 응답 사용`() = runTest {
        var calls = 0
        val api = OpenMeteo {
            if (++calls == 1) throw java.net.SocketException("Connection reset")
            response()
        }
        assertEquals(false, LiveInfo(api).rainAt(GeoPoint(37.0, 127.0), 7))
        assertEquals(2, calls)
    }

    @Test fun `반복 시간 초과는 두번까지만 시도하고 확인 불가`() = runTest {
        var calls = 0
        val info = LiveInfo(OpenMeteo { calls++; throw java.net.SocketTimeoutException("Read timed out") })
        assertNull(info.rainAt(GeoPoint(37.0, 127.0), 7))
        assertEquals(2, calls)
    }

    @Test fun `응답 오류 인증서 오류 취소는 재시도하지 않음`() = runTest {
        for (failure in listOf(IllegalArgumentException("HTTP 429"),
            javax.net.ssl.SSLHandshakeException("certificate"),
            javax.net.ssl.SSLPeerUnverifiedException("hostname"),
            kotlinx.coroutines.CancellationException("cancelled"))) {
            var calls = 0
            try {
                WeatherRequests.run<Unit> { calls++; throw failure }
                fail("예외가 전달되어야 함")
            } catch (caught: Exception) {
                assertSame(failure, caught)
            }
            assertEquals(1, calls)
        }
    }

    @Test fun `배열 길이 단위 시간대 오류는 확인 불가`() = runTest {
        listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("hourly").put("rain", JSONArray()) },
            { it.getJSONObject("hourly_units").put("rain", "inch") },
            { it.put("timezone", "UTC") },
            { it.getJSONObject("hourly").getJSONArray("time").put(2, "2020-01-01T00:00") },
        ).forEach { mutate ->
            val data = response().also(mutate)
            assertNull(LiveInfo(OpenMeteo { data }).rainAt(GeoPoint(37.0, 127.0), 7))
        }
    }

    @Test fun `필수 JSON null과 불완전 기간은 확인 불가`() = runTest {
        val data = response()
        val index = java.time.ZonedDateTime.now(OpenMeteo.ZONE).hour + 2
        data.getJSONObject("hourly").getJSONArray("precipitation_probability").put(index, JSONObject.NULL)
        assertNull(LiveInfo(OpenMeteo { data }).rainAt(GeoPoint(37.0, 127.0), 7))
        val short = response()
        short.getJSONObject("hourly").keys().forEach { key ->
            val array = short.getJSONObject("hourly").getJSONArray(key)
            while (array.length() > 120) array.remove(array.length() - 1)
        }
        assertNull(LiveInfo(OpenMeteo { short }).rainAt(GeoPoint(37.0, 127.0), 7))
    }

    @Test fun `지역 검색은 좌표를 응답에서 확보`() = runTest {
        var url = ""
        val data = JSONObject("""{"results":[{"name":"전주시","country_code":"KR","admin1":"전북특별자치도","latitude":35.8,"longitude":127.1}]}""")
        val result = OpenMeteo { url = it; data }.search("전주")
        assertEquals(GeoPoint(35.8, 127.1), result.single().point)
        assertTrue(url.contains("countryCode=KR"))
    }

    @Test fun `잘못된 좌표는 HTTP를 호출하지 않음`() = runTest {
        var called = false
        val info = LiveInfo(OpenMeteo { called = true; response() })
        assertNull(info.rainAt(GeoPoint(Double.NaN, 127.0), 7))
        assertFalse(called)
    }
}
