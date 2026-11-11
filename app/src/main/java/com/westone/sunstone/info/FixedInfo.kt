package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.port.InfoPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: FixedInfo
 * 목적: 바깥 정보 창구(`InfoPort`)의 고정 응답. 키·네트워크가 없어도 시나리오 ①이
 *       끝까지 돌게 한다.
 *       - 주유소 세 곳: 세차 되는 곳 중 가장 싼 "OO주유소"가 시연 주유소 좌표
 *         (assets/places.json `gas`)에 있어, 경유지로 넣으면 근접 알림으로 도착한다
 *       - 날씨: 일주일 비 소식 없음
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #2 "가격·날씨는 키 없으면 고정 응답"
 * 미구현: 프로토타입이라 오피넷 주유소·기상청 날씨 API 대신 늘 같은 값을 준다.
 *       이름·가격은 지어낸 값이다. 실제 차에서는 현재 위치 근처를 API로 찾아야 한다
 */
class FixedInfo : InfoPort {

    private companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: STATIONS
         * 목적: 고정 응답 주유소 목록. 가격은 휘발유 1리터(원).
         * 근거: 미정 — 시연 주유소 좌표만 places.json과 맞췄고 나머지는 지어냈다
         */
        val STATIONS = listOf(
            GasStation(PlaceHit("OO주유소", GeoPoint(37.4846, 127.0340)), priceWon = 1650, carWash = true),
            GasStation(PlaceHit("△△주유소", GeoPoint(37.4900, 127.0300)), priceWon = 1610, carWash = false),
            GasStation(PlaceHit("□□주유소", GeoPoint(37.4800, 127.0400)), priceWon = 1690, carWash = true),
        )
    }

    override suspend fun gasStations(): List<GasStation> = STATIONS

    override suspend fun rainWithin(days: Int): Boolean = false
}
