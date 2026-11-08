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
        say(routine.intro)

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
                is Step.Control -> runControl(run, i, step)
                is Step.Guide -> {
                    run.mark(i, StepStatus.GUIDED)
                    say(step.say)
                }
            }
            publish(run)
        }
        run.phase = Phase.ACTIVE
        publish(run)
    }

    private suspend fun runControl(run: ActiveRun, index: Int, step: Step.Control) {
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
            say(step.fallback)
        }
    }

    // ---------------------------------------------------------------- 되돌리기

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
