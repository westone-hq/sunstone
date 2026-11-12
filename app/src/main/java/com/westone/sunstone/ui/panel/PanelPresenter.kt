package com.westone.sunstone.ui.panel

import android.content.Context
import com.westone.sunstone.R
import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.Question
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunView
import com.westone.sunstone.routines.engine.RunnerUi
import com.westone.sunstone.routines.engine.StepStatus
import com.westone.sunstone.routines.engine.StepView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 작성: 2026-11-12 18:44:00 · 박건준
 * 수정: 2026-11-12 20:10:00 · 박건준
 * 클래스: PanelPresenter
 * 목적: 왼쪽 구역에 그릴 값(`ScreenUi`)을 만든다. 판단은 하지 않고 routines가
 *       준 값을 화면 모양으로만 바꾼다.
 *       - `Asking.question`(질문, 값이 바뀌면 알려주는 상태) → 제안 패널.
 *         그 질문의 말이 시작될 때 띄우고, 그 전까지는 앞 화면(토스트 포함)을
 *         둔다. 예·아니오 버튼과 닫기는 대답이 된다
 *       - `RunnerUi.runs`의 세차 모드 → 맞추는 중·준비됨 패널. 단계 상태를
 *         WAITING=TODO, RUNNING=NOW, DONE=DONE, GUIDED=MANUAL로 옮긴다
 *       - `Asking.notices` → 토스트
 *       - 패널이 뜨면 듣는 중 막대(LISTENING, 안내는 질문의 `hint`), 들은
 *         말(`heard()`)은 HEARD로
 *         1초 보인 뒤 내린다. 질문 말이 끝나고 8초 안에 대답이 없으면 막대만
 *         닫고 패널은 남긴다
 *       - 새 패널이 뜨거나 시연 장면이 바뀌면 남은 토스트를 바로 지운다
 * 인자: context — 화면 문구(strings.xml)를 읽는다
 *       scope — 타이머와 구독을 돌린다(화면 스레드)
 *       asking — 질문·토스트
 *       runner — 루틴 실행기 화면 상태
 *       speaking — 음성 안내가 말하는 중인지
 *       starts — 말 하나가 시작될 때 그 말에 붙은 질문(`AndroidSpeaker.starts`)
 *       onAnswer — 패널 예·아니오 버튼(`AppGraph.answer()`)
 *       onFinish — 준비됨 패널 [지금 끝내기](`RoutineRunner.undo()`)
 * 근거: 인용 — docs/SCREEN.md 1장 상태 흐름, 5장 대답 기다리는 시간 8초.
 *       2026-11-12 박건준 검토: 패널은 그 패널의 말이 시작될 때 띄운다
 */
