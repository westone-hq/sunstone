package com.westone.sunstone.routines

import com.westone.sunstone.routines.weather.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** 클래스: WeatherTest
 * 목적: 실제 날씨와 무관하게 시간 경계·임계값·자료 누락을 재현한다.
 */
class WeatherTest {
    private val start = Instant.parse("2026-11-14T01:30:00Z")
    private val end = start.plusSeconds(168 * 3600L)

    /** 함수: hours
     * 목적: 경계의 일부 시간까지 덮는 169개 시간 구간을 만든다.
     */
    private fun hours() = (0..168).map {
        ForecastHour(Instant.parse("2026-11-14T02:00:00Z").plusSeconds(it * 3600L),
            10.0, 0.0, 0.0, 0.0, 0.0, 0, 12.0)
    }

    @Test fun `전체 168시간 맑음`() {
        assertEquals(RainStatus.NO_RAIN_EXPECTED, RainPolicy.assess(start, end, hours()).status)
    }
    @Test fun `확률 60 경계와 비 형태`() {
        val rows = hours().toMutableList()
        rows[5] = rows[5].copy(probability = 60.0, code = 61)
        assertEquals(RainStatus.RAIN_EXPECTED, RainPolicy.assess(start, end, rows).status)
        rows[5] = rows[5].copy(probability = 59.0)
        assertEquals(RainStatus.NO_RAIN_EXPECTED, RainPolicy.assess(start, end, rows).status)
    }
    @Test fun `비 소나기 합계 0점5 경계`() {
        val rows = hours().toMutableList()
        rows[4] = rows[4].copy(rain = 0.3, showers = 0.2, precipitation = 0.5)
        assertEquals(RainStatus.RAIN_EXPECTED, RainPolicy.assess(start, end, rows).status)
        rows[4] = rows[4].copy(rain = 0.29)
        assertEquals(RainStatus.NO_RAIN_EXPECTED, RainPolicy.assess(start, end, rows).status)
    }
    @Test fun `누락 중복 역순 종료 부족은 확인 불가`() {
        val rows = hours()
        listOf(rows.dropLast(1), rows.drop(1), rows.filterIndexed { i, _ -> i != 80 },
            rows + rows.last(), rows.reversed()).forEach {
            assertEquals(RainStatus.UNKNOWN, RainPolicy.assess(start, end, it).status)
        }
    }
    @Test fun `필수값 누락과 비정상 범위`() {
        val rows = hours().toMutableList()
        listOf(rows[0].copy(probability = null), rows[0].copy(rain = null),
            rows[0].copy(probability = 101.0), rows[0].copy(precipitation = Double.NaN),
            rows[0].copy(rain = -1.0), rows[0].copy(code = 1000)).forEach {
            rows[0] = it
            assertEquals(RainStatus.UNKNOWN, RainPolicy.assess(start, end, rows).status)
        }
    }
    @Test fun `눈과 형태 불명은 비 없음 아님`() {
        val rows = hours().toMutableList()
        rows[1] = rows[1].copy(probability = 80.0, snow = 1.0, code = 73)
        assertEquals("눈 예보", RainPolicy.assess(start, end, rows).reason)
        rows[1] = rows[1].copy(snow = 0.0, code = 0)
        assertEquals(RainStatus.UNKNOWN, RainPolicy.assess(start, end, rows).status)
    }
    @Test fun `과거 행은 미래 판단에 포함하지 않음`() {
        val old = hours()[0].copy(end = start.minusSeconds(1800), rain = 10.0)
        assertEquals(RainStatus.NO_RAIN_EXPECTED, RainPolicy.assess(start, end, listOf(old) + hours()).status)
    }
    @Test fun `지역과 기간 추출 및 한국 날짜 경계`() {
        assertEquals("전주", WeatherQuery.parse("전주 날씨 알려줘")!!.region)
        assertEquals("부산", WeatherQuery.parse("부산 내일 비 와?")!!.region)
        assertNull(WeatherQuery.parse("여기 날씨 어때?")!!.region)
        assertNull(WeatherQuery.parse("세차할게"))
        val range = WeatherQuery.parse("내일 비 와?")!!.range(Instant.parse("2026-11-14T16:00:00Z"))
        assertEquals(Instant.parse("2026-11-15T15:00:00Z"), range.first)
        assertEquals(86400, java.time.Duration.between(range.first, range.second).seconds.toInt())
    }
}
