package com.westone.sunstone.demo

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.MoveControl
import android.os.SystemClock
import android.util.Log
import com.westone.sunstone.car.awaitCallback
import com.westone.sunstone.routines.model.SeatAxis
import kotlinx.coroutines.delay

/**
 * 작성: 2026-11-09 18:53:00 · 박건준
 * 수정: 2026-11-11 11:50:00 · 박건준
 * 클래스: SeatTimer
 * 목적: 운전석(또는 조수석) 시트 축마다 SDK로 FORWARD·BACKWARD를 보내고, 콘솔 값이
 *       0→100, 100→0에 닿는 시간을 잰다. 시트 위치를 앱이 읽을 수 없어서,
 *       "몇 초 움직이면 몇만큼 가는지"로 위치를 맞출 수 있을지 보려는 실측이라
 *       debug 빌드에만 있다. 결과는 `seattime,축,방향,회차,밀리초` 줄과 축·
 *       방향별 평균 줄로 logcat에 남긴다.
 * 인자: vehicle — 초기화된 Pleos `Vehicle`. 루틴이 쓰는 것과 따로 만든다
 *       passenger — true면 조수석, false면 운전석
 * 근거: 측정 — SDK와 AAOS 속성 모두 시트 위치를 읽지 못했다
 *       (docs/api-spike.md 시트 위치 읽기 행)
 */
internal class SeatTimer(private val vehicle: Vehicle, passenger: Boolean = false) {

    /** 잴 좌석 area */
    private val area = SeatArea(if (passenger) SeatValue.ROW_1_RIGHT else SeatValue.ROW_1_LEFT)

    /** 콘솔 좌석 이름. 결과 줄에도 쓴다 */
    private val seat = if (passenger) "passenger" else "driver"

