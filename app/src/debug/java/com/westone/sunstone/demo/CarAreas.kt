package com.westone.sunstone.demo

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.Area
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.AreaValue
import android.util.Log
import com.westone.sunstone.car.awaitCallback

/**
 * 작성: 2026-11-09 18:34:38 · 박건준
 * 클래스: CarAreas
 * 목적: 차가 실제로 받는 area(좌석·문·창문 위치 묶음) 목록을 SDK의
 *       `check…Capabilities`로 읽어 logcat에 한 줄씩 남긴다. probe에서
 *       `area … not supported`로 실패한 쓰기를 어느 area로 불러야 하는지
 *       찾는 용도라 debug 빌드에만 있다.
 * 인자: vehicle — 초기화된 Pleos `Vehicle`. 루틴이 쓰는 것과 따로 만든다
 */
internal class CarAreas(private val vehicle: Vehicle) {

    /**
     * 작성: 2026-11-09 18:34:38 · 박건준
     * 함수: run
     * 목적: 대상 API마다 지원 area와 읽기·쓰기 권한을
     *       `areas,API,권한,area목록` 형식으로 logcat(`SunstoneCar`)에 남긴다.
     *       한 API가 실패해도 다음 API로 넘어간다.
     * 반환: 확인한 API 수
     */
    suspend fun run(): Int {
        val checks = checks() // 확인할 API 이름과 부르는 방법
        checks.forEach { (api, call) ->
            val line = runCatching { awaitCallback(block = call) } // 권한과 area 목록, 실패하면 예외
                .getOrElse { "실패,${it.message ?: it.javaClass.simpleName}" }
            Log.i(TAG, "areas,$api,$line")
        }
        Log.i(TAG, "areas 끝: ${checks.size}개")
        return checks.size
    }

    /**
     * 작성: 2026-11-09 18:34:38 · 박건준
     * 함수: checks
     * 목적: 확인할 API 목록. probe에서 실패한 쓰기(내기 순환, 문 잠금,
     *       좌석 공조 전원, 뒷유리 블라인드, 앞좌석 창문 잠금)를 먼저 두고,
     *       반영된 API와 시트 읽기 API를 비교용으로 함께 둔다.
     * 반환: (API 이름, 콜백을 받아 SDK를 부르는 함수) 목록
     * 근거: 표준 — Pleos Vehicle SDK 2.3.2 `check…Capabilities(
     *       (CapabilityConfig, List 또는 Map) -> Unit, (Exception) -> Unit)`
     */
    private fun checks(): List<Pair<String, Call>> {
        val hvac = vehicle.getHVAC() // 공조 API
        val door = vehicle.getDoor() // 문 API
        val window = vehicle.getWindow() // 창문 API
        val seat = vehicle.getSeat() // 시트 API
        val wiper = vehicle.getWiper() // 와이퍼 API
        return listOf(
            "HvacRecirculation" to { ok, fail -> hvac.checkHvacRecirculationCapabilities(list(ok), fail) },
            "HvacPower" to { ok, fail -> hvac.checkHvacPowerCapabilities(list(ok), fail) },
            "DoorLock" to { ok, fail -> door.checkDoorLockCapabilities(list(ok), fail) },
            "MoveWindowBlind" to { ok, fail -> window.checkMoveWindowBlindCapabilities(list(ok), fail) },
            "WindowBlindPosition" to { ok, fail -> window.checkWindowBlindPositionCapabilities(map(ok), fail) },
            "WindowLock" to { ok, fail -> window.checkWindowLockCapabilities(list(ok), fail) },
            "WindowPosition" to { ok, fail -> window.checkWindowPositionCapabilities(map(ok), fail) },
            "WiperSwitch" to { ok, fail -> wiper.checkWiperSwitchCapabilities(map(ok), fail) },
            "SeatOccupancy" to { ok, fail -> seat.checkSeatOccupancyCapabilities(list(ok), fail) },
            "SeatBeltBuckled" to { ok, fail -> seat.checkSeatBeltBuckledCapabilities(list(ok), fail) },
            "SeatForeAftPosition" to { ok, fail -> seat.checkSeatForeAftPositionCapabilities(map(ok), fail) },
            "MoveSeatForeAft" to { ok, fail -> seat.checkMoveSeatForeAftCapabilities(list(ok), fail) },
            "SeatMemory" to { ok, fail -> seat.checkSeatMemoryCapabilities(map(ok), fail) },
        )
    }

