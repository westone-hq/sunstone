package com.westone.sunstone.routines.model

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: GeoPoint
 * 목적: 위도·경도 한 점.
 * 인자: lat — 위도
 *       lng — 경도
 */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-14 13:41:10 · 박희정
 * 클래스: PlaceHit
 * 목적: 찾은 장소 하나. 내비 목적지·경유지로 넘긴다.
 * 인자: name — 장소 이름(내비 화면과 말에 쓴다)
 *       point — 좌표
 *       kind — 장소의 성격(집, 유치원 …). 모르면 `Place.NONE`
 *       address — 주소. 후보가 여럿일 때 고르는 카드에 보인다. 없으면 빈 글자
 *       source — 어디서 찾았는지("카카오 장소 검색"). 시연 장소면 null
 */
data class PlaceHit(
    val name: String,
    val point: GeoPoint,
    val kind: Place = Place.NONE,
    val address: String = "",
    val source: String? = null,
)

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-14 13:41:10 · 박희정
 * 클래스: RouteSummary
 * 목적: 경로를 시작하거나 경유지를 바꿀 때 내비가 준 경로 요약.
 * 인자: distanceM — 거리(m)
 *       durationSec — 걸리는 시간(초)
 *       tollWon — 통행료(원). 0이면 유료 도로가 없다
 *       arrivalTracked — 그 경유지에 도착하는 것을 알아챌 수 있으면 true
 * 근거: 측정 — 통행료는 경로 시작·경유지 변경 이벤트에만 맞는 값이 온다
 *       (docs/api-spike.md 통행료 행)
 */
data class RouteSummary(
    val distanceM: Int, val durationSec: Int, val tollWon: Int, val arrivalTracked: Boolean = true,
)

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-14 16:00:00 · 박건준
 * 클래스: GasStation
 * 목적: 주유소 하나.
 * 인자: place — 이름과 좌표
 *       priceWon — 1리터 가격(원). 오피넷이나 시연 값. 가격 없이 장소만
 *       찾았으면(카카오) null
 *       carWash — 세차 시설이 있으면 true. 자동 세차·영업 중인지는 모른다
 *       facilityKnown — 세차 시설이 있는지 확인했으면 true. false면 `carWash`를
 *       믿지 않는다
 *       distanceM — 지금 위치에서의 거리(m). 모르면 null
 * 근거: 측정 — 오피넷 상세정보 API에 세차 여부(`CAR_WASH_YN`)가 있다
 *       (docs/api-spike.md 바깥 정보 표)
 */
data class GasStation(
    val place: PlaceHit,
    val priceWon: Int?,
    val carWash: Boolean,
    val facilityKnown: Boolean = true,
    val distanceM: Int? = null,
)

/**
 * 작성: 2026-11-14 16:00:00 · 박건준
 * 클래스: RainOutlook
 * 목적: 앞으로 며칠 동안의 비 예보를 한 값으로. 날씨를 받지 못했으면 이 값
 *       자체가 없다(null).
 * 인자: day — 비가 오는 가장 가까운 날(오늘 0, 내일 1 …). 본 기간 안에 비가
 *       없으면 null
 * 근거: 인용 — docs/SPEC.md 4장 날씨 칸 글자("오늘 비", "N일 뒤 비",
 *       "7일 동안 비 없음")
 */
data class RainOutlook(val day: Int?)
