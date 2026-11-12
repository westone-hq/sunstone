package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.Choice
import com.westone.sunstone.routines.dialog.Question
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.QuestionRow
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.engine.StepOutcome
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.model.WiperMode
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.ControlResult
import com.westone.sunstone.routines.port.MemoryPort
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.WashLines
import com.westone.sunstone.routines.speech.WashPart
import com.westone.sunstone.routines.speech.withObject
import java.time.LocalDate

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: WashRoutine
 * 목적: 세차 모드. 현대 세차 모드가 하는 일(창문·선루프 닫기, 와이퍼 끄기,
 *       내기 순환, 문 잠금, 미러 접기)을 앱이 한 단계씩 직접 한다.
 *       - 세차장에 도착해 멈추면 먼저 권한다. 주유소는 길안내 대화가 세차를
 *         예약했을 때만, 주유 정차가 아닌 세차 입구 정차에서 권한다
 *       - 예약한 세차 입구에서는 다시 묻지 않고 "곧 세차 모드를 켤게요"
 *         카드(`Asking`)를 올려 5초 뒤 켠다(취소할 수 있다). 세차장에서는
 *         "세차 모드 켤까요?" 카드로 묻는다
 *       - 다 하고 나서 한 번에 짧게 말한다. 하나라도 했으면 오늘을 마지막
 *         세차일로 남기고 예약을 지운다
 *       - 미러 접기와 N 유지는 API가 없어서 말로 안내한다. N 유지는 자동
 *         세차기가 있는 세차장에서만 말한다
 *       - 세차 모드 중 D로 바꾸면(정차 중이어도) 와이퍼·내기 순환을 원래대로
 *         돌린다. 창문은 다시 열지 않고 문 잠금은 풀지 않는다
 * 인자: booking — 길안내 대화가 한 세차 예약. `TripDialog`와 같은 것을 넘긴다
 *       memory — 마지막 세차일을 남긴다
 *       asking — 주유 정차 토스트를 낸다. `RoutineRunner`·`TripDialog`와 같은
 *       것을 넘긴다
 *       today — 오늘 날짜. 테스트에서 고정한다
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #3~#5, docs/SCREEN.md 6장 시나리오 ①
 *       다시 짠 흐름 5~9
 */
