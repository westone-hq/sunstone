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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 작성: 2026-11-12 18:44:00 · 박건준
 * 클래스: PanelPresenter
 * 목적: 왼쪽 구역에 그릴 값(`ScreenUi`)을 만든다. 판단은 하지 않고 routines가
 *       준 값을 화면 모양으로만 바꾼다.
 *       - `Asking.question`(질문, 값이 바뀌면 알려주는 상태) → 제안 패널.
 *         예·아니오 버튼과 닫기는 대답이 된다
 *       - `RunnerUi.runs`의 세차 모드 → 맞추는 중·준비됨 패널. 단계 상태를
 *         WAITING=TODO, RUNNING=NOW, DONE=DONE, GUIDED=MANUAL로 옮긴다
 *       - `Asking.notices` → 토스트
 *       - 질문이 뜨면 듣는 중 막대(LISTENING), 들은 말(`heard()`)은 HEARD.
 *         질문 말이 끝나고 8초 안에 대답이 없으면 막대만 닫고 패널은 남긴다
 * 인자: context — 화면 문구(strings.xml)를 읽는다
 *       scope — 타이머와 구독을 돌린다(화면 스레드)
 *       asking — 질문·토스트
 *       runner — 루틴 실행기 화면 상태
 *       speaking — 음성 안내가 말하는 중인지
 *       onAnswer — 패널 예·아니오 버튼(`AppGraph.answer()`)
 *       onFinish — 준비됨 패널 [지금 끝내기](`RoutineRunner.undo()`)
 * 근거: 인용 — docs/SCREEN.md 1장 상태 흐름, 5장 대답 기다리는 시간 8초
 */
class PanelPresenter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val asking: Asking,
    runner: StateFlow<RunnerUi>,
    private val speaking: StateFlow<Boolean>,
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
         * 변수: HEARD_MS
         * 목적: 들은 말을 막대에 보여 주는 시간(밀리초).
         * 근거: 미정 — 시안(sunstone-ui.html)은 들은 뒤 약 2.2초 뒤 다음
         *       상태로 넘어간다. 시연 영상에서 읽을 수 있게 조금 길게 잡았다
         */
        const val HEARD_MS = 2_500L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 변수: TOAST_MS
         * 목적: 토스트를 보여 주는 시간(밀리초).
         * 근거: 인용 — 시안 template.html `toast()` 3200
         */
        const val TOAST_MS = 3_200L
    }

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
    val ui: StateFlow<ScreenUi> = combine(asking.question, runner, listen, toast, hiddenRun) { q, r, l, t, h ->
        ScreenUi(panelOf(q, r, h), l, t)
    }.stateIn(scope, SharingStarted.Eagerly, ScreenUi(null, ListenUi(ListenState.OFF), null))

    init {
        scope.launch { asking.question.collect { onQuestion(it) } }
        scope.launch { asking.notices.collect { showToast(it) } }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: heard
     * 목적: 운전자가 한 말을 막대에 HEARD로 보여 준다. 음성·시연 버튼·adb
     *       `say`가 모두 `AppGraph.hear()`를 거쳐 여기로 온다. 보여 준 뒤 아직
     *       질문이 있으면 다시 듣고, 없으면 막대를 닫는다.
     * 인자: text — 들은 말
     */
    fun heard(text: String) {
        listenJob?.cancel()
        listen.value = ListenUi(ListenState.HEARD, context.getString(R.string.listen_heard, text))
        listenJob = scope.launch {
            delay(HEARD_MS)
            if (asking.question.value != null) startListening() else listen.value = ListenUi(ListenState.OFF)
        }
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
    fun tapMic() = startListening()

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
     * 함수: onQuestion
     * 목적: 질문이 바뀌면 막대를 맞춘다. 새 질문이면 듣는 중으로, 질문이
     *       없어지면 듣는 중 막대를 닫는다. 들은 말을 보여 주는 중이면
     *       `heard()`의 타이머가 이어서 정한다.
     * 인자: q — 지금 질문. 없으면 null
     */
    private fun onQuestion(q: Question?) {
        if (listen.value.state == ListenState.HEARD) return
        if (q != null) {
            startListening()
        } else if (listen.value.state == ListenState.LISTENING) {
            listenJob?.cancel()
            listen.value = ListenUi(ListenState.OFF)
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 함수: startListening
     * 목적: 듣는 중 막대를 띄우고, 지금 말이 끝난 뒤 `LISTEN_TIMEOUT_MS`가
     *       지나면 막대만 닫는다.
     */
    private fun startListening() {
        listenJob?.cancel()
        listen.value = ListenUi(
            ListenState.LISTENING,
            context.getString(R.string.listen_on),
            context.getString(R.string.listen_hint),
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
     * 함수: showToast
     * 목적: 토스트 문장을 `TOAST_MS` 동안 보여 준다. 새 문장이 오면 바로 바꾼다.
     * 인자: text — 토스트 문장
     */
    private fun showToast(text: String) {
        toastJob?.cancel()
        toast.value = text
        toastJob = scope.launch {
            delay(TOAST_MS)
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
     * 함수: mergeSteps
     * 목적: 이어지는 같은 이름 단계를 한 줄로 묶고 상태를 옮긴다.
     *       - 하나라도 RUNNING → NOW
     *       - 모두 DONE → DONE
     *       - 하나라도 GUIDED → MANUAL(오른쪽에 사람이 할 일)
     *       - 모두 WAITING·SKIPPED → TODO
     *       - 일부만 DONE(단계 사이) → NOW
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
            RowUi(g.first().icon, g.first().title, state, note) to g.first().guide
        }
    }
}
