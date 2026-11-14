package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.port.InfoPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-12 11:06:21 · 박희정
 * 수정: 2026-11-14 17:00:00 · 박건준
 * 클래스: FixedInfo
 * 목적: 바깥 정보 창구(`InfoPort`)의 시연 준비 값. 시연 모드가 도는 동안
 *       `AppGraph`가 실제 값(`LiveInfo`) 대신 이것을 잇는다. 키·네트워크가
 *       없어도 시나리오 ①이 끝까지 같은 말로 돌게 한다.
 *       - 주유소 세 곳: 세차 되는 곳 중 가장 싼 "말죽거리주유소"가 시연 주유소 좌표
 *         (assets/places.json `gas`)에 있어, 경유지로 넣으면 근접 알림으로 도착한다
 *       - 날씨: 일주일 비 소식 없음
 * 근거: 인용 — 시연 시나리오 ① #2 "가격·날씨는 키 없으면 고정 응답"
 * 미구현: 프로토타입이라 시연 중에는 오피넷 주유소·Open-Meteo 날씨 대신 늘 같은
 *       값을 준다(시연 날의 실제 날씨·가격에 대사가 달라지지 않게).
 *       주유소 이름·좌표는 실제(서울 양재 근처)지만 가격과 세차 가능 여부는
 *       지어낸 예시 값이다. 카드의 "세차도 되는 곳이에요"와 세차 예약도 이
 *       예시 값에서 나온다(실제 그 주유소의 정보가 아니다). 실제 차에서는 현재
 *       위치 근처를 API로 찾아야 한다
 */
class FixedInfo : InfoPort {

    private companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 수정: 2026-11-12 11:06:21 · 박희정
         * 변수: STATIONS
         * 목적: 고정 응답 주유소 목록. 가격은 휘발유 1리터(원).
         * 근거: 인용 — 이름·좌표는 OpenStreetMap 주유소 자료(2026-11-12 조회)
         *       미정 — 가격·세차 가능 여부는 지어냈다
         */
        val STATIONS = listOf(
            GasStation(PlaceHit("말죽거리주유소", GeoPoint(37.4852, 127.0407)), priceWon = 1650, carWash = true),
            GasStation(PlaceHit("서울석유양재주유소", GeoPoint(37.4781, 127.0396)), priceWon = 1610, carWash = false),
            GasStation(PlaceHit("S-OIL 중앙주유소", GeoPoint(37.4816, 127.0422)), priceWon = 1690, carWash = true),
        )
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: gasStations
     * 목적: 고정 응답 주유소 세 곳을 돌려준다.
     */
    override suspend fun gasStations(): List<GasStation> = STATIONS

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-14 17:00:00 · 박건준
     * 함수: rain
     * 목적: 시연 준비 날씨. 늘 비 소식이 없다.
     * 근거: 인용 — docs/SPEC.md 9장 시나리오 ①의 준비 값 "7일 동안 비 없음"
     */
    override suspend fun rain(point: GeoPoint?, days: Int) = RainOutlook(null)
}
