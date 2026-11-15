package com.westone.sunstone.routines.weather

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** 클래스: WeatherQuery
 * 목적: STT 없이 기존 텍스트에서 날씨 지역과 기간을 추출한다.
 */
data class WeatherQuery(val region: String?, val period: String) {
    /** 함수: range
     * 목적: 한국 날짜 기준 오늘·내일·이번 주·향후 168시간을 구분한다.
     */
    fun range(now: Instant): Pair<Instant, Instant> {
        val zone = ZoneId.of("Asia/Seoul") // 한국 날짜 경계
        val date = now.atZone(zone).toLocalDate() // 오늘
        return when (period) {
            "내일" -> date.plusDays(1).atStartOfDay(zone).toInstant() to date.plusDays(2).atStartOfDay(zone).toInstant()
            "이번 주" -> now to date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .plusDays(1).atStartOfDay(zone).toInstant()
            "7일" -> now to now.plusSeconds(168 * 3600L)
            else -> now to date.plusDays(1).atStartOfDay(zone).toInstant()
        }
    }

    companion object {
        /** 함수: parse
         * 목적: 지원하는 날씨 질문만 소비하고 길안내·세차 명령은 기존 해석기로 넘긴다.
         */
        fun parse(text: String): WeatherQuery? {
            if (Regex("세차|길\\s*안내|안내해|가자").containsMatchIn(text)) return null
            if (!Regex("날씨|비\\s*(와|오|소식|예보)|눈\\s*(와|오|예보)").containsMatchIn(text)) return null
            val period = when {
                "내일" in text -> "내일"
                Regex("이번\\s*주").containsMatchIn(text) -> "이번 주"
                Regex("일주일|7일").containsMatchIn(text) -> "7일"
                else -> "오늘"
            }
            val marker = Regex("날씨|비\\s*(?:와|오|소식|예보)|눈\\s*(?:와|오|예보)").find(text) ?: return null
            val prefix = text.substring(0, marker.range.first)
                .replace(Regex("내일|오늘|이번\\s*주|일주일|7일|향후"), "").trim() // 지역 후보
            val region = prefix.removeSuffix("의").removeSuffix("에서").trim()
                .takeUnless { it.isEmpty() || it in setOf("여기", "현재 위치", "지금", "이곳") }
            return WeatherQuery(region, period)
        }
    }
}
