package com.westone.sunstone.demo

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeadrestHeightAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeightPositionListener
import ai.pleos.playground.vehicle.listener.SeatTiltPositionListener
import android.util.Log
import com.westone.sunstone.car.awaitCallback
import kotlinx.coroutines.delay

/**
 * 작성: 2026-11-09 20:40:00 · 박건준
 * 클래스: SeatReadCheck
 * 목적: SDK 시트 위치 읽기가 정말 안 되는지 다른 방식으로 다시 확인한다.
 *       실측용이라 debug 빌드에만 있다. 결과는 `seatcheck,` 줄로 남긴다.
 *       1. 축마다 위치 읽기 지원 목록(권한·area·범위)
 *       2. 지원 목록이 돌려준 area 객체 그대로 `getCurrent…`를 부른 값
 *       3. 위치 리스너를 `LISTEN_MS` 동안 등록해 받은 값(그동안 콘솔에서
 *          값을 바꾼다)
 * 인자: vehicle — 초기화된 Pleos `Vehicle`. 루틴이 쓰는 것과 따로 만든다
 * 근거: 측정 — `getCurrentSeat…Position(SeatArea(ROW_1_LEFT))`는 늘 0이었다
 *       (docs/api-spike.md 시트 위치 읽기 행)
 */
internal class SeatReadCheck(private val vehicle: Vehicle) {

    private companion object {
        /**
         * 작성: 2026-11-09 20:40:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 20:40:00 · 박건준
         * 변수: LISTEN_MS
         * 목적: 위치 리스너를 등록해 두는 시간(밀리초). 이 동안 콘솔에서 시트
         *       값을 바꾼다.
         * 근거: 미정 — 콘솔에서 네 축을 두 번씩 바꿀 수 있는 시간
         */
        const val LISTEN_MS = 20_000L
    }

    /**
     * 작성: 2026-11-09 20:40:00 · 박건준
     * 함수: run
     * 목적: 지원 목록 → 지원 area로 읽기 → 리스너 순서로 확인한다. 한 단계가
     *       실패해도 다음으로 넘어간다.
     */
    suspend fun run() {
        val seat = vehicle.getSeat() // 시트 API
        val axes = listOf<Triple<String, Check, Read>>( // (축 이름, 지원 목록 묻기, 읽기)
            Triple("ForeAft", { ok, f -> seat.checkSeatForeAftPositionCapabilities(ok, f) },
                { a, ok, f -> seat.getCurrentSeatForeAftPosition(a, ok, f) }),
            Triple("Height", { ok, f -> seat.checkSeatHeightPositionCapabilities(ok, f) },
                { a, ok, f -> seat.getCurrentSeatHeightPosition(a, ok, f) }),
            Triple("BackrestAngle", { ok, f -> seat.checkSeatBackrestAnglePositionCapabilities(ok, f) },
                { a, ok, f -> seat.getCurrentSeatBackrestAnglePosition(a, ok, f) }),
            Triple("Tilt", { ok, f -> seat.checkSeatTiltPositionCapabilities(ok, f) },
                { a, ok, f -> seat.getCurrentSeatTiltPosition(a, ok, f) }),
            Triple("HeadrestHeight", { ok, f -> seat.checkSeatHeadrestHeightPositionCapabilities(ok, f) },
                { a, ok, f -> seat.getCurrentSeatHeadrestHeightAftPosition(a, ok, f) }),
        )
        axes.forEach { (name, check, read) ->
            val areas = runCatching { // 지원 area와 범위, 실패하면 null
                awaitCallback<Map<SeatArea, String>> { ok, fail ->
                    check({ config, map ->
                        Log.i(TAG, "seatcheck,$name,지원,${config.accessType}," +
                            map.entries.joinToString(" ; ") { "${it.key.values.joinToString("+")}=" +
                                "${it.value.minValue}~${it.value.maxValue}" })
                        ok(map.mapValues { "${it.value.minValue}~${it.value.maxValue}" })
                    }, fail)
                }
            }.onFailure { Log.i(TAG, "seatcheck,$name,지원,실패,${it.message}") }.getOrNull()
            val driver = areas?.keys?.firstOrNull { SeatValue.ROW_1_LEFT in it.values } // 지원 목록이 준 운전석 area
            if (driver != null) {
                val value = runCatching { awaitCallback<Int?> { ok, fail -> read(driver, ok, fail) } } // 읽은 값
                    .fold({ it.toString() }, { "실패 ${it.message}" })
                Log.i(TAG, "seatcheck,$name,읽기(지원 area),$value,콘솔 ${VirtualConsole.read(console(name))}")
            }
        }
        listen()
        Log.i(TAG, "seatcheck 끝")
    }

