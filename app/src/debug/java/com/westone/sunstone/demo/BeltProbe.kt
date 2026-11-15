package com.westone.sunstone.demo

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.util.Log
import com.westone.sunstone.car.awaitCallback
import kotlinx.coroutines.delay

/**
 * 작성: 2026-11-10 14:40:00 · 박건준
 * 수정: 2026-11-11 12:30:00 · 박건준
 * 클래스: BeltProbe
 * 목적: 벨트 값을 에뮬레이터에서 바꿀 수 있는지 잰다. 콘솔에는 좌석별 벨트
 *       칸이 없고 차 전체 경고등 `SeatBelt`(telltales)만 있어서, 그 값이
 *       SDK의 좌석별 벨트·미착용 경고·경고등 읽기 중 어디에 들어오는지 본다.
 *       실측용이라 debug 빌드에만 있다. 결과는 `belt,` 줄로 남긴다.
 *       1. 미착용 경고 지원 목록, 좌석마다 착용·미착용 경고 읽기, 경고등 읽기
 *       2. 같은 값의 리스너를 `LISTEN_MS` 등록해 둔다. 그동안 밖에서 콘솔
 *          `PUT /vehicle/telltales/simulator?type=SeatBelt&state=On|Off`로
 *          경고등을 켜고 끈다
 * 인자: vehicle — 초기화된 Pleos `Vehicle`. 루틴이 쓰는 것과 따로 만든다
 * 근거: 측정 — 콘솔 카탈로그 63개 중 벨트 관련은 `safety.telltales`의
 *       `SeatBelt` 하나(차량 속성 `TELLTALES` 0x21410900, area 0).
 *       경고등 읽기에 쓴 `PRIVATE_CAR_SAFETY`는 2026-11-11에 뺐다. 지금
 *       돌리면 경고등 단계는 `need Permission`으로 끝난다
 */
internal class BeltProbe(private val vehicle: Vehicle) {

    private companion object {
        /**
         * 작성: 2026-11-10 14:40:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-10 14:40:00 · 박건준
         * 변수: LISTEN_MS
         * 목적: 리스너를 등록해 두는 시간(밀리초). 이 동안 콘솔에서 경고등을
         *       바꾼다.
         * 근거: 미정 — 켜고 끄기를 두 번 할 수 있는 시간
         */
        const val LISTEN_MS = 30_000L

        /**
         * 작성: 2026-11-10 14:40:00 · 박건준
         * 변수: SEATS
         * 목적: 확인할 좌석(이름, area). 차일드락이 보는 뒷좌석 좌우와 운전석.
         */
        val SEATS = listOf(
            "운전석" to SeatArea(SeatValue.ROW_1_LEFT),
            "뒷좌석 왼쪽" to SeatArea(SeatValue.ROW_2_LEFT),
            "뒷좌석 오른쪽" to SeatArea(SeatValue.ROW_2_RIGHT),
        )
    }

