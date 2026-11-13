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
 * 수정: 2026-11-12 16:10:00 · 박희정
 * 클래스: PlaceHit
 * 목적: 찾은 장소 하나. 내비 목적지·경유지로 넘긴다.
 * 인자: name — 장소 이름(내비 화면과 말에 쓴다)
 *       point — 좌표
 *       kind — 장소의 성격(집, 유치원 …). 모르면 `Place.NONE`
 */
data class PlaceHit(val name: String, val point: GeoPoint, val kind: Place = Place.NONE)

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: RouteSummary
 * 목적: 경로를 시작하거나 경유지를 바꿀 때 내비가 준 경로 요약.
 * 인자: distanceM — 거리(m)
 *       durationSec — 걸리는 시간(초)
 *       tollWon — 통행료(원). 0이면 유료 도로가 없다
 * 근거: 측정 — 통행료는 경로 시작·경유지 변경 이벤트에만 맞는 값이 온다
 *       (docs/api-spike.md 통행료 행)
 */
data class RouteSummary(val distanceM: Int, val durationSec: Int, val tollWon: Int)

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: GasStation
 * 목적: 주유소 하나.
 * 인자: place — 이름과 좌표
 *       priceWon — 휘발유 1리터 가격(원)
 *       carWash — 세차할 수 있으면 true
 * 근거: 측정 — 오피넷 상세정보 API에 세차 여부(`CAR_WASH_YN`)가 있다
 */
data class GasStation(val place: PlaceHit, val priceWon: Int, val carWash: Boolean)