class PanelPresenter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val asking: Asking,
    runner: StateFlow<RunnerUi>,
    private val speaking: StateFlow<Boolean>,
    private val starts: SharedFlow<Any?>,
    private val onAnswer: (Boolean) -> Unit,
    private val onFinish: (RoutineId) -> Unit,
) {

    private companion object {
        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 변수: LISTEN_TIMEOUT_MS
         * 목적: 질문 말이 끝난 뒤 대답을 기다리는 시간(밀리초). 지나면 듣는 중
         *       막대만 닫는다.
         * 근거: 인용 — docs/SCREEN.md 5장 대답 기다리는 시간 8초
         */
        const val LISTEN_TIMEOUT_MS = 8_000L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 변수: SPEAK_START_MS
         * 목적: 질문을 올린 뒤 질문 말이 시작되기를 기다리는 최대 시간(밀리초).
         *       길안내 대화는 패널을 먼저 올리고 말을 이어 넣는다.
         * 근거: 미정 — 말이 줄에 들어오기까지 한 프레임 안팎이라 넉넉히 잡았다
         */
        const val SPEAK_START_MS = 1_000L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 수정: 2026-11-12 19:44:00 · 박건준
         * 변수: HEARD_MS
         * 목적: 들은 말을 막대에 보여 주는 시간(밀리초). 지나면 막대를 내린다.
         * 근거: 인용 — 2026-11-12 박건준 검토: 말을 알아듣고 1초 뒤 내린다
         *       (실행 중에도 들은 말 막대가 남아 있었다)
         */
        const val HEARD_MS = 1_000L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 수정: 2026-11-12 20:20:00 · 박건준
         * 변수: TOAST_MS
         * 목적: 토스트를 보여 주는 가장 짧은 시간(밀리초). 함께 나온 말이 더
         *       길면 말이 끝날 때까지 둔다.
         * 근거: 인용 — 시안 template.html `toast()` 3200
         */
        const val TOAST_MS = 3_200L

        /**
         * 작성: 2026-11-12 20:10:00 · 박건준
         * 변수: SHOW_WAIT_MS
         * 목적: 질문의 말이 시작되기를 기다리는 최대 시간(밀리초). 지나면 말이
         *       없어도 패널을 띄운다(말이 줄에서 빠졌을 때).
         * 근거: 미정 — 앞 말 두세 개(문장마다 3~6초)가 끝나기를 넉넉히 기다린다
         */
        const val SHOW_WAIT_MS = 20_000L
    }

    /** 화면에 띄운 질문. 질문의 말이 시작되면 채우고, 대답이 오면 비운다. 없으면 null */
    private val shown = MutableStateFlow<Question?>(null)

    /** 질문의 말이 시작되기를 기다리는 작업. 없으면 null */
    private var showJob: Job? = null

    /** 듣는 중 막대. 질문·들은 말·마이크 칸이 바꾼다 */
    private val listen = MutableStateFlow(ListenUi(ListenState.OFF))

    /** 지금 토스트 문장. 없으면 null */
    private val toast = MutableStateFlow<String?>(null)

    /** 닫기로 내린 진행 패널의 실행 시작 시각(밀리초). 그 실행 동안은 다시 띄우지 않는다. 없으면 null */
    private val hiddenRun = MutableStateFlow<Long?>(null)

    /** 듣는 중 막대를 닫거나 바꾸는 타이머. 없으면 null */
    private var listenJob: Job? = null

    /** 토스트를 내리는 타이머. 없으면 null */
    private var toastJob: Job? = null

    /** 지금 진행 패널이 보여 주는 실행. `panelOf()`가 채우고 닫기(`HIDE`)가 쓴다. 없으면 null */
    private var shownRun: RunView? = null

    /** 왼쪽 구역에 그릴 것 전부(값이 바뀌면 알려주는 상태) */
    val ui: StateFlow<ScreenUi> = combine(shown, runner, listen, toast, hiddenRun) { q, r, l, t, h ->
        ScreenUi(panelOf(q, r, h), l, t)
    }.stateIn(scope, SharingStarted.Eagerly, ScreenUi(null, ListenUi(ListenState.OFF), null))

    init {
        scope.launch { asking.question.collect { onQuestion(it) } }
        scope.launch { asking.notices.collect { showToast(it) } }
        // 새 패널(제목이 바뀜)이 뜨면 앞 장면 토스트가 남지 않게 지운다
        scope.launch { ui.map { it.panel?.title }.distinctUntilChanged().collect { if (it != null) clearToast() } }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 19:44:00 · 박건준
     * 함수: heard
     * 목적: 운전자가 한 말을 막대에 HEARD로 보여 준다. 음성·시연 버튼·adb
     *       `say`가 모두 `AppGraph.hear()`를 거쳐 여기로 온다. `HEARD_MS` 뒤
     *       아직 질문이 남아 있으면(못 알아들음) 다시 듣고, 없으면 막대를 닫는다.
     * 인자: text — 들은 말
     *       hint — 들은 말 아래에 보일, 앱이 이어서 하는 일("세차 모드를
     *       켤게요"). 없으면 null(줄을 비운다)
     * 근거: 인용 — 시안 sunstone-ui.html 들음 상태 `.vh`
     */
    fun heard(text: String, hint: String?) {
        listenJob?.cancel()
        listen.value = ListenUi(ListenState.HEARD, context.getString(R.string.listen_heard, text), hint)
        listenJob = scope.launch {
            delay(HEARD_MS)
            val q = shown.value // 아직 떠 있는 질문(못 알아들었을 때), 없으면 null
            if (q != null) startListening(q.hint) else listen.value = ListenUi(ListenState.OFF)
        }
    }

    /**
     * 작성: 2026-11-12 19:44:00 · 박건준
     * 함수: clearToast
     * 목적: 남은 토스트를 바로 지운다. 새 패널이 뜰 때와 시연 장면이 바뀔 때
     *       부른다.
     */
    fun clearToast() {
        toastJob?.cancel()
        toast.value = null
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: tapMic
     * 목적: 마이크 칸을 눌렀을 때 듣는 중 막대를 띄운다. 8초 동안 말이 없으면
     *       닫는다.
     * 미구현: 프로토타입이라 마이크 칸은 막대만 띄우고 음성 인식은 하지 않는다.
     *       에뮬레이터에 마이크 입력이 없어 말은 시연 버튼·adb `say`로 넣는다
     *       (docs/api-spike.md 음성 인식 행)
     */
    fun tapMic() = startListening(null)

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: press
     * 목적: 패널 버튼이나 닫기 칸을 처리한다.
     * 인자: action — 누른 것이 하는 일
     */
    fun press(action: PanelAction) {
        when (action) {
            PanelAction.YES -> onAnswer(true) // → 로직 이동: AppGraph.answer()
            PanelAction.NO -> onAnswer(false) // → 로직 이동: AppGraph.answer()
            PanelAction.FINISH -> onFinish(RoutineId.WASH) // → 로직 이동: RoutineRunner.undo()
            PanelAction.HIDE -> hiddenRun.value = shownRun?.startedAtMs
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 20:10:00 · 박건준
     * 함수: onQuestion
     * 목적: 질문이 바뀌면 패널과 막대를 맞춘다.
     *       - 새 질문 — 그 질문의 말이 시작될 때(`starts`) 패널을 띄우고 듣는 중
     *         막대를 켠다. 그 전까지는 앞 화면을 그대로 둔다
     *       - 질문이 없어짐(대답함) — 패널을 내리고 듣는 중 막대를 닫는다
     *       들은 말을 보여 주는 중이면 막대는 `heard()`의 타이머가 이어서 정한다.
     * 인자: q — 지금 질문. 없으면 null
     */
    private fun onQuestion(q: Question?) {
        showJob?.cancel()
        if (q == null) {
            shown.value = null
            if (listen.value.state == ListenState.LISTENING) {
                listenJob?.cancel()
                listen.value = ListenUi(ListenState.OFF)
            }
            return
        }
        showJob = scope.launch {
            withTimeoutOrNull(SHOW_WAIT_MS) { starts.first { it === q } }
            shown.value = q
            if (listen.value.state != ListenState.HEARD) startListening(q.hint)
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 20:10:00 · 박건준
     * 함수: startListening
     * 목적: 듣는 중 막대를 띄우고, 지금 말이 끝난 뒤 `LISTEN_TIMEOUT_MS`가
     *       지나면 막대만 닫는다.
     * 인자: hint — 막대 아래 줄 대답 예. null이면 기본 안내("“응” 또는 “괜찮아”")
     */
    private fun startListening(hint: String?) {
        listenJob?.cancel()
        listen.value = ListenUi(
            ListenState.LISTENING,
            context.getString(R.string.listen_on),
            hint ?: context.getString(R.string.listen_hint),
        )
        listenJob = scope.launch {
            withTimeoutOrNull(SPEAK_START_MS) { speaking.first { it } }
            speaking.first { !it }
            delay(LISTEN_TIMEOUT_MS)
            listen.value = ListenUi(ListenState.OFF)
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 20:20:00 · 박건준
     * 함수: showToast
     * 목적: 토스트 문장을 보여 준다. 새 문장이 오면 바로 바꾼다.
     *       - 함께 나온 말이 있으면(말을 줄에 세운 뒤 토스트가 온다) 그 말이
     *         끝날 때까지 두고, 말이 `TOAST_MS`보다 짧으면 `TOAST_MS`를 채운다
     *       - 말 없이 온 토스트(①-8 주유 정차)는 `TOAST_MS`
     *       새 패널이 뜨면 `clearToast()`가 먼저 지운다.
     * 인자: text — 토스트 문장
     * 근거: 인용 — 2026-11-12 박건준 검토: 경유지 말(3초 남짓)이 토스트보다
     *       길어 다음 패널 전까지 빈 화면이 생겼다(r4-S3)
     */
    private fun showToast(text: String) {
        toastJob?.cancel()
        toast.value = text
        toastJob = scope.launch {
            delay(TOAST_MS)
            speaking.first { !it }
            toast.value = null
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: panelOf
     * 목적: 지금 보일 패널을 고른다. 질문이 먼저이고, 없으면 세차 모드 진행.
     * 인자: q — 지금 질문. 없으면 null
     *       r — 루틴 실행기 화면 상태
     *       hidden — 닫기로 내린 실행의 시작 시각. 없으면 null
     * 반환: 패널. 보일 것이 없으면 null
     */
    private fun panelOf(q: Question?, r: RunnerUi, hidden: Long?): PanelUi? {
        if (q != null) return questionPanel(q)
        val run = r.runs[RoutineId.WASH]?.takeIf { it.startedAtMs != hidden } // 보일 세차 모드 실행
        shownRun = run?.takeIf { it.phase == Phase.RUNNING || it.phase == Phase.ACTIVE }
        return shownRun?.let(::runPanel)
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: questionPanel
     * 목적: 질문을 제안 패널로 바꾼다. 줄은 모두 아직 안 함(회색)이고, 사람이
     *       할 줄도 오른쪽 안내를 미리 보인다.
     * 인자: q — 질문
     * 반환: 패널
     * 근거: 인용 — docs/SCREEN.md 1장 "직접 ~ 주세요"는 제안할 때부터 미리
     *       보여 준다
     */
    private fun questionPanel(q: Question) = PanelUi(
        title = q.title,
        detail = q.detail,
        progress = null,
        rows = q.rows.map { RowUi(it.icon, it.text, RowState.TODO, it.note) },
        actions = listOf(ActionUi(q.yesLabel, true, PanelAction.YES), ActionUi(q.noLabel, false, PanelAction.NO)),
        close = PanelAction.NO,
    )

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: runPanel
     * 목적: 루틴 실행을 맞추는 중·준비됨 패널로 바꾼다. 같은 이름 단계(창문
     *       네 개)는 한 줄로 묶는다.
     *       - 맞추는 중 — "N개 중 M개 맞췄어요." + 진행선, 버튼 없음
     *       - 준비됨 — "출발하면 원래대로 돌려놓을게요." + [지금 끝내기]
     * 인자: run — 실행 기록(실행 중이거나 켜져 있음)
     * 반환: 패널
     * 근거: 인용 — 시안 template.html `noti()`의 제목·설명·버튼
     */
    private fun runPanel(run: RunView): PanelUi {
        val rows = mergeSteps(run.steps) // 한 줄씩 묶은 항목
        val total = rows.count { !it.second } // 앱이 맞출 줄 수
        val done = rows.count { it.first.state == RowState.DONE } // 맞춘 줄 수
        val ready = run.phase == Phase.ACTIVE // 다 맞추고 켜져 있는지
        return PanelUi(
            title = context.getString(if (ready) R.string.panel_ready else R.string.panel_running, run.title),
            detail = if (ready) context.getString(R.string.panel_ready_detail)
            else context.getString(R.string.panel_progress, total, done),
            progress = Progress(done, total),
            rows = rows.map { it.first },
            actions = if (ready) listOf(ActionUi(context.getString(R.string.panel_finish), true, PanelAction.FINISH))
            else emptyList(),
            close = PanelAction.HIDE,
        )
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 19:44:00 · 박건준
     * 함수: mergeSteps
     * 목적: 이어지는 같은 이름 단계를 한 줄로 묶고 상태를 옮긴다.
     *       - 하나라도 RUNNING → NOW
     *       - 모두 DONE → DONE
     *       - 하나라도 GUIDED → MANUAL(오른쪽에 사람이 할 일)
     *       - 모두 WAITING·SKIPPED → TODO
     *       - 일부만 DONE(단계 사이) → NOW
     *       DONE 줄은 끝난 꼴 글자(`StepView.done`, "창문 닫음")로 바꾼다
     * 인자: steps — 실행 단계들, 실행 순서대로
     * 반환: (줄, 사람이 할 줄인지) 목록
     */
    private fun mergeSteps(steps: List<StepView>): List<Pair<RowUi, Boolean>> {
        val groups = mutableListOf<MutableList<StepView>>() // 같은 이름끼리 이은 묶음
        steps.forEach { s ->
            if (groups.lastOrNull()?.first()?.title == s.title) groups.last() += s else groups += mutableListOf(s)
        }
        return groups.map { g ->
            val state = when { // 묶음 상태
                g.any { it.status == StepStatus.RUNNING } -> RowState.NOW
                g.all { it.status == StepStatus.DONE } -> RowState.DONE
                g.any { it.status == StepStatus.GUIDED } -> RowState.MANUAL
                g.all { it.status == StepStatus.WAITING || it.status == StepStatus.SKIPPED } -> RowState.TODO
                else -> RowState.NOW
            }
            val note = if (state == RowState.MANUAL) g.firstNotNullOfOrNull { it.note } else null // 사람이 할 일
            val text = if (state == RowState.DONE) g.first().done ?: g.first().title else g.first().title // 줄 글자
            RowUi(g.first().icon, text, state, note) to g.first().guide
        }
    }
}