    private companion object {
        /**
         * 작성: 2026-11-09 18:53:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 18:53:00 · 박건준
         * 변수: ROUNDS
         * 목적: 축·방향마다 잴 횟수.
         * 근거: 미정 — 대표 지시(5번)
         */
        const val ROUNDS = 5

        /**
         * 작성: 2026-11-09 18:53:00 · 박건준
         * 변수: POLL_MS
         * 목적: 움직이는 동안 콘솔 값을 읽는 간격(밀리초). 잰 시간의 오차가 이
         *       만큼 생긴다.
         * 근거: 미정 — 콘솔 읽기 한 번이 수십 밀리초라 100으로 정했다
         */
        const val POLL_MS = 100L

        /**
         * 작성: 2026-11-09 18:53:00 · 박건준
         * 변수: LIMIT_MS
         * 목적: 한 번 움직일 때 기다리는 최대 시간(밀리초). 넘으면 STOP하고 그
         *       회차를 실패로 적는다.
         * 근거: 측정 — probe에서 0→100이 10초 안에 끝났다. 세 배로 잡았다
         */
        const val LIMIT_MS = 30_000L

        /**
         * 작성: 2026-11-09 18:53:00 · 박건준
         * 변수: AXES
         * 목적: 잴 축과 콘솔 축 이름. 콘솔에 없는 축(쿠션 길이, 머리받이)은
         *       잴 수 없어 뺀다.
         * 근거: 측정 — 콘솔 `seat.positions`에는 slide·recline·tilt·height·
         *       lumbar만 있다
         */
        val AXES = listOf(
            SeatAxis.FORE_AFT to "slide",
            SeatAxis.HEIGHT to "height",
            SeatAxis.BACKREST_ANGLE to "recline",
            SeatAxis.TILT to "tilt",
        )
    }

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: run
     * 목적: 축마다 콘솔로 0에 맞춘 뒤 `ROUNDS`번 0→100, 100→0을 재고, 끝나면
     *       0으로 돌려 둔다.
     * 반환: 잰 횟수(축 × 방향 × 회차)
     */
    suspend fun run(): Int {
        var count = 0 // 잰 횟수
        AXES.forEach { (axis, control) ->
            VirtualConsole.setSeat(control, seat, 0)
            delay(POLL_MS)
            val up = mutableListOf<Long>() // 0→100 걸린 시간(밀리초), 실패한 회차는 빠진다
            val down = mutableListOf<Long>() // 100→0 걸린 시간(밀리초)
            repeat(ROUNDS) { round ->
                time(axis, control, MoveControl.FORWARD, round + 1)?.let(up::add)
                time(axis, control, MoveControl.BACKWARD, round + 1)?.let(down::add)
                count += 2
            }
            summary(axis, "0→100", up)
            summary(axis, "100→0", down)
            VirtualConsole.setSeat(control, seat, 0)
        }
        Log.i(TAG, "seattime 끝: $seat ${count}회")
        return count
    }

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: time
     * 목적: 한 방향으로 움직이기 시작해 콘솔 값이 끝(앞이면 100, 뒤면 0)에
     *       닿을 때까지 잰다. 어떻게 끝나든 STOP을 보낸다.
     * 인자: axis — 움직일 축
     *       control — 콘솔 축 이름
     *       direction — FORWARD(값이 커지는 쪽) 또는 BACKWARD
     *       round — 회차(1부터)
     * 반환: 걸린 시간(밀리초). 제한 시간 안에 못 닿으면 null
     */
    private suspend fun time(axis: SeatAxis, control: String, direction: MoveControl, round: Int): Long? {
        val goal = if (direction == MoveControl.FORWARD) 100 else 0 // 닿아야 할 콘솔 값
        val path = "seat.positions.$control.$seat" // 콘솔 값 경로
        val started = SystemClock.elapsedRealtime() // 움직이기 시작한 시각(밀리초)
        var took: Long? = null // 걸린 시간(밀리초), 못 닿으면 null
        try {
            move(axis, direction)
            while (SystemClock.elapsedRealtime() - started < LIMIT_MS) {
                delay(POLL_MS)
                if (VirtualConsole.read(path).toDoubleOrNull()?.toInt() == goal) {
                    took = SystemClock.elapsedRealtime() - started
                    break
                }
            }
        } finally {
            runCatching { move(axis, MoveControl.STOP) }
        }
        Log.i(TAG, "seattime,$seat,$axis,${if (goal == 100) "0→100" else "100→0"},$round,${took ?: "제한 시간 넘김"}")
        return took
    }

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: summary
     * 목적: 축·방향 하나의 평균·최소·최대를 한 줄로 남긴다.
     * 인자: axis — 축
     *       label — 방향 표시(`0→100` 또는 `100→0`)
     *       times — 잰 시간 목록(밀리초)
     */
    private fun summary(axis: SeatAxis, label: String, times: List<Long>) {
        val avg = if (times.isEmpty()) "없음" else times.average().toLong().toString() // 평균(밀리초)
        Log.i(TAG, "seattime평균,$seat,$axis,$label,${times.size}회,$avg,${times.minOrNull()},${times.maxOrNull()}")
    }

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: move
     * 목적: 운전석 축 하나에 앞/뒤/정지를 보내고 차가 받을 때까지 기다린다.
     * 인자: axis — 움직일 축
     *       control — FORWARD, BACKWARD, STOP
     * 근거: 표준 — Pleos Vehicle SDK 2.3.2 `moveSeat…(area, MoveControl,
     *       onComplete, onFailed)`
     */
    private suspend fun move(axis: SeatAxis, control: MoveControl) = awaitCallback { ok, fail ->
        val seat = vehicle.getSeat() // 시트 API
        val done: (SeatArea, MoveControl?) -> Unit = { _, _ -> ok(Unit) } // 차가 받았다는 콜백
        when (axis) {
            SeatAxis.FORE_AFT -> seat.moveSeatForeAft(area, control, done, fail)
            SeatAxis.HEIGHT -> seat.moveSeatHeight(area, control, done, fail)
            SeatAxis.BACKREST_ANGLE -> seat.moveSeatBackrestAngle(area, control, done, fail)
            SeatAxis.TILT -> seat.moveSeatTilt(area, control, done, fail)
            SeatAxis.DEPTH -> seat.moveSeatDepth(area, control, done, fail)
            SeatAxis.HEADREST_HEIGHT -> seat.moveSeatHeadrestHeight(area, control, done, fail)
        }
    }
}
