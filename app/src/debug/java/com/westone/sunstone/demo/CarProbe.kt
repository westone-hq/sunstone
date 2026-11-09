package com.westone.sunstone.demo

import android.os.SystemClock
import android.util.Log
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.ControlResult
import com.westone.sunstone.routines.port.Target
import kotlinx.coroutines.delay

/**
 * 작성: 2026-11-09 16:52:02 · 박건준
 * 클래스: CarProbe
 * 목적: 차 창구(`PleosCarPort`)가 다루는 제어 대상마다 값을 바꿨다가 되돌려
 *       보고, 실제로 반영되는지 한 줄씩 남긴다. docs/api-spike.md 실측용이라
 *       debug 빌드에만 있다.
 *       1. 원래 값 읽기 → 바꾸기 → 1초 → 다시 읽기
 *       2. 원래 값으로 되돌리기 → 1초 → 다시 읽기
 *       3. 다시 읽을 때마다 Virtual Console 값도 콘솔 HTTP API로 읽어 같은 줄에
 *          적는다
 *       - 시트는 기어 P일 때만 움직인다. 축 이동은 `PleosCarPort.moveSeatTo()`가
 *         STOP으로 끝낸다
 * 인자: car — 실측할 차 창구. 루틴이 쓰는 것과 같은 `PleosCarPort`를 넘긴다
 */
