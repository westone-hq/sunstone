package com.westone.sunstone.ui.panel

import android.content.Context
import android.util.Log
import com.westone.sunstone.R
import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.Question
import com.westone.sunstone.routines.dialog.Reply
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
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: PanelPresenter
 * 목적: 왼쪽 구역에 그릴 값(`ScreenUi`)을 만든다. 판단은 하지 않고 routines가
 *       준 값을 화면 모양으로만 바꾼다.
 *       - `Asking.question` → 제안 카드. 그 질문의 말이 시작될 때 띄우고, 그
 *         전까지는 앞 화면(토스트 포함)을 둔다. 버튼과 닫기는 대답이 된다
 *       - 대답하면(말이든 버튼이든) 고른 버튼을 눌린 모습으로 `PICKED_MS` 보인
 *         뒤 카드를 닫는다
 *       - `RunnerUi.runs`의 세차 모드·차일드락 → 맞추는 중·준비됨 카드.
 *         준비되고 `CHIP_MS` 뒤에는 "○○ 켜짐" 한 줄로 접는다
 *       - `Asking.notices` → 토스트(판 자리)
 *       - 카드가 뜨면 듣는 중 판(LISTENING, "듣고 있어요"), 들은 말(`heard()`)은
 *         HEARD로 1초 보인 뒤 내린다. 그때 대답을 처리하는 중(`Asking.working`,
 *         내비가 경유지를 넣는 중)이면 내리지 않고 WORKING(도는 표시)으로 둔다.
 *         질문 말이 끝나고 8초 안에 대답이 없으면 판만 닫고 카드는 남긴다
 *       - 새 카드가 뜨거나 시연 장면이 바뀌면 남은 토스트를 바로 지운다
 * 인자: context — 화면 문구(strings.xml)를 읽는다
 *       scope — 타이머와 구독을 돌린다(화면 스레드)
 *       asking — 질문·토스트
 *       runner — 루틴 실행기 화면 상태
 *       speaking — 음성 안내가 말하는 중인지
 *       starts — 말 하나가 시작될 때 그 말에 붙은 질문(`AndroidSpeaker.starts`)
 *       onAnswer — 카드 버튼·닫기 대답(`AppGraph.answer()`)
 *       onFinish — 켜짐 표시 [끝내기](`RoutineRunner.undo()`)
 *       onHold — 마이크 칸·판 마이크 칸·카드 앱 아이콘을 길게 눌렀을 때
 *       (시연 모드 다음 장면)
 * 근거: 인용 — docs/SCREEN.md 6장, 1장 상태 흐름, 5장 대답 기다리는 시간 8초.
 *       2026-11-12 박건준 검토: 카드는 그 카드의 말이 시작될 때 띄운다
 */
