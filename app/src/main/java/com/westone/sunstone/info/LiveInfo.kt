package com.westone.sunstone.info

import android.util.Log
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.port.InfoPort
import com.westone.sunstone.routines.weather.RainPolicy
import com.westone.sunstone.routines.weather.RainStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * 클래스: LiveInfo
 * 목적: 주유소·날씨를 실제 제공자로 조회한다. 실패 때 고정값으로 대체하지 않는다.
 */
class LiveInfo(
    private val weather: OpenMeteo,
    private val stations: suspend () -> List<com.westone.sunstone.routines.model.GasStation> = { emptyList() },
) : InfoPort {
    override val weatherSource = "Open-Meteo 실제 예보"
    override val nearbyStations = true
    /** 함수: gasStations
     * 목적: 위치·인증·검색 실패 시 가상 주유소를 반환하지 않는다.
     */
    override suspend fun gasStations(): List<com.westone.sunstone.routines.model.GasStation> = try {
        stations()
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        Log.w("Sunstone", "주유소 조회 시간 초과")
        emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Log.w("Sunstone", "카카오 주변 주유소 검색 실패: 위치·키·통신 상태 확인 필요")
        emptyList()
    }

    /** 함수: rainWithin
     * 목적: 좌표 없는 구형 호출을 실제 날씨인 것처럼 처리하지 않는다.
     */
    override suspend fun rainWithin(days: Int): Boolean? = null

    /** 함수: rainAt
     * 목적: 목적지가 아닌 주유소 위치에서 요청 시점부터 기간 전체를 검사한다.
     */
    override suspend fun rainAt(point: GeoPoint, days: Int): Boolean? {
        val start = Instant.now() // 실제 요청 시각
        return try {
            require(days in 1..7) { "지원 기간 오류" }
            withTimeoutOrNull(WeatherRequests.BUDGET_MS) {
                val forecast = weather.forecast(point) // → 로직 이동: OpenMeteo.forecast()
                val result = RainPolicy.assess(start, start.plusSeconds(days * 86400L), forecast.hours)
                Log.i("Sunstone", "세차 예보: Open-Meteo ${result.status} ${result.reason.orEmpty()}")
                when (result.status) {
                    RainStatus.RAIN_EXPECTED -> true
                    RainStatus.NO_RAIN_EXPECTED -> false
                    RainStatus.UNKNOWN -> null
                }
            }.also { if (it == null) Log.w("Sunstone", "세차 예보 확인 불가") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Sunstone", "세차 예보 실패: ${e.message}")
            null
        }
    }
}
