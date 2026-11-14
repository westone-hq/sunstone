package com.westone.sunstone.demo

import android.util.Log
import com.westone.sunstone.info.FixedInfo
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.port.InfoPort

/**
 * 작성: 2026-11-14 13:41:10 · 박희정
 * 수정: 2026-11-14 17:00:00 · 박건준
 * 클래스: DemoWeather
 * 목적: 데모 명령 `weather`가 넣는 날씨. 주유소는 시연 준비 값 그대로다.
 *       비가 오는 갈래(docs/SPEC.md 4장)를 에뮬레이터에서 손으로 볼 때 쓴다.
 * 인자: mode — "dry"(비 없음), "rain"(내일 비), "rain4"처럼 뒤에 숫자를 붙이면
 *       그 날 수 뒤 비, 그 밖은 날씨 모름
 * 미구현: 프로토타입이라 실제 예보 대신 넣는 값이다. debug 빌드에만 있다
 */
class DemoWeather(private val mode: String) : InfoPort {

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 함수: gasStations
     * 목적: 시연 준비 주유소를 그대로 돌려준다.
     */
    override suspend fun gasStations() = FixedInfo().gasStations()

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 함수: rain
     * 목적: 넣어 둔 날씨를 돌려주고 logcat에 남긴다.
     */
    override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? {
        Log.i("SunstoneDemo", "넣은 날씨: $mode(실제 예보 아님)")
        return when {
            mode == "dry" -> RainOutlook(null)
            mode.startsWith("rain") -> RainOutlook((mode.removePrefix("rain").toIntOrNull() ?: 1).takeIf { it <= days })
            else -> null
        }
    }
}
