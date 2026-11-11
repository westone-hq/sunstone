package com.westone.sunstone.routines.library

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
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.WashLines
import com.westone.sunstone.routines.speech.WashPart
import com.westone.sunstone.routines.speech.withObject

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-11 14:28:46 · 박희정
 * 클래스: WashRoutine
 * 목적: 세차 모드. 현대 세차 모드가 하는 일(창문·선루프 닫기, 와이퍼 끄기,
 *       내기 순환, 문 잠금, 미러 접기)을 앱이 한 단계씩 직접 한다.
 *       - 주유소·세차장에 도착해 멈추면 먼저 권한다
 *       - 다 하고 나서 한 번에 말한다(Tab 1 시나리오 ① #4)
 *       - 미러 접기와 N 유지는 API가 없어서 말로 안내한다. N 유지는 자동
 *         세차기가 있는 세차장에서만 말한다
 *       - 세차장·주유소를 벗어나 달리기 시작하면 와이퍼·내기 순환을
 *         원래대로 돌린다. 창문은 다시 열지 않고 문 잠금은 풀지 않는다
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #3~#5
 */
class WashRoutine : Routine {

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
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 변수: LEAVE_SPEED_KMH
         * 목적: 세차장을 떠났다고 보는 속도(km/h). 이 속도 이상이면 되돌린다.
         * 근거: 미정 — 세차장 안 이동(5km/h 안팎)과 구분하려고 임시로 정했다
         */
        const val LEAVE_SPEED_KMH = 10f

        /**
         * 작성: 2026-11-11 14:28:46 · 박희정
         * 변수: MIRROR_TITLE
         * 목적: 사이드미러 안내 단계 이름. 끝에 말할 때 이 단계를 사이드미러로
         *       알아본다.
         */
        const val MIRROR_TITLE = "사이드미러 접기"
    }

    override val id = RoutineId.WASH
    override val title = "세차 모드"
    override val intro = "세차 준비할게요."
    override val reportsAtEnd = true
    override val afterUndo = listOf(WashLines.FINISHED)

    override fun blockedReason(state: CarState): String? = when {
        !state.stopped -> "차가 멈춘 뒤에 할게요."
        state.gear != Gear.PARK && state.gear != Gear.NEUTRAL -> "P나 N에 두시면 시작할게요."
        else -> null
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 14:28:46 · 박희정
     * 함수: plan
     * 목적: 세차 단계를 만든다. 창문(선루프 포함) → 와이퍼 → 내기 순환 →
     *       문 잠금 → 사이드미러 안내 순서. 세차장에 있으면 N 유지 안내를
     *       더한다.
     * 인자: request — 실행 요청. 쓰지 않는다
     *       car — 지금 장소를 보려고 읽는다
     * 반환: 실행할 단계들
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #4는 주유소 장면이라 N 유지를 말하지 않는다
     */
    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> {
        val windows = Window.entries.map { w ->
            Step.Control(
                title = "${w.label} 닫기",
                command = Command(Target.WindowPosition(w), CLOSED),
                fallback = "${withObject(w.label)} 닫아 주세요.",
                restoreOnUndo = false,
            )
        }
        val atCarWash = car.state.value.place == Place.CAR_WASH // 자동 세차기가 있는 곳인지
        return windows + listOfNotNull(
            Step.Control("와이퍼 끄기", Command(Target.Wiper, WiperMode.OFF), "와이퍼 레버를 OFF로 내려 주세요."),
            Step.Control("내기 순환", Command(Target.Recirculation, true), "내기 순환 버튼을 눌러 주세요."),
            Step.Control("문 잠금", Command(Target.DoorLock, true), "문을 잠가 주세요.", restoreOnUndo = false),
            Step.Guide(MIRROR_TITLE, "사이드미러 접기 버튼을 눌러 주세요."),
            if (atCarWash) Step.Guide("자동 세차기라면 N 유지", WashLines.N_HOLD) else null,
        )
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: report
     * 목적: 다 하고 나서 한 번에 할 말을 만든다. 대상마다 단계 결과를 모아
     *       모두 됐으면 "했어요" 쪽, 하나라도 안 됐으면 "직접 해 주세요" 쪽에
     *       넣는다. 이 차에 없는 선루프(지원 안 함)는 말하지 않는다.
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
        return listOf(WashLines.report(done, manual, extra)).filter { it.isNotEmpty() }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 14:28:46 · 박희정
     * 함수: suggestion
     * 목적: 주유소나 세차장에 도착해 P·N으로 멈추면 세차 모드를 권한다.
     *       같은 장소에서 이미 멈춰 있던 상태면 다시 권하지 않는다.
     * 인자: before — 바뀌기 전 차 상태
     *       now — 지금 차 상태
     * 반환: 권할 말. 권하지 않으면 null
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #3
     */
    override fun suggestion(before: CarState, now: CarState): String? {
        val line = when (now.place) { // 장소별 권하는 말
            Place.GAS_STATION -> WashLines.ARRIVED_GAS_STATION
            Place.CAR_WASH -> WashLines.ARRIVED_CAR_WASH
            else -> return null
        }
        val ready = now.stopped && (now.gear == Gear.NEUTRAL || now.gear == Gear.PARK) // 세차 준비할 수 있게 섰는지
        val wasReady = before.place == now.place && before.stopped && before.gear == now.gear // 이미 그 상태였는지
        return if (ready && !wasReady) line else null
    }

    override fun shouldUndo(start: CarState, now: CarState): Boolean =
        now.gear == Gear.DRIVE && now.speedKmh >= LEAVE_SPEED_KMH &&
            now.place != Place.CAR_WASH && now.place != Place.GAS_STATION

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: partOf
     * 목적: 단계가 어느 대상인지 알아낸다. 끝에 말할 때 같은 대상 단계(창문
     *       네 개)를 하나로 묶으려고 쓴다.
     * 인자: step — 단계
     * 반환: 대상. 대상 없이 따로 말할 안내(N 유지)면 null
     */
    private fun partOf(step: Step): WashPart? = when (step) {
        is Step.Guide -> if (step.title == MIRROR_TITLE) WashPart.MIRROR else null
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
