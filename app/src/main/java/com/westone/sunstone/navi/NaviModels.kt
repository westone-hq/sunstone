package com.westone.sunstone.navi

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: GeoPoint
 * 목적: 위도·경도 한 점.
 * 인자: lat — 위도
 *       lng — 경도
 */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: PlaceHit
 * 목적: 찾은 장소 하나. 내비 목적지·경유지로 넘긴다.
 * 인자: name — 장소 이름(내비 화면에 보인다)
 *       point — 좌표
 *       address — 주소. 모르면 빈 글자
 *       category — "주유소"·"세차장" 같은 분류. 모르면 null
 */
data class PlaceHit(val name: String, val point: GeoPoint, val address: String = "", val category: String? = null)

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: RouteSummary
 * 목적: 경로 요약. 경로를 시작하거나 경유지를 바꿀 때 내비가 준 값이다.
 * 인자: distanceM — 거리(m)
 *       durationSec — 걸리는 시간(초)
 *       tollWon — 통행료(원)
 * 근거: 측정 — 통행료는 경로 시작·경유지 변경 이벤트에만 맞는 값이 오고,
 *       안내 중 다시 읽으면 0이다(docs/api-spike.md 통행료 행)
 */
data class RouteSummary(val distanceM: Int, val durationSec: Int, val tollWon: Int)

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: Leg
 * 목적: 한 구간의 남은 거리·시간.
 * 인자: name — 목적지나 경유지 이름
 *       distanceM — 남은 거리(m)
 *       durationSec — 남은 시간(초)
 */
data class Leg(val name: String, val distanceM: Int, val durationSec: Int)

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: Remaining
 * 목적: 지금 남은 거리. 경유지는 가까운 순서다.
 * 인자: destination — 목적지까지
 *       waypoints — 경유지마다
 */
data class Remaining(val destination: Leg, val waypoints: List<Leg>)

/**
 * 작성: 2026-11-11 12:00:00 · 박건준
 * 클래스: Arrival
 * 목적: 목적지나 경유지 도착.
 * 인자: name — 도착한 곳 이름
 *       isDestination — 목적지면 true, 경유지면 false
 */
data class Arrival(val name: String, val isDestination: Boolean)
