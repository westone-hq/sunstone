package com.westone.sunstone.demo

import android.util.Log
import com.westone.sunstone.info.FixedInfo
import com.westone.sunstone.routines.port.InfoPort

/** 클래스: DemoWeather
 * 목적: debug 명령에서만 주입하는 재현 예보다. 실제 HTTP 실패를 가짜 성공으로 바꾸지 않는다.
 * 미구현: 프로토타입 시연용 고정값이다. 일반 날씨 질문은 계속 실제 API를 사용한다.
 */
class DemoWeather(private val mode: String) : InfoPort {
    override val weatherSource = "테스트 날씨: $mode · 실제 예보 아님"
    /** 함수: gasStations
     * 목적: 기존 시연 주유소를 유지한다.
     */
    override suspend fun gasStations() = FixedInfo().gasStations()

    /** 함수: rainWithin
     * 목적: 테스트 상태를 로그에도 밝힌다.
     */
    override suspend fun rainWithin(days: Int): Boolean? {
        Log.i("SunstoneDemo", "테스트 날씨: $mode, 실제 예보 아님")
        return when (mode) { "dry" -> false; "rain" -> true; else -> null }
    }
}
