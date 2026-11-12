package com.westone.sunstone.routines.engine

import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.ControlResult
import com.westone.sunstone.routines.port.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StepStatus { WAITING, RUNNING, DONE, GUIDED, SKIPPED }

enum class Phase { RUNNING, ACTIVE, UNDOING, FINISHED }

data class StepView(val title: String, val status: StepStatus, val note: String? = null)

data class RunView(
    val id: RoutineId,
    val title: String,
    val phase: Phase,
    val steps: List<StepView>,
    val startedAtMs: Long,
    val source: Source,
)

data class Suggestion(val id: RoutineId, val message: String)

data class RunnerUi(
    val runs: Map<RoutineId, RunView> = emptyMap(),
    val suggestion: Suggestion? = null,
    val lastMessage: String? = null,
)

/**
 * 모든 루틴을 같은 방식으로 실행한다.
 *
 * 1. 제어 단계마다 **원래 값 저장 → 실행 → 결과 확인**.
 * 2. 실패하거나 지원하지 않으면 그 단계는 음성 안내로 바꾸고 계속 간다. 루틴은 멈추지 않는다.
 * 3. 되돌리기는 "기본값"이 아니라 **저장해 둔 원래 값**으로 한다.
 * 4. 차 상태가 바뀔 때마다 권하기·지켜보기·자동 되돌리기를 확인한다.
 */