    /**
     * 작성: 2026-11-09 20:40:00 · 박건준
     * 함수: listen
     * 목적: 운전석 위치 리스너 다섯 개를 `LISTEN_MS` 동안 등록해 두고 받은
     *       값을 남긴다. 끝나면 모두 끊는다.
     */
    private suspend fun listen() {
        val seat = vehicle.getSeat() // 시트 API
        val area = SeatArea(SeatValue.ROW_1_LEFT) // 운전석
        val log: (String, Int?) -> Unit = { name, v -> Log.i(TAG, "seatcheck,$name,리스너,$v") } // 받은 값을 남긴다
        val failed: (String, Exception) -> Unit = { name, e -> Log.i(TAG, "seatcheck,$name,리스너 실패,${e.message}") }
        val foreAft = object : SeatForeAftPositionListener { // 앞뒤
            override fun onSeatForeAftPositionUpdated(value: Int?) = log("ForeAft", value)
            override fun onFailed(e: Exception) = failed("ForeAft", e)
        }
        val height = object : SeatHeightPositionListener { // 높이
            override fun onSeatHeightPositionUpdated(value: Int?) = log("Height", value)
            override fun onFailed(e: Exception) = failed("Height", e)
        }
        val backrest = object : SeatBackrestAnglePositionListener { // 등받이
            override fun onSeatBackrestAnglePositionUpdated(value: Int?) = log("BackrestAngle", value)
            override fun onFailed(e: Exception) = failed("BackrestAngle", e)
        }
        val tilt = object : SeatTiltPositionListener { // 쿠션 각도
            override fun onSeatTiltPositionUpdated(value: Int?) = log("Tilt", value)
            override fun onFailed(e: Exception) = failed("Tilt", e)
        }
        val headrest = object : SeatHeadrestHeightAftPositionListener { // 머리받이
            override fun onSeatHeadrestHeightAftPositionUpdated(value: Int?) = log("HeadrestHeight", value)
            override fun onFailed(e: Exception) = failed("HeadrestHeight", e)
        }
        Log.i(TAG, "seatcheck,리스너 시작")
        runCatching { seat.registerSeatForeAftPosition(area, foreAft) }.onFailure { failed("ForeAft", it as Exception) }
        runCatching { seat.registerSeatHeightPosition(area, height) }.onFailure { failed("Height", it as Exception) }
        runCatching { seat.registerSeatBackrestAnglePosition(area, backrest) }
            .onFailure { failed("BackrestAngle", it as Exception) }
        runCatching { seat.registerSeatTiltPosition(area, tilt) }.onFailure { failed("Tilt", it as Exception) }
        runCatching { seat.registerSeatHeadrestHeightAftPosition(area, headrest) }
            .onFailure { failed("HeadrestHeight", it as Exception) }
        try {
            delay(LISTEN_MS)
        } finally {
            runCatching { seat.unregisterSeatForeAftPosition(area, foreAft) }
            runCatching { seat.unregisterSeatHeightPosition(area, height) }
            runCatching { seat.unregisterSeatBackrestAnglePosition(area, backrest) }
            runCatching { seat.unregisterSeatTiltPosition(area, tilt) }
            runCatching { seat.unregisterSeatHeadrestHeightAftPosition(area, headrest) }
            Log.i(TAG, "seatcheck,리스너 끝")
        }
    }

    /**
     * 작성: 2026-11-09 20:40:00 · 박건준
     * 함수: console
     * 목적: 축 이름을 콘솔 `/vehicle/state` 경로로 바꾼다.
     * 인자: name — 축 이름(SDK 함수 이름에 들어가는 것)
     * 반환: 점 경로. 콘솔에 칸이 없는 머리받이는 null
     */
    private fun console(name: String): String? = when (name) {
        "ForeAft" -> "seat.positions.slide.driver"
        "Height" -> "seat.positions.height.driver"
        "BackrestAngle" -> "seat.positions.recline.driver"
        "Tilt" -> "seat.positions.tilt.driver"
        else -> null
    }
}

/**
 * 작성: 2026-11-09 20:40:00 · 박건준
 * 클래스: Check
 * 목적: 축 하나의 위치 지원 목록을 묻는 SDK 호출. (권한, area별 범위)를
 *       콜백으로 준다.
 */
private typealias Check = (
    ok: (ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig,
         Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>>) -> Unit,
    fail: (Exception) -> Unit,
) -> Unit

/**
 * 작성: 2026-11-09 20:40:00 · 박건준
 * 클래스: Read
 * 목적: 축 하나의 지금 위치를 읽는 SDK 호출.
 */
private typealias Read = (area: SeatArea, ok: (Int?) -> Unit, fail: (Exception) -> Unit) -> Unit
