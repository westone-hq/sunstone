package com.westone.sunstone.routines.weather

import java.time.Duration
import java.time.Instant

/**
 * 클래스: RainStatus
 * 목적: 조회 실패를 비 없음과 구분한다.
 */
enum class RainStatus { RAIN_EXPECTED, NO_RAIN_EXPECTED, UNKNOWN }

/**
 * 클래스: ForecastHour
 * 목적: 시각 직전 한 시간의 강수 예보. 양은 mm, 눈은 cm, 확률은 %다.
 */
data class ForecastHour(
    val end: Instant,
    val probability: Double?,
    val precipitation: Double?,
    val rain: Double?,
    val showers: Double?,
    val snow: Double?,
    val code: Int?,
    val temperature: Double?,
)

/**
 * 클래스: RainAssessment
 * 목적: 판단과 보류 사유를 함께 전달한다.
 */
data class RainAssessment(val status: RainStatus, val reason: String? = null)

/**
 * 클래스: RainPolicy
 * 목적: 전체 기간의 완전성을 먼저 검사한 뒤 세차 추천용 강수를 판단한다.
 * 근거: 인용 — 사용자 승인 임시 정책. 기상기관의 세차 기준이 아니다.
 */
object RainPolicy {
    /** 변수: PROBABILITY_PERCENT
     * 목적: 강수 예상 확률 기준(%).
     */
    const val PROBABILITY_PERCENT = 60.0
    /** 변수: RAIN_MM
     * 목적: 한 시간 비·소나기 합계 기준(mm).
     */
    const val RAIN_MM = 0.5

    /**
     * 함수: assess
     * 목적: 목표 기간과 겹치는 시간 구간 전체를 검사한다. 경계 시간은 보수적으로 포함한다.
     * 인자: start — 조회 기준 시각, end — 종료 시각, hours — 시간별 자료
     * 반환: 자료 누락·눈·형태 불명은 UNKNOWN.
     */
    fun assess(start: Instant, end: Instant, hours: List<ForecastHour>): RainAssessment {
        if (!end.isAfter(start)) return unknown("기간 오류")
        val selected = hours.filter { it.end > start && it.end.minusSeconds(3600) < end }
        if (selected.isEmpty() || selected.first().end.minusSeconds(3600) > start || selected.last().end < end) {
            return unknown("예보 기간 부족")
        }
        if (selected.zipWithNext().any { (a, b) -> Duration.between(a.end, b.end).seconds != 3600L }) {
            return unknown("시간 누락 또는 중복")
        }
        if (selected.any { h ->
            h.probability == null || !h.probability.isFinite() || h.probability !in 0.0..100.0 ||
                listOf(h.precipitation, h.rain, h.showers, h.snow).any { it == null || !it.isFinite() || it < 0 } ||
                h.code == null || h.code !in WEATHER_CODES
        }) return unknown("필수 예보 누락 또는 범위 오류")
        val rainExpected = selected.any { h ->
            h.rain!! + h.showers!! >= RAIN_MM ||
                (h.probability!! >= PROBABILITY_PERCENT && h.code in RAIN_CODES)
        }
        if (rainExpected) return RainAssessment(RainStatus.RAIN_EXPECTED)
        if (selected.any { it.snow!! > 0 || it.code in SNOW_CODES }) return unknown("눈 예보")
        if (selected.any { it.probability!! >= PROBABILITY_PERCENT }) return unknown("강수 형태 불명")
        return RainAssessment(RainStatus.NO_RAIN_EXPECTED)
    }

    /** 함수: unknown
     * 목적: 확인 불가 결과를 만든다.
     */
    private fun unknown(reason: String) = RainAssessment(RainStatus.UNKNOWN, reason)

    /** 변수: RAIN_CODES, SNOW_CODES, WEATHER_CODES
     * 목적: Open-Meteo WMO 코드의 비·눈·유효값 목록.
     */
    private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
    private val WEATHER_CODES = setOf(0, 1, 2, 3, 45, 48) + RAIN_CODES + SNOW_CODES
}
