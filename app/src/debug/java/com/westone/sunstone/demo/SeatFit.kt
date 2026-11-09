package com.westone.sunstone.demo

import android.util.Log
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.Target

/**
 * 작성: 2026-11-09 19:14:00 · 박건준
 * 클래스: SeatFit
 * 목적: 차 창구(`PleosCarPort`)의 시간 기반 시트 이동이 목표에 얼마나
 *       가깝게 가는지 콘솔 값으로 잰다. 운전석 축마다 목표 `TARGETS`를
 *       `ROUNDS`번씩 맞추고 `seatfit,축,목표,회차,결과,콘솔,오차` 줄을
 *       logcat에 남긴다. 실측용이라 debug 빌드에만 있다.
 * 인자: car — 실측할 차 창구. 루틴이 쓰는 것과 같은 `PleosCarPort`를 넘긴다
 */
internal class SeatFit(private val car: CarPort) {

    private companion object {
        /**
         * 작성: 2026-11-09 19:14:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 19:14:00 · 박건준
         * 변수: TARGETS, ROUNDS
         * 목적: 맞춰 볼 목표 위치(0~100)와 목표마다 횟수.
         * 근거: 미정 — 대표 지시(30·70, 3번씩)
         */
        val TARGETS = listOf(30, 70) // 목표 위치
        const val ROUNDS = 3 // 목표마다 횟수

        /**
         * 작성: 2026-11-09 19:14:00 · 박건준
         * 변수: AXES
         * 목적: 잴 축과 콘솔 축 이름. 콘솔에 칸이 없는 축은 뺀다.
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
     * 작성: 2026-11-09 19:14:00 · 박건준
     * 함수: run
     * 목적: 축·목표·회차마다 시트를 목표로 보내고, 끝난 뒤 콘솔 값과의
     *       차이를 남긴다. 기어가 P가 아니면 `PleosCarPort`가 움직이지 않고
     *       결과 칸에 `Failed`가 남는다. 끝나면 축마다 0으로 돌려 둔다.
     * 반환: 잰 횟수
     */
    suspend fun run(): Int {
        var count = 0 // 잰 횟수
        AXES.forEach { (axis, control) ->
            TARGETS.forEach { goal ->
                repeat(ROUNDS) { round ->
                    // → 로직 이동: PleosCarPort.apply()
                    val result = car.apply(Command(Target.SeatPosition(Seat.DRIVER, axis), goal)) // 이동 결과
                    val console = VirtualConsole.read("seat.positions.$control.driver") // 움직인 뒤 콘솔 값
                    val error = console.toDoubleOrNull()?.let { (it - goal).toInt().toString() } ?: "" // 오차(콘솔 − 목표)
                    Log.i(TAG, "seatfit,$axis,$goal,${round + 1},${result.javaClass.simpleName},$console,$error")
                    count++
                }
            }
            VirtualConsole.setSeat(control, "driver", 0)
        }
        Log.i(TAG, "seatfit 끝: ${count}회")
        return count
    }
}