class PanelPresenter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val asking: Asking,
    runner: StateFlow<RunnerUi>,
    private val speaking: StateFlow<Boolean>,
    private val starts: SharedFlow<Any?>,
    private val onAnswer: (Reply) -> Unit,
    private val onFinish: (RoutineId) -> Unit,
    val onHold: () -> Unit,
) {

    private companion object {
        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 변수: LISTEN_TIMEOUT_MS
         * 목적: 질문 말이 끝난 뒤 대답을 기다리는 시간(밀리초). 지나면 듣는 중
         *       판만 닫는다.
         * 근거: 인용 — docs/SCREEN.md 5장 대답 기다리는 시간 8초
         */
        const val LISTEN_TIMEOUT_MS = 8_000L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 변수: SPEAK_START_MS
         * 목적: 판을 띄운 뒤 말이 시작되기를 기다리는 최대 시간(밀리초).
         * 근거: 미정 — 말이 줄에 들어오기까지 한 프레임 안팎이라 넉넉히 잡았다
         */
        const val SPEAK_START_MS = 1_000L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 수정: 2026-11-12 19:44:00 · 박건준
         * 변수: HEARD_MS
         * 목적: 들은 말을 판에 보여 주는 시간(밀리초). 지나면 판을 내린다.
         * 근거: 인용 — 2026-11-12 박건준 검토: 말을 알아듣고 1초 뒤 내린다
         */
        const val HEARD_MS = 1_000L

        /**
         * 작성: 2026-11-12 18:44:00 · 박건준
         * 수정: 2026-11-13 03:40:00 · 박건준
         * 변수: TOAST_MS
         * 목적: 토스트를 보여 주는 가장 짧은 시간(밀리초). 들은 말을 보이는 중이면
         *       그것이 내려간 뒤부터 센다. 함께 나온 말이 더 길면 말이 끝날 때까지
         *       둔다.
         * 근거: 인용 — 시안 template2.html `toast()` 3200
         */
        const val TOAST_MS = 3_200L

        /**
         * 작성: 2026-11-12 20:10:00 · 박건준
         * 변수: SHOW_WAIT_MS
         * 목적: 질문의 말이 시작되기를 기다리는 최대 시간(밀리초). 지나면 말이
         *       없어도 카드를 띄운다(말이 줄에서 빠졌을 때).
         * 근거: 미정 — 앞 말 두세 개(문장마다 3~6초)가 끝나기를 넉넉히 기다린다
         */
        const val SHOW_WAIT_MS = 20_000L

        /**
         * 작성: 2026-11-13 03:40:00 · 박건준
         * 변수: PICKED_MS
         * 목적: 고른 버튼을 눌린 모습으로 보여 주는 시간(밀리초). 지나면 카드를
         *       닫는다.
         * 근거: 인용 — 2026-11-13 박건준 2차 업그레이드 지시: 고른 버튼을 1초
         *       보여 준 뒤 카드를 닫는다
         */
        const val PICKED_MS = 1_000L

        /**
         * 작성: 2026-11-13 03:40:00 · 박건준
         * 변수: CHIP_MS
         * 목적: 세차 모드가 준비된 뒤 카드를 "켜짐" 한 줄로 접기까지 시간(밀리초).
         * 근거: 인용 — docs/SCREEN.md 6장 8단계: 3초 뒤 접힌다
         */
        const val CHIP_MS = 3_000L

        /**
         * 작성: 2026-11-13 04:00:00 · 박건준
         * 변수: TAG
         * 목적: 앱 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "Sunstone"

        /**
         * 작성: 2026-11-13 10:00:00 · 박희정
         * 변수: CARD_ROUTINES
         * 목적: 맞추는 중·준비됨 카드로 보일 루틴.
         * 근거: 인용 — docs/SCREEN.md 6장 "②③에 같은 짜임을 쓸 때"
         */
        val CARD_ROUTINES = listOf(RoutineId.WASH, RoutineId.CHILD_LOCK)
    }

    /** 화면에 띄운 질문. 질문의 말이 시작되면 채우고, 대답이 오면 비운다. 없으면 null */
    private val shown = MutableStateFlow<Question?>(null)

    /** 대답한 질문과 그 대답. `PICKED_MS` 동안만 있고 그동안 카드를 눌린 모습으로 둔다. 없으면 null */
    private val held = MutableStateFlow<Pair<Question, Reply>?>(null)

    /** 질문의 말이 시작되기를 기다리는 작업. 없으면 null */
    private var showJob: Job? = null

    /** 고른 버튼을 보여 주다 내리는 작업. 없으면 null */
    private var heldJob: Job? = null

    /** 듣는 중 판. 질문·들은 말·마이크 칸이 바꾼다 */
    private val listen = MutableStateFlow(ListenUi(ListenState.OFF))

    /** 지금 토스트 문장. 없으면 null */
    private val toast = MutableStateFlow<String?>(null)

    /** 닫기로 내린 진행 카드의 실행 시작 시각(밀리초). 그 실행 동안은 다시 띄우지 않는다. 없으면 null */
    private val hiddenRun = MutableStateFlow<Long?>(null)

    /** 켜짐 표시로 접은 실행의 시작 시각(밀리초). 없으면 null */
    private val chipRun = MutableStateFlow<Long?>(null)

    /** 준비됨을 본 실행의 시작 시각(밀리초). 같은 실행에 접기 타이머를 두 번 걸지 않는다. 없으면 null */
    private var readyRun: Long? = null

    /** 듣는 중 판을 닫거나 바꾸는 타이머. 없으면 null */
    private var listenJob: Job? = null

    /** 토스트를 내리는 타이머. 없으면 null */
    private var toastJob: Job? = null

    /** 지금 진행 카드가 보여 주는 실행. `cardOf()`가 채우고 닫기(`Hide`)가 쓴다. 없으면 null */
    private var shownRun: RunView? = null

    /** 지금 보일 카드(값이 바뀌면 알려주는 상태) */
    private val card = combine(shown, held, runner, hiddenRun, chipRun) { q, h, r, hidden, chip ->
        cardOf(h ?: q?.let { it to null }, r, hidden, chip)
    }

    /** 왼쪽 구역에 그릴 것 전부(값이 바뀌면 알려주는 상태) */
    val ui: StateFlow<ScreenUi> = combine(card, listen, toast) { c, l, t -> ScreenUi(c, l, t) }
        .stateIn(scope, SharingStarted.Eagerly, ScreenUi(null, ListenUi(ListenState.OFF), null))

    init {
        scope.launch { asking.question.collect { onQuestion(it) } }
        scope.launch { asking.notices.collect { showToast(it) } }
        scope.launch { runner.map { liveRun(it) }.collect { onRun(it) } }
        // 처리가 끝나면(결과 토스트가 먼저 온다) 도는 표시 판을 내린다
        scope.launch {
            asking.working.collect { on ->
                if (!on && listen.value.state == ListenState.WORKING) listen.value = ListenUi(ListenState.OFF)
            }
        }
        // 새 카드(제목이 바뀜)가 뜨면 앞 장면 토스트가 남지 않게 지운다
        scope.launch { ui.map { it.card?.title }.distinctUntilChanged().collect { if (it != null) clearToast() } }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 04:20:00 · 박건준
     * 함수: heard
     * 목적: 운전자가 한 말을 판에 HEARD로 보여 준다. 음성·시연 녹음본·adb
     *       `say`가 모두 `AppGraph.hear()`를 거쳐 여기로 온다. `HEARD_MS` 뒤
     *       아직 질문이 남아 있으면(못 알아들음) 다시 듣고, 대답을 처리하는
     *       중이면 같은 글자로 WORKING(도는 표시)을 두고, 아니면 판을 닫는다.
     * 인자: text — 들은 말
     *       next — 들은 말 아래에 보일, 앱이 이어서 하는 일("세차 모드를
     *       켤게요"). 없으면 null(줄을 비운다)
     * 근거: 인용 — 시안 sunstone-ui.html 들음 상태 `.vh`
     */
    fun heard(text: String, next: String?) {
        listenJob?.cancel()
        listen.value = ListenUi(ListenState.HEARD, context.getString(R.string.listen_heard, text), next)
        listenJob = scope.launch {
            delay(HEARD_MS)
            val q = shown.value // 아직 떠 있는 질문(못 알아들었을 때), 없으면 null
            listen.value = when {
                q != null -> return@launch startListening()
                asking.working.value -> listen.value.copy(state = ListenState.WORKING)
                else -> ListenUi(ListenState.OFF)
            }
        }
    }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 함수: picked
     * 목적: 대답을 받으면(말이든 버튼이든) 떠 있는 카드에서 그 대답의 버튼을
     *       눌린 모습으로 `PICKED_MS` 보인 뒤 카드를 닫는다. 닫기·거절 버튼이
     *       없는 거절이면 바로 닫는다. 대답 처리 자체는 `AppGraph.answer()`가
     *       한다.
     * 인자: reply — 대답 값
     * 근거: 인용 — 2026-11-13 박건준 2차 업그레이드 지시: 말로 대답해도 그
     *       버튼에 표시한다
     */
    fun picked(reply: Reply) {
        val q = shown.value ?: return // 떠 있는 질문
        if (q.pick(reply) == null) return
        heldJob?.cancel()
        held.value = q to reply
        heldJob = scope.launch {
            delay(PICKED_MS)
            held.value = null
        }
    }

    /**
     * 작성: 2026-11-12 19:44:00 · 박건준
     * 함수: clearToast
     * 목적: 남은 토스트를 바로 지운다. 새 카드가 뜰 때와 시연 장면이 바뀔 때
     *       부른다.
     */
    fun clearToast() {
        toastJob?.cancel()
        toast.value = null
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 22:45:00 · 박건준
     * 함수: tapMic
     * 목적: 마이크 칸을 눌렀을 때 듣는 중 판을 띄운다. 8초 동안 말이 없으면
     *       닫는다. 시연 모드에서 운전자 대사 녹음본을 낼 때도 부른다.
     * 미구현: 프로토타입이라 마이크 칸은 판만 띄우고 음성 인식은 하지 않는다.
     *       에뮬레이터에 마이크 입력이 없어 말은 시연 녹음본·adb `say`로 넣는다
     *       (docs/api-spike.md 음성 인식 행)
     */
    fun tapMic() = startListening()

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: press
     * 목적: 카드 버튼이나 닫기 칸을 처리한다. [끝내기]는 지금 보이는 실행을
     *       끝낸다.
     * 인자: press — 누른 것이 하는 일
     */
    fun press(press: PanelPress) {
        when (press) {
            is PanelPress.Answer -> onAnswer(press.reply) // → 로직 이동: AppGraph.answer()
            PanelPress.Finish -> onFinish(shownRun?.id ?: RoutineId.WASH) // → 로직 이동: RoutineRunner.undo()
            PanelPress.Hide -> hiddenRun.value = shownRun?.startedAtMs
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 22:45:00 · 박건준
     * 함수: onQuestion
     * 목적: 질문이 바뀌면 카드와 판을 맞춘다.
     *       - 새 질문 — 그 질문의 말이 시작될 때(`starts`) 카드를 띄우고 듣는 중
     *         판을 켠다. 그 전까지는 앞 화면을 그대로 둔다
     *       - 질문이 없어짐(대답함) — 카드를 내리고 듣는 중 판을 닫는다
     *       들은 말을 보여 주는 중이면 판은 `heard()`의 타이머가 이어서 정한다.
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
            if (listen.value.state != ListenState.HEARD) startListening()
        }
    }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: onRun
     * 목적: 세차 모드·차일드락이 준비되면(켜짐) `CHIP_MS` 뒤 카드를 켜짐 표시로
     *       접는다. 새 실행이 시작되면 접은 기록을 지운다.
     * 인자: run — 보일 실행 기록(`liveRun()`). 없으면 null
     */
    private fun onRun(run: RunView?) {
        if (run == null || run.phase == Phase.RUNNING) {
            chipRun.value = null
            readyRun = null
            return
        }
        if (run.phase != Phase.ACTIVE || readyRun == run.startedAtMs) return
        readyRun = run.startedAtMs
        scope.launch {
            delay(CHIP_MS)
            if (readyRun == run.startedAtMs) chipRun.value = run.startedAtMs
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-12 22:45:00 · 박건준
     * 함수: startListening
     * 목적: 듣는 중 판("듣고 있어요" 한 줄)을 띄우고, 지금 말이 끝난 뒤
     *       `LISTEN_TIMEOUT_MS`가 지나면 판만 닫는다.
     * 근거: 인용 — 2026-11-12 박건준 검토: 듣는 중에는 대답 예를 보이지 않는다
     */
    private fun startListening() {
        listenJob?.cancel()
        listen.value = ListenUi(ListenState.LISTENING, context.getString(R.string.listen_on))
        listenJob = scope.launch {
            withTimeoutOrNull(SPEAK_START_MS) { speaking.first { it } }
            speaking.first { !it }
            delay(LISTEN_TIMEOUT_MS)
            listen.value = ListenUi(ListenState.OFF)
        }
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 03:40:00 · 박건준
     * 함수: showToast
     * 목적: 토스트를 판 자리에 띄운다.
     *       - 들은 말을 보이는 중이면 그것이 내려간 뒤부터 `TOAST_MS`를 센다
     *       - 함께 나온 말이 있으면 그 말이 끝날 때까지 둔다
     *       새 카드가 뜨면 `clearToast()`가 먼저 지운다. 시연 기록을 맞추려고
     *       logcat에 "토스트,…"를 남긴다.
     * 인자: text — 토스트 문장
     * 근거: 인용 — 2026-11-12 박건준 검토: 토스트는 함께 나온 말이 끝날 때까지
     *       둔다. 시안 template2.html 재생은 들은 말 3초 뒤 토스트를 띄운다
     */
    private fun showToast(text: String) {
        Log.i(TAG, "토스트,$text")
        toastJob?.cancel()
        toast.value = text
        toastJob = scope.launch {
            listen.first { it.state != ListenState.HEARD }
            delay(TOAST_MS)
            speaking.first { !it }
            toast.value = null
        }
    }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: liveRun
     * 목적: 진행 카드로 보일 실행을 고른다. 세차 모드·차일드락 중 실행 중이거나
     *       켜져 있는 것, 둘 다면 나중에 시작한 것.
     * 인자: r — 루틴 실행기 화면 상태
     * 반환: 실행 기록. 없으면 null
     * 근거: 인용 — docs/SCREEN.md 4장 "창구에 더해지는 것": 차일드락 진행은
     *       RunnerUi.runs의 StepView를 그대로 쓴다
     */
    private fun liveRun(r: RunnerUi): RunView? = CARD_ROUTINES.mapNotNull { r.runs[it] }
        .filter { it.phase == Phase.RUNNING || it.phase == Phase.ACTIVE }
        .maxByOrNull { it.startedAtMs }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: cardOf
     * 목적: 지금 보일 카드를 고른다. 질문(대답한 질문 포함)이 먼저이고, 없으면
     *       세차 모드·차일드락 진행(접었으면 켜짐 표시).
     * 인자: asked — 질문과 고른 대답. 질문이 없으면 null, 대답 전이면 대답 null
     *       r — 루틴 실행기 화면 상태
     *       hidden — 닫기로 내린 실행의 시작 시각. 없으면 null
     *       chip — 켜짐 표시로 접은 실행의 시작 시각. 없으면 null
     * 반환: 카드. 보일 것이 없으면 null
     */
    private fun cardOf(asked: Pair<Question, Reply?>?, r: RunnerUi, hidden: Long?, chip: Long?): CardUi? {
        if (asked != null) return questionCard(asked.first, asked.second)
        val run = liveRun(r)?.takeIf { it.startedAtMs != hidden } // 보일 실행
        shownRun = run?.takeIf { it.phase == Phase.RUNNING || it.phase == Phase.ACTIVE }
        val showing = shownRun ?: return null // 진행 카드로 보일 실행
        return if (showing.phase == Phase.ACTIVE && chip == showing.startedAtMs) chipCard(showing) else runCard(showing)
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 03:40:00 · 박건준
     * 함수: questionCard
     * 목적: 질문을 제안 카드로 바꾼다. 할 일 칸은 모두 아직(회색)이고, 사람이 할
     *       칸은 "직접"을 미리 보인다. 첫 번째 버튼이 채운 버튼이다.
     * 인자: q — 질문
     *       reply — 고른 대답. 대답 전이면 null
     * 반환: 카드
     * 근거: 인용 — docs/SCREEN.md 6장 2·6단계
     */
    private fun questionCard(q: Question, reply: Reply?): CardUi {
        val pick = reply?.let(q::pick) // 고른 버튼, 없으면 null
        return CardUi(
            title = q.title,
            detail = q.detail,
            emphasis = q.emphasis,
            facts = q.facts,
            cells = q.rows.map { row ->
                CellUi(row.icon, row.text, if (row.manual) CellState.MANUAL else CellState.TODO, row.note)
            },
            actions = q.choices.mapIndexed { i, c ->
                ActionUi(c.label, i == 0, PanelPress.Answer(c.reply), if (pick == null) c.waitMs else 0, c == pick)
            },
            close = PanelPress.Answer(Reply.NO),
            key = q,
        )
    }

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: runCard
     * 목적: 루틴 실행을 맞추는 중·준비됨 카드로 바꾼다. 같은 이름 단계(창문
     *       네 개)는 한 칸으로 묶는다. 버튼은 없다.
     *       - 맞추는 중 — "N개 중 M개 맞췄어요."(앞부분 굵게)
     *       - 준비됨 — 사람이 할 일("사이드미러는 직접 접어 주세요.", 차일드락은
     *         "뒷문 차일드락은 직접 걸어 주세요.")
     * 인자: run — 실행 기록(실행 중이거나 켜져 있음)
     * 반환: 카드
     * 근거: 인용 — 시안 template2.html `card()` run·ready
     */
    private fun runCard(run: RunView): CardUi {
        val cells = mergeSteps(run.steps) // 한 칸씩 묶은 할 일
        val total = cells.count { !it.second } // 앱이 맞출 칸 수
        val done = cells.count { it.first.state == CellState.DONE } // 맞춘 칸 수
        val ready = run.phase == Phase.ACTIVE // 다 맞추고 켜져 있는지
        return CardUi(
            title = context.getString(if (ready) R.string.panel_ready else R.string.panel_running, run.title),
            detail = if (ready) {
                val manual = if (run.id == RoutineId.CHILD_LOCK) { // 준비됨 아래 사람이 할 일
                    R.string.panel_ready_detail_child
                } else {
                    R.string.panel_ready_detail
                }
                context.getString(manual)
            } else {
                context.getString(R.string.panel_progress, total, done)
            },
            emphasis = if (ready) null else context.getString(R.string.panel_progress_bold, total, done),
            cells = cells.map { it.first },
            close = PanelPress.Hide,
            key = run.startedAtMs,
        )
    }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: chipCard
     * 목적: 켜진 세차 모드·차일드락을 높이 88 한 줄(켜짐 표시)로 바꾼다.
     *       [끝내기]만 둔다.
     * 인자: run — 켜져 있는 실행 기록
     * 반환: 카드
     * 근거: 인용 — docs/SCREEN.md 6장 8단계, 6장 ② "출발하면 '차일드락 켜짐' 한
     *       줄로 접힙니다", 시안 template2.html `.chipc`
     */
    private fun chipCard(run: RunView) = CardUi(
        title = context.getString(R.string.chip_title, run.title),
        detail = context.getString(
            if (run.id == RoutineId.CHILD_LOCK) R.string.chip_detail_child else R.string.chip_detail,
        ),
        actions = listOf(ActionUi(context.getString(R.string.chip_finish), false, PanelPress.Finish)),
        chip = true,
        key = run.startedAtMs,
    )

    /**
     * 작성: 2026-11-12 18:44:00 · 박건준
     * 수정: 2026-11-13 03:40:00 · 박건준
     * 함수: mergeSteps
     * 목적: 이어지는 같은 이름 단계를 한 칸으로 묶고 상태를 옮긴다.
     *       - 하나라도 RUNNING → NOW
     *       - 모두 DONE → DONE
     *       - 하나라도 GUIDED → MANUAL(아래 "직접")
     *       - 모두 WAITING·SKIPPED → TODO
     *       - 일부만 DONE(단계 사이) → NOW
     * 인자: steps — 실행 단계들, 실행 순서대로
     * 반환: (칸, 사람이 할 칸인지) 목록
     */
    private fun mergeSteps(steps: List<StepView>): List<Pair<CellUi, Boolean>> {
        val groups = mutableListOf<MutableList<StepView>>() // 같은 이름끼리 이은 묶음
        steps.forEach { s ->
            if (groups.lastOrNull()?.first()?.title == s.title) groups.last() += s else groups += mutableListOf(s)
        }
        return groups.map { g ->
            val state = when { // 묶음 상태
                g.any { it.status == StepStatus.RUNNING } -> CellState.NOW
                g.all { it.status == StepStatus.DONE } -> CellState.DONE
                g.any { it.status == StepStatus.GUIDED } -> CellState.MANUAL
                g.all { it.status == StepStatus.WAITING || it.status == StepStatus.SKIPPED } -> CellState.TODO
                else -> CellState.NOW
            }
            val note = if (state == CellState.MANUAL) context.getString(R.string.cell_manual) else null // 사람 몫 표시
            CellUi(g.first().icon, g.first().title, state, note) to g.first().guide
        }
    }
}
