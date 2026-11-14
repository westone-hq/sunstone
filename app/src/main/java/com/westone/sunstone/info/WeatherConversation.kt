package com.westone.sunstone.info

import android.util.Log
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.weather.RainPolicy
import com.westone.sunstone.routines.weather.RainStatus
import com.westone.sunstone.routines.weather.WeatherQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** 클래스: WeatherConversation
 * 목적: 텍스트 날씨 질문과 동명 지역 재확인을 기존 음성 출력에 연결한다.
 */
class WeatherConversation(
    private val weather: OpenMeteo,
    private val location: WeatherLocation,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
) {
    private var pending: Pair<WeatherQuery, List<WeatherPlace>>? = null
    private var job: Job? = null

    /** 함수: cancel
     * 목적: 새 차량 명령이 들어오면 이전 날씨 결과가 뒤늦게 끼어들지 않게 한다.
     */
    fun cancel() {
        job?.cancel()
        pending = null
    }

    /** 함수: hear
     * 목적: 날씨 질문 또는 지역 후보 응답만 소비한다. 실제 STT는 사용하지 않는다.
     */
    fun hear(text: String): Boolean {
        val query = WeatherQuery.parse(text) // 새로운 날씨 질문
        val waiting = pending // 이전 질문의 후보
        if (query == null && waiting == null) return false
        if (query == null && text in setOf("취소", "괜찮아", "그만")) {
            cancel()
            speaker.say("날씨 조회를 취소했어요.")
            return true
        }
        val chosen = if (query == null && waiting != null) {
            val index = text.removeSuffix("번").trim().toIntOrNull()?.minus(1) // 후보 번호
            waiting.second.getOrNull(index ?: -1) ?: waiting.second.singleOrNull {
                text == it.label || text == "${it.area} ${it.name}"
            }
        } else null
        if (query == null && chosen == null) return false
        job?.cancel()
        pending = null
        job = scope.launch {
            try {
                val request = query ?: waiting!!.first // 후보 선택은 원래 기간 유지
                speaker.say("${request.region ?: "현재 위치"} ${request.period} 예보를 확인할게요.")
                val completed = withTimeoutOrNull(WeatherRequests.BUDGET_MS * 2 + 3_000) {
                    val place = chosen ?: if (request.region == null) {
                        val point = location.current() // → 로직 이동: WeatherLocation.current()
                        if (point == null) {
                            speaker.say("현재 위치를 확인할 수 없어 날씨를 조회하지 못했어요.")
                            return@withTimeoutOrNull true
                        }
                        WeatherPlace("현재 위치", "", point)
                    } else {
                        val candidates = weather.search(requireNotNull(request.region)) // → 로직 이동: OpenMeteo.search()
                        if (candidates.isEmpty()) {
                            speaker.say("요청한 지역을 찾지 못했어요. 행정구역과 함께 다시 입력해 주세요.")
                            return@withTimeoutOrNull true
                        }
                        if (candidates.size > 1) {
                            pending = request to candidates
                            speaker.say("지역을 골라 번호를 입력해 주세요. " + candidates.mapIndexed { i, p ->
                                "${i + 1}번 ${p.label}"
                            }.joinToString(". "))
                            return@withTimeoutOrNull true
                        }
                        candidates.single()
                    }
                    val now = Instant.now() // 조회 기간 기준
                    val (start, end) = request.range(now) // → 로직 이동: WeatherQuery.range()
                    val snapshot = weather.forecast(place.point) // → 로직 이동: OpenMeteo.forecast()
                    val assessment = RainPolicy.assess(start, end, snapshot.hours)
                    Log.i("Sunstone", "지역 예보: Open-Meteo ${assessment.status} ${assessment.reason.orEmpty()}")
                    val hours = snapshot.hours.filter { it.end > start && it.end.minusSeconds(3600) < end }
                    val summary = when (assessment.status) {
                        RainStatus.RAIN_EXPECTED -> "비 예보가 있어요."
                        RainStatus.NO_RAIN_EXPECTED -> "기준 이상의 비 예보는 없어요."
                        RainStatus.UNKNOWN -> if (assessment.reason == "눈 예보") "눈 예보가 있어요."
                            else "강수 예보를 확실히 확인할 수 없어요."
                    }
                    val numbers = if (assessment.status != RainStatus.UNKNOWN || assessment.reason == "눈 예보") {
                        " 시간별 최대 강수확률 ${hours.maxOf { it.probability!! }.toInt()}%, " +
                            "최대 비의 양은 시간당 ${"%.1f".format(java.util.Locale.KOREA, hours.maxOf { it.rain!! + it.showers!! })}mm예요."
                    } else ""
                    speaker.say("${place.label} ${request.period}, $summary$numbers Open-Meteo 예보 기준이에요.")
                    true
                }
                if (completed == null) speaker.say("날씨 조회 시간이 초과됐어요. 잠시 후 다시 요청해 주세요.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Sunstone", "지역 날씨 조회 실패: ${e.message}")
                speaker.say("현재 날씨를 확인할 수 없어요. 잠시 후 다시 요청해 주세요.")
            }
        }
        return true
    }
}
