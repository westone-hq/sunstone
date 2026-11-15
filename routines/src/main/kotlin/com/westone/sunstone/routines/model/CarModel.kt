package com.westone.sunstone.routines.model

/** 기어. Pleos VehicleGear를 앱이 쓰는 다섯 가지로 줄인 것. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 창문. 선루프도 창문으로 다룬다(Pleos WindowValue.ROOF_TOP_1/2). */
enum class Window(val label: String) {
    FRONT_LEFT("운전석 창문"),
    FRONT_RIGHT("조수석 창문"),
    REAR_LEFT("뒷좌석 왼쪽 창문"),
    REAR_RIGHT("뒷좌석 오른쪽 창문"),
    ROOF_1("선루프 앞"),
    ROOF_2("선루프 뒤");

    val isRear: Boolean get() = this == REAR_LEFT || this == REAR_RIGHT
}

enum class Door(val label: String) {
    FRONT_LEFT("운전석 문"),
    FRONT_RIGHT("조수석 문"),
    REAR_LEFT("뒷좌석 왼쪽 문"),
    REAR_RIGHT("뒷좌석 오른쪽 문"),
    TRUNK("트렁크");

    val isRear: Boolean get() = this == REAR_LEFT || this == REAR_RIGHT
}

enum class Seat(val label: String) {
    DRIVER("운전석"),
    PASSENGER("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_CENTER("뒷좌석 가운데"),
    REAR_RIGHT("뒷좌석 오른쪽");

    val isRear: Boolean get() = this == REAR_LEFT || this == REAR_CENTER || this == REAR_RIGHT
}

enum class WiperMode { OFF, AUTO, INTERMITTENT, CONTINUOUS, MIST, UNKNOWN }

/** 시트에서 위치를 읽고 움직일 수 있는 축. */
enum class SeatAxis(val label: String) {
    FORE_AFT("앞뒤"),
    HEIGHT("높이"),
    BACKREST_ANGLE("등받이"),
    TILT("쿠션 각도"),
    DEPTH("쿠션 길이"),
    HEADREST_HEIGHT("헤드레스트 높이"),
}

/**
 * 수정: 2026-11-12 16:10:00 · 박희정
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: Place
 * 목적: 장소의 성격. 지금 있는 곳(차 상태)과 내비 목적지(`PlaceHit.kind`)에
 *       쓴다. 차가 아니라 앱이 시연 장소 목록으로 채운다.
 *       - `HOME` — 차일드락이 "집에 가는 길"을 알아볼 때 쓴다
 * 근거: 인용 — 시연 시나리오 ② #5 "집 판단: 내비 목적지"
 */
enum class Place { CAR_WASH, GAS_STATION, KIDS, HOME, NONE }

/**
 * 루틴이 판단에 쓰는 차 상태 한 장면.
 * 차에서 읽은 값(기어·문·좌석)과 앱이 덧붙인 값(장소)을 함께 담는다.
 */
data class CarState(
    val gear: Gear = Gear.UNKNOWN,
    /** 모르면 0. 에뮬레이터는 속도를 넣을 수 없어서 데모에서 덮어쓴다. */
    val speedKmh: Float = 0f,
    val doorOpen: Map<Door, Boolean> = emptyMap(),
    val seatOccupied: Map<Seat, Boolean> = emptyMap(),
    val beltBuckled: Map<Seat, Boolean> = emptyMap(),
    val place: Place = Place.NONE,
    /** 연료(0~100%). 모르면 null. 에뮬레이터 차는 전기차라 데모 `fuel`로 넣는다 */
    val fuelPercent: Int? = null,
    /** 주행 가능 거리(km). 모르면 null. 목적지까지 남은 거리와 견준다(docs/SPEC.md 4장) */
    val rangeKm: Int? = null,
) {
    val stopped: Boolean get() = speedKmh < 1f
    fun isOpen(door: Door): Boolean = doorOpen[door] == true
    fun occupied(seat: Seat): Boolean = seatOccupied[seat] == true
    fun buckled(seat: Seat): Boolean = beltBuckled[seat] == true
    val rearOccupied: Boolean get() = Seat.entries.any { it.isRear && occupied(it) }
    val anyRearDoorOpen: Boolean get() = Door.entries.any { it.isRear && isOpen(it) }
}