    /**
     * 작성: 2026-11-10 14:40:00 · 박건준
     * 함수: run
     * 목적: 클래스 설명의 1~2단계를 한다. 한 호출이 실패해도 다음으로
     *       넘어가고, 끝나면 리스너를 모두 끊는다.
     */
    suspend fun run() {
        val safety = vehicle.getSafety() // 안전 API
        val seat = vehicle.getSeat() // 시트 API
        runCatching {
            awaitCallback<String> { ok, fail ->
                safety.checkSeatNotBuckledWarningCapabilities({ config, areas ->
                    ok("${config.accessType},${areas.joinToString(" ") { it.values.joinToString("+") }}")
                }, fail)
            }
        }.fold({ Log.i(TAG, "belt,미착용 경고 지원,$it") }, { Log.i(TAG, "belt,미착용 경고 지원,실패,${it.message}") })
        read()
        val buckled = SEATS.map { (name, _) -> // 좌석마다 착용 리스너
            object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(value: Boolean?) = log("착용 리스너", name, value)
                override fun onFailed(e: Exception) = log("착용 리스너 실패", name, e.message)
            }
        }
        val warning = SEATS.map { (name, _) -> // 좌석마다 미착용 경고 리스너
            object : SeatBeltNotBuckledWarningListener {
                override fun onSeatBeltNotBuckledWarningUpdated(value: Boolean?) = log("경고 리스너", name, value)
                override fun onFailed(e: Exception) = log("경고 리스너 실패", name, e.message)
            }
        }
        val telltale = object : TelltaleInfoListener { // 경고등 리스너
            override fun onTelltaleInfoUpdated(info: Telltales?) = log("경고등 리스너", "차", info?.let(::seatBelt))
            override fun onFailed(e: Exception) = log("경고등 리스너 실패", "차", e.message)
        }
        SEATS.forEachIndexed { i, (name, area) ->
            runCatching { seat.registerSeatBeltBuckledState(area, buckled[i]) }
                .onFailure { log("착용 등록 실패", name, it.message) }
            runCatching { safety.registerSeatBeltNotBuckledWarning(area, warning[i]) }
                .onFailure { log("경고 등록 실패", name, it.message) }
        }
        runCatching { safety.registerTelltales(telltale) }.onFailure { log("경고등 등록 실패", "차", it.message) }
        Log.i(TAG, "belt,대기,${LISTEN_MS / 1000}초")
        try {
            delay(LISTEN_MS)
            read()
        } finally {
            SEATS.forEachIndexed { i, (_, area) ->
                runCatching { seat.unregisterSeatBeltBuckledState(area, buckled[i]) }
                runCatching { safety.unregisterSeatBeltNotBuckledWarning(area, warning[i]) }
            }
            runCatching { safety.unregisterTelltales(telltale) }
            Log.i(TAG, "belt 끝")
        }
    }

    /**
     * 작성: 2026-11-10 14:40:00 · 박건준
     * 함수: read
     * 목적: 좌석마다 착용·미착용 경고와 차 경고등을 한 번씩 읽어 남긴다.
     */
    private suspend fun read() {
        val safety = vehicle.getSafety() // 안전 API
        val seat = vehicle.getSeat() // 시트 API
        SEATS.forEach { (name, area) ->
            val on = runCatching { // 착용
                awaitCallback<Boolean?> { ok, fail -> seat.getCurrentSeatBeltBuckledState(area, ok, fail) }
            }.fold({ it.toString() }, { "실패 ${it.message}" })
            val warn = runCatching { // 미착용 경고
                awaitCallback<Boolean?> { ok, fail -> safety.hasSeatBeltNotBuckledWarning(area, ok, fail) }
            }.fold({ it.toString() }, { "실패 ${it.message}" })
            log("읽기", name, "착용 $on / 경고 $warn")
        }
        val lamp = runCatching { awaitCallback<Telltales> { ok, fail -> safety.getTelltalesInfo(ok, fail) } }
            .fold(::seatBelt, { "실패 ${it.message}" }) // 벨트 경고등
        log("읽기", "차", "경고등 $lamp")
    }

    /**
     * 작성: 2026-11-10 14:40:00 · 박건준
     * 함수: seatBelt
     * 목적: 경고등 목록에서 벨트 경고등만 글자로 꺼낸다.
     * 반환: "상태/색" 또는 "없음(경고등 n개)"
     */
    private fun seatBelt(info: Telltales): String =
        info.telltales.firstOrNull { it.type.name == "SEAT_BELT" }?.let { "${it.state}/${it.color}" }
            ?: "없음(경고등 ${info.telltales.size}개)"

    /**
     * 작성: 2026-11-10 14:40:00 · 박건준
     * 함수: log
     * 목적: `belt,무엇,좌석,값` 한 줄을 남긴다. CSV를 깨지 않게 쉼표를 바꾼다.
     */
    private fun log(what: String, seat: String, value: Any?) {
        Log.i(TAG, "belt,$what,$seat,${value.toString().replace(',', ';')}")
    }
}
