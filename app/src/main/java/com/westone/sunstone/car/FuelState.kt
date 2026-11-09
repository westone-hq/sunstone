package com.westone.sunstone.car

/**
 * 작성: 2026-11-09 20:18:22 · 박건준
 * 클래스: FuelState
 * 목적: 연료·배터리·주행 가능 거리 값 묶음. 루틴의 차 상태(`CarState`)에는
 *       연료 칸이 없어서 앱 연결 안에만 둔다. 어느 경로(Pleos SDK, AAOS 속성)가
 *       값을 주는지 실측하는 단계라 경로별로 따로 담는다. 받지 못한 값은 null.
 * 인자: fuelPercent — SDK `getFuelPercent` 연료(%)
 *       batteryPercent — SDK `getEvBatteryStateOfCharge` 배터리 충전량(%)
 *       economy — SDK `getCurrentFuelEconomy` 연비(단위 미확인)
 *       range — SDK `getRangeRemainingDistance` 주행 가능 거리(단위 미확인)
 *       aaosRangeM — AAOS `RANGE_REMAINING` 주행 가능 거리(m)
 *       aaosFuelMl — AAOS `FUEL_LEVEL` 남은 연료(ml)
 *       aaosBatteryWh — AAOS `EV_BATTERY_LEVEL` 남은 배터리(Wh)
 *       fuelCapacityMl — AAOS `INFO_FUEL_CAPACITY` 탱크 용량(ml)
 *       batteryCapacityWh — AAOS `INFO_EV_BATTERY_CAPACITY` 배터리 용량(Wh)
 *       overridePercent — 데모 `fuel N`이 덮어쓴 연료(%). null이면 덮어쓰지
 *       않음
 * 근거: 표준 — AAOS `VehiclePropertyIds` 단위(m, ml, Wh)
 */
data class FuelState(
    val fuelPercent: Int? = null,
    val batteryPercent: Int? = null,
    val economy: Float? = null,
    val range: Float? = null,
    val aaosRangeM: Float? = null,
    val aaosFuelMl: Float? = null,
    val aaosBatteryWh: Float? = null,
    val fuelCapacityMl: Float? = null,
    val batteryCapacityWh: Float? = null,
    val overridePercent: Int? = null,
) {
    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: percent
     * 목적: 화면과 루틴 판단에 쓸 연료(%). 데모 덮어쓰기가 있으면 그 값,
     *       없으면 SDK 연료 값.
     * 반환: 연료(%). 둘 다 없으면 null
     * 미확인: 에뮬레이터는 전기차라 SDK 연료 값이 오지 않을 수 있다. 그때
     *       배터리 충전량으로 대신할지는 정하지 않았다(docs/api-spike.md 연료 행)
     */
    val percent: Int? get() = overridePercent ?: fuelPercent
}
