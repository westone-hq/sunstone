package com.westone.sunstone.routines.weather

import com.westone.sunstone.routines.model.RainOutlook
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: outlook
     * 목적: 오늘(지금부터 자정까지)부터 [days]일 뒤까지 날마다 `assess()`로 보고
     *       비가 오는 가장 가까운 날을 준다. 판단할 수 없는 날(자료 누락, 눈,
     *       강수 형태 불명)은 건너뛰고 뒤의 날을 더 본다. 비를 찾지 못했는데
     *       판단할 수 없는 날이 있었으면 날씨를 모르는 것으로 본다("비 없음"이라
     *       말하지 않는다).
     * 인자: now — 지금
     *       days — 볼 기간(일). 7이면 오늘과 그 뒤 7일
     *       hours — 시간별 예보
     *       zone — 날짜를 가를 시간대
     * 반환: 비 예보. 판단할 수 없으면 null
     * 근거: 인용 — docs/SPEC.md 4장 날씨 칸 글자("오늘 비", "N일 뒤 비",
     *       "7일 동안 비 없음"), 날씨를 받지 못했을 때
     */
    fun outlook(now: Instant, days: Int, hours: List<ForecastHour>, zone: ZoneId): RainOutlook? {
        val today = now.atZone(zone).toLocalDate() // 오늘 날짜
        var unsure = false // 판단할 수 없는 날이 있었는지
        for (day in 0..days) {
            val start = if (day == 0) now else today.plusDays(day.toLong()).atStartOfDay(zone).toInstant() // 그날 시작
            val end = today.plusDays(day + 1L).atStartOfDay(zone).toInstant() // 그날 끝(다음 날 0시)
            when (assess(start, end, hours).status) {
                RainStatus.RAIN_EXPECTED -> return RainOutlook(day)
                RainStatus.UNKNOWN -> unsure = true
                RainStatus.NO_RAIN_EXPECTED -> Unit
            }
        }
        return if (unsure) null else RainOutlook(null)
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