class WashRoutine(
    private val booking: WashBooking,
    private val memory: MemoryPort,
    private val asking: Asking,
    private val today: () -> LocalDate,
) : Routine {

    private companion object {
        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 변수: CLOSED
         * 목적: 창문 닫힘 값(Pleos 창문 단위 0~10에서 0).
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0 창문 0 설정 후 다시 읽은 값 0
         *       (docs/api-spike.md 세차 창문 행)
         */
        const val CLOSED = 0

        /**
         * 작성: 2026-11-12 18:22:00 · 박건준
         * 변수: ROOFS
         * 목적: 선루프 창문 두 개. 이 차에 없으면(읽을 수 없으면) 단계에서 뺀다.
         * 근거: 측정 — Pleos 에뮬레이터에는 선루프가 없어 읽기가 null이다
         *       (docs/api-spike.md 세차 선루프 행)
         */
        val ROOFS = setOf(Window.ROOF_1, Window.ROOF_2)

        /**
         * 작성: 2026-11-13 03:25:00 · 박건준
         * 변수: START_WAIT_MS
         * 목적: 예약한 세차 입구에서 세차 모드를 켜기 전에 기다리는 시간(밀리초).
         *       그 사이 "취소"라고 하거나 [취소]를 누르면 켜지 않는다.
         * 근거: 인용 — docs/SCREEN.md 6장 6단계: [지금 켜기]에 5초가 차오른다
         */
        const val START_WAIT_MS = 5_000L
    }

    override val id = RoutineId.WASH
    override val title = "세차 모드"
    override val intro = "세차 준비할게요."
    override val reportsAtEnd = true
    override val afterUndo = listOf(WashLines.FINISHED)
    override val undoNotice = WashLines.RESTORED_NOTICE

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 17:40:00 · 박희정
     * 함수: blockedReason
     * 목적: 지금 세차 모드를 하면 안 되는 이유를 돌려준다. 단계마다 다시 본다.
     *       - 달리는 중 → 멈춘 뒤에
     *       - P·N이 아님 → P나 N에 두면
     *       - 문(트렁크 포함)이 열림 → 문을 모두 닫으면
     * 인자: state — 지금 차 상태
     * 반환: 이유 문장. 해도 되면 null
     * 근거: 인용 — 세차 모드 기능 카드(Tab 1 3.2) 조건: 시동 ON, 20km/h 이하,
     *       문 닫힘. 속도는 CLAUDE.md "차를 바꾸는 일은 정차 중에만"을 따라 더
     *       좁게 멈춤으로 본다
     * 미구현: 시동 ON 여부는 차 상태에 없어서 보지 않는다
     */
    override fun blockedReason(state: CarState): String? = when {
        !state.stopped -> WashLines.BLOCKED_MOVING
        state.gear != Gear.PARK && state.gear != Gear.NEUTRAL -> WashLines.BLOCKED_GEAR
        state.doorOpen.any { it.value } -> WashLines.BLOCKED_DOOR
        else -> null
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: plan
     * 목적: 세차 단계를 만든다. 창문(선루프 포함) → 와이퍼 → 내기 순환 →
     *       문 잠금 → 사이드미러 안내 순서. 세차장에 있으면 N 유지 안내를
     *       더한다. 이 차에 없는 선루프(읽을 수 없음)는 넣지 않는다.
     *       옆 창문 네 개는 같은 이름("창문")이라 화면에서 한 칸으로 보인다.
     * 인자: request — 실행 요청. 쓰지 않는다
     *       car — 지금 장소와 선루프가 있는지 보려고 읽는다
     * 반환: 실행할 단계들
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #4는 주유소 장면이라 N 유지를 말하지
     *       않는다. docs/SCREEN.md 6장 할 일 칸 다섯
     */
    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> {
        val present = Window.entries.filter { it !in ROOFS || car.read(Target.WindowPosition(it)) != null } // 있는 창문
        val windows = present.map { w -> // 창문 닫기 단계들
            Step.Control(
                title = if (w in ROOFS) WashLines.STEP_SUNROOF else WashLines.STEP_WINDOWS,
                command = Command(Target.WindowPosition(w), CLOSED),
                fallback = "${withObject(w.label)} 닫아 주세요.",
                restoreOnUndo = false,
                icon = "window",
            )
        }
        val atCarWash = car.state.value.place == Place.CAR_WASH // 자동 세차기가 있는 곳인지
        return windows + listOfNotNull(
            Step.Control(
                WashLines.STEP_WIPER, Command(Target.Wiper, WiperMode.OFF), "와이퍼 레버를 OFF로 내려 주세요.",
                icon = "wiper",
            ),
            Step.Control(
                WashLines.STEP_RECIRCULATION, Command(Target.Recirculation, true), "내기 순환 버튼을 눌러 주세요.",
                icon = "air",
            ),
            Step.Control(
                WashLines.STEP_DOOR_LOCK, Command(Target.DoorLock, true), "문을 잠가 주세요.",
                restoreOnUndo = false, icon = "lock",
            ),
            Step.Guide(WashLines.STEP_MIRROR, "사이드미러 접기 버튼을 눌러 주세요.", "mirror", WashLines.MANUAL),
            if (atCarWash) Step.Guide("자동 세차기라면 N 유지", WashLines.N_HOLD) else null,
        )
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-12 18:22:00 · 박건준
     * 함수: report
     * 목적: 다 하고 나서 한 번에 할 말을 만든다. 대상마다 단계 결과를 모아
     *       모두 됐으면 "맞췄어요" 쪽, 하나라도 안 됐으면 "직접 해 주세요" 쪽에
     *       넣는다. 이 차에 없는 선루프(지원 안 함)는 말하지 않는다.
     *       앱이 하나라도 했으면 세차한 것으로 보고 오늘을 남기고 예약을 지운다.
     * 인자: outcomes — 단계마다의 결과
     * 반환: 말할 문장 하나
     * 근거: 측정 — Pleos 에뮬레이터에는 선루프가 없어 `Unsupported`가 온다
     *       (docs/api-spike.md 세차 선루프 행). 안내하면 Tab 1 #4 문장과 달라진다
     */
    override fun report(outcomes: List<StepOutcome>): List<String> {
        val results = linkedMapOf<WashPart, MutableList<ControlResult?>>() // 대상별 결과, 단계 순서대로
        val extra = mutableListOf<String>() // 끝에 덧붙일 안내
        outcomes.forEach { (step, result) ->
            val part = partOf(step) // 이 단계의 대상, 따로 말할 안내면 null
            if (part == null) {
                if (step is Step.Guide) extra += step.say
            } else {
                results.getOrPut(part) { mutableListOf() } += result
            }
        }
        val done = mutableListOf<WashPart>() // 앱이 한 대상
        val manual = mutableListOf<WashPart>() // 사람이 해야 하는 대상
        results.forEach { (part, list) ->
            val present = if (part == WashPart.SUNROOF) list.filterNot { it is ControlResult.Unsupported } else list
            when {
                present.isEmpty() -> Unit
                present.all { it?.ok == true } -> done += part
                else -> manual += part
            }
        }
        if (done.isNotEmpty()) {
            memory.recordWash(today()) // → 로직 이동: MemoryPort.recordWash()
            booking.clear()
        }
        return listOf(WashLines.report(done, manual, extra)).filter { it.isNotEmpty() }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: suggestion
     * 목적: 세차장이나 세차를 예약한 주유소에서 P·N으로 멈추면 세차 모드를
     *       권한다. 같은 장소에서 이미 멈춰 있던 상태면 다시 권하지 않는다.
     *       예약한 주유소는 정차마다 다르게 한다(`WashBooking` 단계).
     *       1. 처음 섬(주유 정차) — 말 없이 토스트만 낸다
     *       2. 그 뒤 주유소 안에서 움직이면 남겨 둔다
     *       3. 움직인 뒤 다시 섬(세차 입구) — 다시 묻지 않고 "곧 세차 모드
     *          켤게요."(`question()`이 5초 기다리는 카드를 올린다)
     * 인자: before — 바뀌기 전 차 상태
     *       now — 지금 차 상태
     * 반환: 권할 말. 권하지 않으면 null
     * 근거: 인용 — Tab 1 5.1 장소에 루틴 예약 → 도착 시 권하기. docs/SCREEN.md
     *       6장 5·6단계
     * 미구현: 프로토타입이라 주유소 안 두 번째 정차를 세차 입구로 본다
     *       (`WashBooking` 참고)
     */
    override fun suggestion(before: CarState, now: CarState): String? {
        val ready = now.stopped && (now.gear == Gear.NEUTRAL || now.gear == Gear.PARK) // 세차 준비할 수 있게 섰는지
        val wasReady = before.place == now.place && before.stopped && before.gear == now.gear // 이미 그 상태였는지
        val arrived = ready && !wasReady // 방금 P·N으로 섰는지
        return when (now.place) {
            Place.CAR_WASH -> if (arrived) WashLines.ARRIVED_CAR_WASH else null
            Place.GAS_STATION -> {
                if (!booking.atGasStation) return null
                if (!now.stopped) booking.markMoved()
                when {
                    !arrived -> null
                    !booking.fuelStopDone -> {
                        booking.markFuelStop()
                        asking.notice(WashLines.WAIT_FOR_ENTRANCE) // → 로직 이동: Asking.notice()
                        null
                    }
                    booking.movedAfterFuel -> WashLines.SOON
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * 작성: 2026-11-12 18:22:00 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 함수: question
     * 목적: 세차 모드를 권할 때 올릴 카드. 할 일 칸은 세차 단계 다섯(창문은 한
     *       칸)이고 미러는 사람이 할 칸("직접")으로 미리 보인다.
     *       - 예약한 주유소(세차 입구) — "곧 세차 모드를 켤게요", [지금 켜기]에
     *         `START_WAIT_MS`가 차오르고 지나면 켠다. [취소]
     *       - 세차장 — "세차 모드 켤까요?", [세차 모드 켜기] [괜찮아요]
     * 인자: now — 지금 차 상태. 장소를 본다
     * 반환: 세차 모드 질문
     * 근거: 인용 — docs/SCREEN.md 6장 6단계, 시안 template2.html `card()` wash
     */
    override fun question(now: CarState): Question {
        val cells = listOf( // 할 일 칸 다섯
            QuestionRow("window", WashLines.STEP_WINDOWS),
            QuestionRow("wiper", WashLines.STEP_WIPER),
            QuestionRow("air", WashLines.STEP_RECIRCULATION),
            QuestionRow("lock", WashLines.STEP_DOOR_LOCK),
            QuestionRow("mirror", WashLines.STEP_MIRROR, manual = true, note = WashLines.MANUAL),
        )
        val booked = now.place == Place.GAS_STATION && booking.atGasStation // 예약한 세차 입구인지
        return Question(
            kind = QuestionKind.WASH_MODE,
            title = if (booked) WashLines.SOON_TITLE else WashLines.ASK_MODE,
            detail = WashLines.MODE_DETAIL,
            rows = cells,
            choices = if (booked) {
                listOf(Choice(WashLines.START_NOW, Reply.YES, START_WAIT_MS), Choice(WashLines.CANCEL, Reply.NO))
            } else {
                listOf(Choice(WashLines.ASK_MODE_YES, Reply.YES), Choice(WashLines.ASK_MODE_NO, Reply.NO))
            },
        )
    }

    /**
     * 작성: 2026-11-12 18:22:00 · 박건준
     * 함수: shouldUndo
     * 목적: 세차 모드 중 D로 바꾸면 되돌린다. 멈춰 있어도 되돌려, 출발 전에
     *       사이드미러를 확인하라고 말할 수 있게 한다.
     * 인자: start — 세차 모드를 시작한 순간 상태(P·N). 쓰지 않는다
     *       now — 지금 차 상태
     * 반환: 되돌릴 때면 true
     * 근거: 인용 — docs/SCREEN.md 3장 "대사집에서 바뀌는 것": 해제 시점을
     *       30km/h에서 D로 바꾼 순간으로 앞당긴다
     */
    override fun shouldUndo(start: CarState, now: CarState): Boolean = now.gear == Gear.DRIVE

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: partOf
     * 목적: 단계가 어느 대상인지 알아낸다. 끝에 말할 때 같은 대상 단계(창문
     *       네 개)를 하나로 묶으려고 쓴다.
     * 인자: step — 단계
     * 반환: 대상. 대상 없이 따로 말할 안내(N 유지)면 null
     */
    private fun partOf(step: Step): WashPart? = when (step) {
        is Step.Guide -> if (step.title == WashLines.STEP_MIRROR) WashPart.MIRROR else null
        is Step.Control -> when (val target = step.command.target) {
            is Target.WindowPosition ->
                if (target.window == Window.ROOF_1 || target.window == Window.ROOF_2) WashPart.SUNROOF
                else WashPart.WINDOWS
            Target.Wiper -> WashPart.WIPER
            Target.Recirculation -> WashPart.RECIRCULATION
            Target.DoorLock -> WashPart.DOOR_LOCK
            else -> null
        }
    }
}
