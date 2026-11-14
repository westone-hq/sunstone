package com.westone.sunstone.info

import android.util.Log
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.weather.ForecastHour
import com.westone.sunstone.routines.weather.RainPolicy
import com.westone.sunstone.routines.weather.RainStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * 클래스: WeatherPlace
 * 목적: 지명 후보와 행정구역을 함께 보관한다.
 */
data class WeatherPlace(val name: String, val area: String, val point: GeoPoint) {
    val label: String get() = listOf(name, area).filter { it.isNotBlank() }.distinct().joinToString(" · ")
}

/**
 * 클래스: ForecastSnapshot
 * 목적: 원본 예보와 조회 시각을 보관한다. 판단 기간은 매 요청마다 다시 계산한다.
 */
data class ForecastSnapshot(val fetched: Instant, val hours: List<ForecastHour>)

/**
 * 클래스: OpenMeteo
 * 목적: 공개 Forecast·Geocoding API를 UI 스레드 밖에서 호출한다.
 * 근거: 표준 — https://open-meteo.com/en/docs 및 /en/docs/geocoding-api
 */
class OpenMeteo(private val request: (suspend (String) -> JSONObject)? = null) {
    private val cache = ConcurrentHashMap<GeoPoint, ForecastSnapshot>()
    private val locks = ConcurrentHashMap<GeoPoint, Mutex>()

    /** 변수: ZONE, CACHE_SECONDS, CONNECT_MS, READ_MS
     * 목적: 한국 시간대와 캐시·요청 제한. 무료 API에 반복 요청하지 않는다.
     */
    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        const val CACHE_SECONDS = 600L
        const val CONNECT_MS = 3_000
        const val READ_MS = 4_000
    }

    /** 함수: forecast
     * 목적: 같은 위치의 동시 요청을 합치고 9일 원본 자료를 검증한다.
     */
    suspend fun forecast(point: GeoPoint): ForecastSnapshot = locks.getOrPut(point) { Mutex() }.withLock {
        require(valid(point)) { "좌표 오류" }
        val now = Instant.now() // 캐시 나이를 확인할 시각
        cache[point]?.takeIf { now >= it.fetched && now.epochSecond - it.fetched.epochSecond < CACHE_SECONDS }
            ?.let { return@withLock it }
        val json = get("https://api.open-meteo.com/v1/forecast?latitude=${point.lat}&longitude=${point.lng}" +
            "&hourly=precipitation_probability,precipitation,rain,showers,snowfall,weather_code,temperature_2m" +
            "&forecast_days=9&timezone=Asia%2FSeoul")
        require(json.getString("timezone") == "Asia/Seoul") { "시간대 오류" }
        val hourly = json.getJSONObject("hourly") // 시간 배열과 같은 순서의 변수 배열
        val units = json.getJSONObject("hourly_units") // 단위를 바꾼 응답은 판단하지 않는다
        val expected = mapOf("precipitation_probability" to "%", "precipitation" to "mm", "rain" to "mm",
            "showers" to "mm", "snowfall" to "cm", "weather_code" to "wmo code", "temperature_2m" to "°C")
        val time = hourly.getJSONArray("time") // ISO 현지 시각
        expected.forEach { (key, unit) ->
            require(units.getString(key) == unit && hourly.getJSONArray(key).length() == time.length()) {
                "예보 단위 또는 배열 길이 오류: $key"
            }
        }
        val hours = (0 until time.length()).map { i ->
            val end = LocalDateTime.parse(time.getString(i)).atZone(ZONE).toInstant() // 직전 한 시간 종료
            ForecastHour(end, number(hourly, "precipitation_probability", i), number(hourly, "precipitation", i),
                number(hourly, "rain", i), number(hourly, "showers", i), number(hourly, "snowfall", i),
                number(hourly, "weather_code", i)?.takeIf { it % 1.0 == 0.0 }?.toInt(),
                number(hourly, "temperature_2m", i))
        }
        require(hours.zipWithNext().all { (a, b) -> b.end.epochSecond - a.end.epochSecond == 3600L }) {
            "예보 시간 누락 또는 중복"
        }
        val snapshot = ForecastSnapshot(now, hours) // 통신 실패 때 이전 캐시로 대체하지 않는다
        if (cache.size >= 32) cache.clear()
        cache[point] = snapshot
        Log.i("Sunstone", "날씨 조회 성공: Open-Meteo ${point.lat},${point.lng}")
        snapshot
    }

    /** 함수: search
     * 목적: 한국 후보를 검색한다. 검색 결과가 여러 개면 호출자가 재확인한다.
     */
    suspend fun search(name: String): List<WeatherPlace> {
        val normalized = mapOf("서울" to "서울특별시", "부산" to "부산광역시", "전주" to "전주시",
            "제주" to "제주시", "대구" to "대구광역시", "인천" to "인천광역시",
            "대전" to "대전광역시", "울산" to "울산광역시", "세종" to "세종특별자치시")[name] ?: name
        val encoded = URLEncoder.encode(normalized, "UTF-8") // 행정구역 명칭만 보완, 좌표는 API에서 확보
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=ko&countryCode=KR")
        val results = json.optJSONArray("results") ?: return emptyList() // 결과 없음은 정상 응답
        return (0 until results.length()).mapNotNull { i ->
            val row = results.getJSONObject(i) // 지역 한 곳
            if (row.optString("country_code") != "KR") return@mapNotNull null
            val point = GeoPoint(row.getDouble("latitude"), row.getDouble("longitude")) // WGS84
            if (!valid(point)) return@mapNotNull null
            WeatherPlace(row.getString("name"), (1..4).map { row.optString("admin$it") }
                .filter { it.isNotBlank() }.distinct().joinToString(" "), point)
        }.distinctBy { it.point }
    }

    /** 함수: get
     * 목적: 제한 시간 내 HTTPS JSON만 읽고 연결을 닫는다. 취소도 전파한다.
     */
    private suspend fun get(url: String): JSONObject = WeatherRequests.run {
        request?.let { return@run it(url) }
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection // TLS는 플랫폼 검증 사용
            val started = System.nanoTime()
            try {
                connection.connectTimeout = CONNECT_MS
                connection.readTimeout = READ_MS
                connection.instanceFollowRedirects = false
                require(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } // JSON 본문
                JSONObject(text).also {
                    require(!it.optBoolean("error")) { "API 오류" }
                    Log.i("Sunstone", "날씨 HTTP 성공: ${URI(url).host} ${(System.nanoTime() - started) / 1_000_000}ms")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /** 함수: number
     * 목적: JSON null이나 문자열을 0으로 바꾸지 않는다.
     */
    private fun number(json: JSONObject, key: String, index: Int): Double? =
        (json.getJSONArray(key).opt(index) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    /** 함수: valid
     * 목적: 임의의 대체 좌표 없이 유효한 WGS84만 사용한다.
     */
    private fun valid(point: GeoPoint) = point.lat.isFinite() && point.lng.isFinite() &&
        point.lat in -90.0..90.0 && point.lng in -180.0..180.0
}