internal class CarProbe(private val car: CarPort) {

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: run
     * 목적: 대상 전부를 차례로 실측하고, 머리 줄과 대상마다 한 줄을 CSV 형식으로
     *       logcat(`SunstoneCar`)에 남긴다. PC에서 `probe,`로 시작하는 줄만 모으면
     *       CSV 파일이 된다.
     * 반환: 실측한 대상 수
     */
    suspend fun run(): Int {
        log(HEADER)
        val items = items() // 실측할 대상 목록, 시트 축은 맨 뒤
        items.forEach { log(measure(it).joinToString(",") { field -> csv(field) }) }
        Log.i(TAG, "probe 끝: ${items.size}개")
        return items.size
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 수정: 2026-11-09 18:53:00 · 박건준
     * 함수: measure
     * 목적: 대상 하나를 바꿨다가 되돌리고, CSV 한 줄에 들어갈 칸을 만든다.
     *       원래 값을 못 읽으면 `ProbeItem.fallback`을 원래 값으로 보고 진행하고,
     *       오류 칸에 적는다.
     * 인자: item — 실측할 대상
     * 반환: `HEADER`와 같은 순서의 칸 목록
     */
    private suspend fun measure(item: ProbeItem): List<String> {
        val label = item.label // 사람이 읽는 대상 이름
        if (item.needsPark && car.state.value.gear != Gear.PARK) {
            return listOf(label, item.api, item.call) + List(SKIPPED_BLANKS) { "" } + "기어 P 아님, 건너뜀"
        }
        val notes = mutableListOf<String>() // 오류 칸에 모을 문장
        // → 로직 이동: PleosCarPort.read()
        val original = car.read(item.target) // 바꾸기 전 값, 못 읽으면 null
        if (original == null) notes += "원래 값 못 읽음(${item.fallback}로 가정)"
        val base = original ?: item.fallback // 되돌릴 때 쓸 값
        val changed = item.alter(base) // 바꿔 볼 값

        val changeStarted = SystemClock.elapsedRealtime() // 바꾸기 시작 시각(밀리초)
        // → 로직 이동: PleosCarPort.apply()
        val changeResult = car.apply(Command(item.target, changed)) // 바꾸기 결과
        val changeMs = SystemClock.elapsedRealtime() - changeStarted // 바꾸기에 걸린 시간(밀리초)
        reason(changeResult)?.let { notes += "바꾸기: $it" }
        delay(SETTLE_MS)
        // → 로직 이동: PleosCarPort.read()
        val afterChange = car.read(item.readBack) // 바꾼 뒤 앱이 다시 읽은 값
        val consoleAfterChange = VirtualConsole.read(item.consolePath) // 바꾼 뒤 콘솔 값

        var revertResult: ControlResult? = null // 되돌리기 결과, 되돌리지 않는 대상은 null
        var revertMs = 0L // 되돌리기에 걸린 시간(밀리초)
        if (item.revert) {
            val revertStarted = SystemClock.elapsedRealtime() // 되돌리기 시작 시각(밀리초)
            // → 로직 이동: PleosCarPort.apply()
            revertResult = car.apply(Command(item.target, base))
            revertMs = SystemClock.elapsedRealtime() - revertStarted
            reason(revertResult)?.let { notes += "되돌리기: $it" }
            delay(SETTLE_MS)
        } else {
            notes += "되돌리기 없음(현재 값을 읽을 수 없는 대상)"
        }
        // → 로직 이동: PleosCarPort.read()
        val afterRevert = car.read(item.readBack) // 되돌린 뒤 앱이 다시 읽은 값
        val consoleAfterRevert = VirtualConsole.read(item.consolePath) // 되돌린 뒤 콘솔 값

        return listOf(
            label, item.api, item.call,
            show(original), show(changed), show(afterChange), consoleAfterChange,
            if (item.revert) show(base) else "", show(afterRevert), consoleAfterRevert,
            "$changeMs/$revertMs", "${name(changeResult)}/${revertResult?.let(::name).orEmpty()}",
            notes.joinToString(" / "),
        )
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 수정: 2026-11-09 18:37:39 · 박건준
     * 함수: items
     * 목적: 실측할 대상 목록을 만든다. API 이름과 호출 방식은 차 연결
     *       (PleosCarPort.kt)의 `read()`·`apply()`에 적힌 그대로 옮긴다.
     * 반환: 창문 → 선루프 → 창문 잠금 → 와이퍼 → 내기 순환 → 문 잠금 →
     *       블라인드 → 좌석 공조 → 운전석 시트 축 → 시트 메모리 순서의 목록
     * 근거: 측정 — 콘솔 `/vehicle/state`에 선루프·블라인드·좌석별 공조 전원·
     *       쿠션 길이·머리받이 칸이 없어서 콘솔 경로를 null로 둔다. 좌석 공조는
     *       콘솔에 전체 전원(`hvac.power_on`)만 있어 그 값을 비교한다
     */
    private fun items(): List<ProbeItem> = buildList {
        val window = "getCurrentWindowPosition / setWindowPosition" // 창문·선루프 API
        listOf(Window.FRONT_LEFT, Window.FRONT_RIGHT, Window.REAR_LEFT, Window.REAR_RIGHT).forEach {
            add(ProbeItem(Target.WindowPosition(it), window, DIRECT, "body.windows.${it.console()}", 0) { v ->
                if (v == 0) WINDOW_TEST else 0
            })
        }
        listOf(Window.ROOF_1, Window.ROOF_2).forEach {
            add(ProbeItem(Target.WindowPosition(it), window, DIRECT, null, 0) { v -> if (v == 0) WINDOW_TEST else 0 })
        }
        listOf(Window.FRONT_LEFT, Window.FRONT_RIGHT, Window.REAR_LEFT, Window.REAR_RIGHT).forEach {
            add(
                ProbeItem(
                    Target.WindowLock(it), "getCurrentWindowLockState / setWindowLock", DIRECT,
                    "window_lock.locked.${it.console()}", false, alter = ::flip,
                ),
            )
        }
        add(
            ProbeItem(
                Target.Wiper, "getCurrentWiperSwitch / setWiperSwitch", DIRECT,
                "body.wipers.front.mode", WiperMode.OFF,
            ) { v -> if (v == WiperMode.OFF) WiperMode.AUTO else WiperMode.OFF },
        )
        add(
            ProbeItem(
                Target.Recirculation, "getHvacRecirculationState / setHvacRecirculation (다섯 좌석 합친 area)", DIRECT,
                "hvac.recirc_on", false, alter = ::flip,
            ),
        )
        add(
            ProbeItem(
                Target.DoorLock, "getDoorLockState / setDoorLock (네 문 합친 area)", DIRECT,
                "body.door_locks.all", true, alter = ::flip,
            ),
        )
        add(
            ProbeItem(
                Target.RearBlind, "getCurrentWindowBlindPosition / moveWindowBlind", DIRECT,
                null, false, alter = ::flip,
            ),
        )
        listOf(Seat.DRIVER, Seat.PASSENGER, Seat.REAR_LEFT, Seat.REAR_RIGHT).forEach {
            add(
                ProbeItem(
                    Target.Climate(it), "getHvacPowerState / setHvacPower (다섯 좌석 합친 area)", DIRECT,
                    "hvac.power_on", false, alter = ::flip,
                ),
            )
        }
        SeatAxis.entries.forEach { axis ->
            add(
                ProbeItem(
                    Target.SeatPosition(Seat.DRIVER, axis),
                    axis.api(), DIRECT,
                    axis.console()?.let { "seat.positions.$it.driver" }, SEAT_FALLBACK, needsPark = true,
                ) { v -> (v as Int).let { if (it <= SEAT_FALLBACK) it + SEAT_STEP else it - SEAT_STEP } },
            )
        }
        add(
            ProbeItem(
                Target.SeatMemory(Seat.DRIVER), "selectSeatMemory(앞뒤 위치로 확인)", DIRECT,
                "seat.positions.slide.driver", 0, needsPark = true, revert = false,
                readBack = Target.SeatPosition(Seat.DRIVER, SeatAxis.FORE_AFT),
            ) { MEMORY_SLOT },
        )
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: log
     * 목적: CSV 한 줄을 `probe,` 머리를 붙여 logcat에 남긴다.
     * 인자: line — 쉼표로 이은 CSV 한 줄
     */
    private fun log(line: String) {
        Log.i(TAG, "probe,$line")
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: csv
     * 목적: 칸 하나를 큰따옴표로 감싸서 오류 문장 속 쉼표가 칸을 나누지 않게 한다.
     * 인자: field — 칸 내용
     * 반환: 감싼 칸
     */
    private fun csv(field: String): String = "\"" + field.replace("\"", "\"\"") + "\""

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: show
     * 목적: 읽은 값을 칸에 넣을 글자로 바꾼다.
     * 인자: value — 읽은 값. null이면 읽지 못한 것
     * 반환: 값의 글자 표현, null이면 `못 읽음`
     */
    private fun show(value: Any?): String = value?.toString() ?: "못 읽음"

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: name
     * 목적: 제어 결과를 짧은 이름으로 바꾼다.
     * 인자: result — `PleosCarPort.apply()`의 결과
     * 반환: `Done`, `Sent`, `Unsupported`, `Failed` 중 하나
     */
    private fun name(result: ControlResult): String = result.javaClass.simpleName

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: reason
     * 목적: 실패·미지원 결과에서 이유 문장만 꺼낸다.
     * 인자: result — `PleosCarPort.apply()`의 결과
     * 반환: 이유 문장. 성공(`Done`, `Sent`)이면 null
     */
    private fun reason(result: ControlResult): String? = when (result) {
        is ControlResult.Unsupported -> result.reason
        is ControlResult.Failed -> result.reason
        else -> null
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: flip
     * 목적: 켜짐/꺼짐 대상의 바꿔 볼 값을 만든다.
     * 인자: value — 원래 값(Boolean)
     * 반환: 반대 값
     */
    private fun flip(value: Any): Any = !(value as Boolean)

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: console
     * 목적: 창문을 콘솔 JSON의 칸 이름으로 바꾼다. 선루프는 콘솔에 칸이 없다.
     * 반환: `front_left` 같은 칸 이름
     */
    private fun Window.console(): String = when (this) {
        Window.FRONT_LEFT -> "front_left"
        Window.FRONT_RIGHT -> "front_right"
        Window.REAR_LEFT -> "rear_left"
        Window.REAR_RIGHT -> "rear_right"
        Window.ROOF_1, Window.ROOF_2 -> ""
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 함수: console
     * 목적: 시트 축을 콘솔 JSON `seat.positions`의 칸 이름으로 바꾼다.
     * 반환: 칸 이름. 콘솔에 없는 축(쿠션 길이, 머리받이)은 null
     * 근거: 측정 — 콘솔 `seat.positions`에는 slide·recline·tilt·height·lumbar만
     *       있다. lumbar(허리 받침)는 쿠션 길이와 다른 축이라 대응시키지 않는다
     */
    private fun SeatAxis.console(): String? = when (this) {
        SeatAxis.FORE_AFT -> "slide"
        SeatAxis.HEIGHT -> "height"
        SeatAxis.BACKREST_ANGLE -> "recline"
        SeatAxis.TILT -> "tilt"
        SeatAxis.DEPTH, SeatAxis.HEADREST_HEIGHT -> null
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 수정: 2026-11-09 20:05:58 · 박건준
     * 함수: api
     * 목적: 시트 축을 CSV API 칸에 적을 읽기 / 이동 방법으로 바꾼다. 차
     *       연결(PleosCarPort.kt)은 위치를 차에서 읽지 않고 시간으로 맞춘
     *       추정값을 돌려주고, 이동은 `moveSeat…`를 시간만큼 보낸다.
     * 반환: `추정 위치 / moveSeatForeAft(시간)` 같은 글자
     */
    private fun SeatAxis.api(): String = when (this) {
        SeatAxis.FORE_AFT -> "추정 위치 / moveSeatForeAft(시간)"
        SeatAxis.HEIGHT -> "추정 위치 / moveSeatHeight(시간)"
        SeatAxis.BACKREST_ANGLE -> "추정 위치 / moveSeatBackrestAngle(시간)"
        SeatAxis.TILT -> "추정 위치 / moveSeatTilt(시간)"
        SeatAxis.DEPTH -> "추정 위치 / moveSeatDepth(시간)"
        SeatAxis.HEADREST_HEIGHT -> "추정 위치 / moveSeatHeadrestHeight(시간)"
    }

    /**
     * 작성: 2026-11-09 16:52:02 · 박건준
     * 클래스: ProbeItem
     * 목적: 실측할 대상 하나와 그 대상을 어떻게 바꾸고 확인할지 묶는다.
     * 인자: target — 바꿀 대상
     *       api — 읽기 / 쓰기 SDK 함수 이름
     *       call — 호출 방식(지금은 모두 직접)
     *       consolePath — 콘솔 `/vehicle/state` 안의 점 경로. null이면 콘솔에 없음
     *       fallback — 원래 값을 못 읽었을 때 원래 값으로 볼 값
     *       needsPark — true면 기어 P일 때만 실측한다(시트)
     *       revert — false면 되돌리지 않는다(현재 값을 읽을 수 없는 시트 메모리)
     *       readBack — 다시 읽을 대상. 기본은 `target`
     *       alter — 원래 값에서 바꿔 볼 값을 만든다
     */
    private class ProbeItem(
        val target: Target,
        val api: String,
        val call: String,
        val consolePath: String?,
        val fallback: Any,
        val needsPark: Boolean = false,
        val revert: Boolean = true,
        val readBack: Target = target,
        val alter: (Any) -> Any,
    ) {
        /** 대상 이름. `Target` 글자 표현 그대로(예: `WindowPosition(window=ROOF_1)`) */
        val label: String = target.toString()
    }

    private companion object {
        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: TAG
         * 목적: 로그 태그. 실측 결과는 차 영역이라 `SunstoneCar`를 쓴다.
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: SKIPPED_BLANKS
         * 목적: 건너뛴 대상 줄에서 대상·API·호출 방식과 오류 사이를 비워 둘 칸 수.
         *       `HEADER` 13칸 중 앞 3칸과 끝 1칸을 뺀 값이다.
         */
        const val SKIPPED_BLANKS = 9

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: HEADER
         * 목적: CSV 머리 줄. `measure()`가 만드는 칸 순서와 같다.
         */
        const val HEADER = "대상,API,호출 방식,원래 값,바꾼 값,앱이 다시 읽은 값,콘솔 값," +
            "되돌린 값,되돌린 뒤 앱 값,되돌린 뒤 콘솔 값,걸린 시간(밀리초 바꾸기/되돌리기),결과(바꾸기/되돌리기),오류"

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: SETTLE_MS
         * 목적: 바꾼 뒤 다시 읽기 전에 기다리는 시간(밀리초).
         * 근거: 미정 — 대표 지시(1초). 반영이 늦는 대상이 나오면 다시 정한다
         */
        const val SETTLE_MS = 1_000L

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: WINDOW_TEST
         * 목적: 닫힌 창문을 열어 볼 위치(0=닫힘~10=열림, Pleos 창문 단위).
         *       되돌릴 때 0으로 닫히는지가 다른 팀 실패 사례라서 닫힌 상태에서
         *       시작한다.
         * 근거: 인용 — 다른 팀 실측(2026-11), 창문을 0으로 되돌리기 실패 사례
         */
        const val WINDOW_TEST = 5

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: SEAT_FALLBACK
         * 목적: 시트 위치를 못 읽었을 때 원래 값으로 볼 값. 바꿀 방향을 정하는
         *       기준으로도 쓴다(이하면 늘리고, 넘으면 줄인다).
         * 근거: 미확인 — 시트 위치 단위를 아직 모른다. 콘솔은 0~100(%)으로 보인다
         */
        const val SEAT_FALLBACK = 50

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: SEAT_STEP
         * 목적: 시트 축마다 움직여 볼 양(차가 주는 단위 그대로).
         * 근거: 미확인 — 단위를 모르는 상태에서 콘솔 0~100 기준 10%로 정했다
         */
        const val SEAT_STEP = 10

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 변수: MEMORY_SLOT
         * 목적: 불러 볼 시트 메모리 번호.
         */
        const val MEMORY_SLOT = 1

        /**
         * 작성: 2026-11-09 16:52:02 · 박건준
         * 수정: 2026-11-09 18:37:39 · 박건준
         * 변수: DIRECT
         * 목적: CSV 호출 방식 칸에 적는 이름. 차 연결(PleosCarPort.kt)이 SDK를
         *       모두 직접 부른다. 이전 실측 CSV와 칸을 맞추려고 칸은 남긴다.
         */
        const val DIRECT = "직접"
    }
}
