package com.westone.sunstone.info

import android.util.Log
import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.routines.weather.RainPolicy
import com.westone.sunstone.routines.weather.RainStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * 작성: 2026-11-14 13:41:10 · 박희정
 * 수정: 2026-11-14 17:00:00 · 박건준
 * 클래스: LiveInfo
 * 목적: 바깥 정보 창구(`InfoPort`)의 실제 값. 주유소는 넘겨받은 검색으로,
 *       날씨는 Open-Meteo 시간별 예보로 받는다. 실패하면 지어낸 값으로 채우지
 *       않고 "없음"·"모름"으로 돌려준다(주유를 묻지 않거나 날씨 칸을 뺀다).
 * 인자: weather — Open-Meteo
 *       location — 지금 위치를 주는 곳. 모르면 null
 *       stations — 지금 위치 근처 주유소 검색(오피넷, 안 되면 카카오)
 * 근거: 인용 — docs/SPEC.md 4장 날씨를 받지 못했을 때
 */
class LiveInfo(
    private val weather: OpenMeteo,
    private val location: suspend () -> GeoPoint? = { null },
    private val stations: suspend () -> List<GasStation> = { emptyList() },
) : InfoPort {

    override val nearbyStations = true

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 함수: gasStations
     * 목적: 근처 주유소를 찾는다. 위치·키·통신이 안 되면 빈 목록.
     */
    override suspend fun gasStations(): List<GasStation> = try {
        stations()
    } catch (_: TimeoutCancellationException) {
        Log.w(TAG, "주유소 조회 시간 초과")
        emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "주유소 검색 실패: ${e.message}")
        emptyList()
    }

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: rain
     * 목적: 그곳의 시간별 예보를 받아 비가 오는 가장 가까운 날을 준다
     *       (`RainPolicy.outlook()`).
     * 인자: point — 볼 곳. null이면 지금 위치
     *       days — 볼 기간(일)
     * 반환: 비 예보. 위치를 모르거나 예보를 받지 못했거나 판단할 수 없으면 null
     */
    override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? = try {
        withTimeoutOrNull(WeatherRequests.BUDGET_MS) {
            val at = point ?: location() ?: return@withTimeoutOrNull null // 볼 곳
            val forecast = weather.forecast(at) // → 로직 이동: OpenMeteo.forecast()
            RainPolicy.outlook(Instant.now(), days, forecast.hours, OpenMeteo.ZONE)
        }.also { Log.i(TAG, "비 예보: ${it?.let { o -> o.day?.let { d -> "${d}일 뒤" } ?: "없음" } ?: "모름"}") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "비 예보 실패: ${e.message}")
        null
    }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 함수: rainAt
     * 목적: 그곳에 지금부터 [days]일 동안 비가 오는지 본다
     *       (`RainPolicy.assess()`). 날씨를 묻는 말과 테스트가 쓴다.
     * 인자: point — 볼 곳
     *       days — 볼 기간(일, 1~7)
     * 반환: 비가 오면 true, 안 오면 false, 판단할 수 없으면 null
     */
    suspend fun rainAt(point: GeoPoint, days: Int): Boolean? {
        val start = Instant.now() // 지금
        return try {
            require(days in 1..7) { "지원 기간 오류" }
            withTimeoutOrNull(WeatherRequests.BUDGET_MS) {
                val forecast = weather.forecast(point) // → 로직 이동: OpenMeteo.forecast()
                when (RainPolicy.assess(start, start.plusSeconds(days * 86400L), forecast.hours).status) {
                    RainStatus.RAIN_EXPECTED -> true
                    RainStatus.NO_RAIN_EXPECTED -> false
                    RainStatus.UNKNOWN -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "비 예보 실패: ${e.message}")
            null
        }
    }

    private companion object {
        /**
         * 작성: 2026-11-14 17:00:00 · 박건준
         * 변수: TAG
         * 목적: 앱 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "Sunstone"
    }
}