class RoutineRunner(
    private val car: CarPort,
    routines: List<Routine>,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val suggestCooldownMs: Long = 10 * 60_000L,
) {
    private companion object {
        /**
         * 작성: 2026-11-11 14:28:46 · 박희정
         * 변수: DECLINED
         * 목적: 권하기를 거절했을 때 하는 말.
         * 근거: 미정 — Tab 1 대사집에 거절 문장이 없어 짧게 정했다
         */
        const val DECLINED = "알겠어요."
    }

    private val routines = routines.associateBy { it.id }
    private val mutex = Mutex()
    private val active = mutableMapOf<RoutineId, ActiveRun>()
    private val lastSuggestedAt = mutableMapOf<RoutineId, Long>()

    private val _ui = MutableStateFlow(RunnerUi())
    val ui: StateFlow<RunnerUi> = _ui.asStateFlow()

    val available: List<Routine> get() = routines.values.toList()

    /** 차 상태를 지켜보기 시작한다. 앱이 시작할 때 한 번 부른다. */
    fun start(): Job = scope.launch {
        var before = car.state.value
        car.state.collect { now ->
            onCarChanged(before, now)
            before = now
        }
    }

    fun request(request: RunRequest): Job = scope.launch { mutex.withLock { run(request) } }

    fun undo(id: RoutineId): Job = scope.launch { mutex.withLock { undoLocked(id) } }

    fun acceptSuggestion(): Job? {
        val s = _ui.value.suggestion ?: return null
        _ui.update { it.copy(suggestion = null) }
        return request(RunRequest(s.id, source = Source.DETECTED))
    }

    fun dismissSuggestion() {
        _ui.update { it.copy(suggestion = null) }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: forgetSuggestions
     * 목적: 권하기 기록(같은 루틴 10분에 한 번)과 떠 있는 권하기를 지운다.
     *       새 주행·시연 장면을 처음부터 다시 시작할 때 부른다.
     */
    fun forgetSuggestions() {
        lastSuggestedAt.clear()
        _ui.update { it.copy(suggestion = null) }
    }

    /**
     * 작성: 2026-11-11 19:10:34 · 박희정
     * 함수: forgetFinished
     * 목적: 끝난 실행 기록을 화면에서 지운다. 켜져 있거나 실행·되돌리는 중인
     *       기록은 남긴다. 시연을 처음부터 다시 시작할 때 부른다.
     */
    fun forgetFinished() {
        _ui.update { ui -> ui.copy(runs = ui.runs.filterValues { it.phase != Phase.FINISHED }) }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: activeIds
     * 목적: 지금 켜져 있는(되돌릴 수 있는) 루틴들.
     * 반환: 루틴 ID 목록
     */
    fun activeIds(): List<RoutineId> = _ui.value.runs.values.filter { it.phase == Phase.ACTIVE }.map { it.id }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: answer
     * 목적: 권하기에 말로 한 대답("응", "괜찮아")을 처리한다. "네"면 권한
     *       루틴을 실행하고, "아니요"면 권하기를 지우고 알았다고 말한다.
     * 인자: yes — 받아들이면 true, 거절이면 false
     * 반환: 권하기가 떠 있어서 대답으로 처리했으면 true. 권하기가 없으면
     *       아무것도 하지 않고 false(부르는 쪽이 "못 알아들었어요"로 처리)
     */
    fun answer(yes: Boolean): Boolean {
        if (_ui.value.suggestion == null) return false
        if (yes) {
            acceptSuggestion()
        } else {
            dismissSuggestion()
            say(DECLINED)
        }
        return true
    }

    // ---------------------------------------------------------------- 실행

    private suspend fun run(request: RunRequest) {
        val routine = routines[request.id] ?: return
        val current = active[request.id]
        val busy = current != null && when (current.phase) {
            Phase.RUNNING, Phase.UNDOING -> true
            Phase.ACTIVE -> routine.staysActive
            Phase.FINISHED -> false
        }
        if (busy) {
            say("이미 ${routine.title} 중이에요.")
            return
        }
        val start = car.state.value
        routine.blockedReason(start)?.let { say(it); return }
        if (_ui.value.suggestion?.id == request.id) _ui.update { it.copy(suggestion = null) }

        val steps = routine.plan(request, car)
        val run = ActiveRun(routine, start, steps, clock(), request.source)
        active[request.id] = run
        publish(run)
        val atEnd = routine.reportsAtEnd // 끝에 한 번에 말하는 루틴인지
        if (!atEnd) say(routine.intro)
        val outcomes = mutableListOf<StepOutcome>() // 단계마다의 결과, `Routine.report()`에 넘긴다

        for (i in steps.indices) {
            routine.blockedReason(car.state.value)?.let { reason ->
                run.skipFrom(i)
                publish(run)
                say("멈출게요. $reason")
                run.phase = Phase.ACTIVE
                publish(run)
                return
            }
            run.mark(i, StepStatus.RUNNING)
            publish(run)
            when (val step = steps[i]) {
                is Step.Control -> outcomes += StepOutcome(step, runControl(run, i, step, sayFallback = !atEnd))
                is Step.Guide -> {
                    run.mark(i, StepStatus.GUIDED)
                    if (!atEnd) say(step.say)
                    outcomes += StepOutcome(step, null)
                }
            }
            publish(run)
        }
        run.phase = Phase.ACTIVE
        publish(run)
        if (atEnd) routine.report(outcomes).forEach(::say) // → 로직 이동: Routine.report()
    }

    /**
     * 수정: 2026-11-11 14:28:46 · 박희정
     * 함수: runControl
     * 목적: 제어 단계 하나를 처리한다. 원래 값을 읽어 두고, 실행한 뒤,
     *       실패하면 그 단계를 안내로 표시한다.
     * 인자: run — 지금 실행 중인 루틴의 기록
     *       index — 처리할 단계 번호 (0부터)
     *       step — 처리할 제어 단계
     *       sayFallback — 실패했을 때 [Step.Control.fallback]을 바로 말할지.
     *       끝에 한 번에 말하는 루틴은 false
     * 반환: 차에 보낸 결과. 성공하면 되돌리기 목록에 원래 값을 더한다
     */
    private suspend fun runControl(
        run: ActiveRun,
        index: Int,
        step: Step.Control,
        sayFallback: Boolean,
    ): ControlResult {
        val before = runCatching { car.read(step.command.target) }.getOrNull()
        val result = runCatching { car.apply(step.command) }
            .getOrElse { ControlResult.Failed(it.message ?: it.javaClass.simpleName) }
        if (result.ok) {
            if (step.restoreOnUndo && before != null && before != step.command.value) {
                run.saved += Command(step.command.target, before)
            }
            run.mark(index, StepStatus.DONE)
        } else {
            val why = when (result) {
                is ControlResult.Unsupported -> result.reason
                is ControlResult.Failed -> result.reason
                else -> null
            }
            run.mark(index, StepStatus.GUIDED, why)
            if (sayFallback) say(step.fallback)
        }
        return result
    }

    // ---------------------------------------------------------------- 되돌리기

    /**
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: undoLocked
     * 목적: 켜진 루틴을 저장한 원래 값으로 되돌리고 끝낸다. 끝나면 그 루틴의
     *       권하기 기록(10분에 한 번)도 지운다. 한 번 쓰고 끝난 뒤 다시 생긴
     *       상황(아이가 내렸다가 다시 탐)은 새로 권해야 한다.
     * 인자: id — 되돌릴 루틴
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #4 뒤 #5 "차일드락 이어서 할까요?"
     */
    private suspend fun undoLocked(id: RoutineId) {
        val run = active[id] ?: return
        if (run.phase != Phase.ACTIVE) return
        run.phase = Phase.UNDOING
        publish(run)
        for (command in run.saved.asReversed()) {
            runCatching { car.apply(command) }
        }
        run.routine.afterUndo.forEach(::say)
        run.phase = Phase.FINISHED
        lastSuggestedAt.remove(id)
        publish(run)
    }

    // ---------------------------------------------------------------- 상황 보기

    private fun onCarChanged(before: CarState, now: CarState) {
        val running = active.values.filter { it.phase == Phase.ACTIVE }
        running.forEach { run ->
            run.routine.watch(before, now)?.let(::say)
            if (run.routine.shouldUndo(run.start, now)) undo(run.routine.id)
        }
        if (_ui.value.suggestion != null) return
        val busy = active.filterValues { it.phase != Phase.FINISHED }.keys
        for (routine in routines.values) {
            if (routine.id in busy) continue
            val last = lastSuggestedAt[routine.id]
            if (last != null && clock() - last < suggestCooldownMs) continue
            val message = routine.suggestion(before, now) ?: continue
            lastSuggestedAt[routine.id] = clock()
            _ui.update { it.copy(suggestion = Suggestion(routine.id, message)) }
            say(message)
            return
        }
    }

    // ---------------------------------------------------------------- 공용

    private fun say(text: String) {
        _ui.update { it.copy(lastMessage = text) }
        speaker.say(text)
    }

    private fun publish(run: ActiveRun) {
        _ui.update { it.copy(runs = it.runs + (run.routine.id to run.view())) }
    }

    private class ActiveRun(
        val routine: Routine,
        val start: CarState,
        val steps: List<Step>,
        val startedAtMs: Long,
        val source: Source,
    ) {
        var phase: Phase = Phase.RUNNING
        val saved = mutableListOf<Command>()
        private val status = MutableList(steps.size) { StepStatus.WAITING }
        private val notes = MutableList<String?>(steps.size) { null }

        fun mark(i: Int, s: StepStatus, note: String? = null) {
            status[i] = s
            if (note != null) notes[i] = note
        }

        fun skipFrom(i: Int) {
            for (k in i until steps.size) status[k] = StepStatus.SKIPPED
        }

        fun view() = RunView(
            id = routine.id,
            title = routine.title,
            phase = phase,
            steps = steps.mapIndexed { i, s -> StepView(s.title, status[i], notes[i]) },
            startedAtMs = startedAtMs,
            source = source,
        )
    }
}