    /**
     * 작성: 2026-11-09 18:34:38 · 박건준
     * 함수: list
     * 목적: area 목록을 주는 콜백을 한 줄 문자열 콜백으로 바꾼다.
     * 인자: ok — 완성된 한 줄을 받을 콜백
     * 반환: SDK에 넘길 (권한, area 목록) 콜백
     */
    private fun <A : Area> list(ok: (String) -> Unit): (CapabilityConfig, List<A>) -> Unit =
        { config, areas -> ok("${config.accessType},${areas.joinToString(" ; ") { text(it) }}") }

    /**
     * 작성: 2026-11-09 18:34:38 · 박건준
     * 함수: map
     * 목적: area별 값(범위나 지원 값 목록)을 주는 콜백을 한 줄 문자열 콜백으로
     *       바꾼다.
     * 인자: ok — 완성된 한 줄을 받을 콜백
     * 반환: SDK에 넘길 (권한, area별 값) 콜백
     */
    private fun <A : Area, V> map(ok: (String) -> Unit): (CapabilityConfig, Map<A, V>) -> Unit =
        { config, areas ->
            ok("${config.accessType},${areas.entries.joinToString(" ; ") { "${text(it.key)}=${bounds(it.value)}" }}")
        }

    /**
     * 작성: 2026-11-09 18:34:38 · 박건준
     * 함수: text
     * 목적: area를 `ROW_1_LEFT+ROW_1_RIGHT(5)`처럼 이름과 합친 번호로 쓴다.
     *       Pleos 서비스 로그의 `areaIds`와 같은 번호라 로그와 맞춰 볼 수 있다.
     * 인자: area — 좌석·문·창문 area
     * 반환: 이름을 `+`로 이은 것과 괄호 안 번호
     */
    private fun text(area: Area): String {
        val values: List<AreaValue> = when (area) { // area를 이루는 위치들
            is SeatArea -> area.values.toList()
            is DoorArea -> area.values.toList()
            is WindowArea -> area.values.toList()
            else -> emptyList()
        }
        return values.joinToString("+") { (it as Enum<*>).name } + "(${values.sumOf { it.id }})"
    }

    /**
     * 작성: 2026-11-09 18:34:38 · 박건준
     * 함수: bounds
     * 목적: area별 값이 범위(`AreaBounds`)면 `최소~최대`로, 아니면 그대로 쓴다.
     * 인자: value — 범위 또는 지원 값 목록
     * 반환: 사람이 읽는 문자열
     */
    private fun bounds(value: Any?): String = when (value) {
        is AreaBounds<*> -> "${value.minValue}~${value.maxValue}"
        is List<*> -> value.joinToString("|")
        else -> value.toString()
    }

    private companion object {
        /**
         * 작성: 2026-11-09 18:34:38 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그. 차 관련 로그와 같은 태그를 쓴다.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        const val TAG = "SunstoneCar"
    }
}

/**
 * 작성: 2026-11-09 18:34:38 · 박건준
 * 클래스: Call
 * 목적: SDK 확인 함수 하나를 부르는 방법. 성공하면 한 줄 문자열을, 실패하면
 *       예외를 콜백으로 준다. `awaitCallback()`에 그대로 넘긴다.
 */
private typealias Call = (ok: (String) -> Unit, fail: (Exception) -> Unit) -> Unit
