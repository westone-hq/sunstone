package com.westone.sunstone.routines.dialog

import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.SeatProfile
import com.westone.sunstone.routines.port.SeatProfiles
import com.westone.sunstone.routines.port.Speaker
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.SeatLines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 작성: 2026-11-13 15:00:00 · 박희정
 * 클래스: SeatDialog
 * 목적: 시트 맞추기 대화(시나리오 ③). 여러 번 말을 주고받는 일이라 루틴이
 *       아니라 대화로 둔다.
 *       1. "시트 좀 맞춰 줘" — 맞추는 중 카드를 띄우고 말을 기다린다
 *       2. "앞으로 조금" — 그 축만 조금 움직이고 한 일을 말한다
 *       3. "됐어"·[다 됐어요] — 누구 이름으로 기억할지 묻는다
 *       4. 이름 — 지금 자세를 그 이름으로 저장한다
 *       - [원래대로] — 이번에 움직인 만큼 반대로 되돌린다
 *       - "옆에 희정이 앉았어" — 저장된 자세면 시트 메모리 루틴으로 맞추고,
 *         처음 보는 이름이면 같이 맞출지 묻는다(예면 1부터, 끝나면 그 이름으로
 *         바로 저장)
 *       - 시트는 정차 중 P단에서만 움직인다
 * 인자: car — 시트를 움직이고 위치를 읽는다
 *       profiles — 사람별 자세 저장소
 *       asking — 카드·토스트. 다른 대화와 같은 것을 넘긴다
 *       speaker — 말하기
 *       startRun — 저장된 자세로 맞출 때 시트 메모리 루틴을 부른다
 *       (`RoutineRunner.request()`)
 * 근거: 인용 — docs/SCREEN.md 4장 ③ 표와 규칙, 5장 "조금"의 양. CLAUDE.md
 *       "시트는 P단에서만"
 */
class SeatDialog(
    private val car: CarPort,
    private val profiles: SeatProfiles,
    private val asking: Asking,
    private val speaker: Speaker,
    private val startRun: (RunRequest) -> Unit,
) {

    private companion object {
        /**
         * 작성: 2026-11-13 15:00:00 · 박희정
         * 변수: AXES
         * 목적: 말로 맞추고 저장하는 축과 카드 칸 아이콘. 카드에 이 순서로 보인다.
         * 근거: 인용 — docs/SCREEN.md 4장 ③-1 줄 앞뒤·높이·등받이, 4장 아이콘 키
         *       seat, seat-height, seat-back
         */
        val AXES = linkedMapOf(
            SeatAxis.FORE_AFT to "seat",
            SeatAxis.HEIGHT to "seat-height",
            SeatAxis.BACKREST_ANGLE to "seat-back",
        )
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 클래스: Stage
     * 목적: 대화가 무엇을 기다리는지.
     *       - `ADJUST` — 맞추는 말("앞으로 조금", "됐어")
     *       - `NAME` — 기억할 이름
     *       - `NEW_PERSON` — 처음 보는 이름에 같이 맞출지 대답
     */
    private enum class Stage { ADJUST, NAME, NEW_PERSON }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 클래스: Session
     * 목적: 지금 하는 시트 맞추기 하나.
     * 인자: seat — 맞추는 좌석
     *       stage — 기다리는 것
     *       name — 미리 정해진 이름(처음 보는 사람). 없으면 끝날 때 묻는다
     *       moves — 이번에 움직인 것(축, 양 %), 움직인 순서대로
     */
    private data class Session(
        val seat: Seat,
        val stage: Stage,
        val name: String?,
        val moves: List<Pair<SeatAxis, Int>> = emptyList(),
    )

    /** 한 번에 한 말만 처리한다. 시트 이동은 0.2~0.6초 걸린다 */
    private val lock = Mutex()

    /** 지금 하는 시트 맞추기. 없으면 null */
    @Volatile
    private var session: Session? = null

    /** 이름을 기다리는 중이면 true. 앱은 이때 들은 말을 이름으로 넘긴다(`name()`) */
    val awaitingName: Boolean get() = session?.stage == Stage.NAME

    /** 시트 맞추기 중이면 true("원래대로"를 이 대화로 넘긴다) */
    val active: Boolean get() = session != null

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: start
     * 목적: 시트 맞추기를 시작한다(③-1). 정차 중 P단이 아니면 이유만 말한다.
     * 인자: seat — 맞출 좌석
     */
    suspend fun start(seat: Seat) = lock.withLock { begin(seat, name = null, say = true) }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: adjust
     * 목적: 축 하나를 조금 움직인다(③-2·③-3). 맞추기 중이 아니면 운전석으로
     *       시작부터 한다. 움직이면 카드 그 칸에 체크와 한 일을 보이고 말한다.
     * 인자: axis — 축
     *       percent — 움직일 양(축 전체의 %). 양수면 앞·위·세움
     * 근거: 인용 — docs/SCREEN.md 5장 "조금" 10%, "많이" 30%
     */
    suspend fun adjust(axis: SeatAxis, percent: Int) = lock.withLock {
        if (session?.stage != Stage.ADJUST && !begin(Seat.DRIVER, name = null, say = false)) return@withLock
        val now = session ?: return@withLock // 맞추는 중인 것
        blocked()?.let { speaker.say(it); return@withLock }
        // → 로직 이동: CarPort.apply()
        val result = car.apply(Command(Target.SeatNudge(now.seat, axis), percent))
        if (!result.ok) {
            speaker.say(SeatLines.UNREADABLE)
            return@withLock
        }
        session = now.copy(moves = now.moves + (axis to percent))
        asking.ask(adjustCard()) // → 로직 이동: Asking.ask()
        speaker.say(SeatLines.movedSay(axis, percent > 0))
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: answer
     * 목적: 시트 대화가 기다리는 대답을 처리한다(말이든 카드 버튼이든).
     *       - 맞추는 중: 받아들임("됐어", [다 됐어요])이면 이름을 묻고(이름이 정해져
     *         있으면 바로 저장), 거절([원래대로])이면 되돌린다
     *       - 이름 묻는 중: 거절([기억 안 함])이면 기억하지 않는다
     *       - 처음 보는 이름: 받아들이면 그 사람 좌석으로 맞추기를 시작한다
     * 인자: reply — 대답 값
     * 반환: 시트 대화가 기다리던 대답이면 true. 아니면 false(다른 곳이 처리)
     */
    suspend fun answer(reply: Reply): Boolean = lock.withLock {
        val now = session ?: return@withLock false // 지금 시트 대화
        val yes = reply != Reply.NO // 받아들였는지
        when (now.stage) {
            Stage.ADJUST -> if (yes) finish(now) else restore(now)
            Stage.NAME -> if (yes) return@withLock false else close(SeatLines.SKIPPED)
            Stage.NEW_PERSON -> if (yes) begin(now.seat, now.name, say = true) else close(SeatLines.SKIPPED)
        }
        true
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: name
     * 목적: 이름을 기다리는 중이면 들은 말에서 이름을 뽑아 지금 자세를 저장한다
     *       (③-5).
     * 인자: text — 들은 말("건준", "건준이야")
     * 반환: 이름으로 처리했으면 true
     */
    suspend fun name(text: String): Boolean = lock.withLock {
        val now = session?.takeIf { it.stage == Stage.NAME } ?: return@withLock false // 이름 기다리는 대화
        val name = cleanName(text) ?: return@withLock false // 뽑은 이름
        save(now, name)
        true
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: recall
     * 목적: 이름으로 저장된 자세로 맞춘다(③-6). 저장된 자세가 있으면 말하고 시트
     *       메모리 루틴을 부른다. 없으면 같이 맞출지 묻는다.
     * 인자: name — 이름
     *       seat — 그 사람이 앉은 좌석
     */
    suspend fun recall(name: String, seat: Seat) = lock.withLock {
        if (profiles.get(name) == null) {
            session = Session(seat, Stage.NEW_PERSON, name)
            asking.ask(newPersonCard()) // → 로직 이동: Asking.ask()
            speaker.say(SeatLines.unknown(name))
            return@withLock
        }
        speaker.say(SeatLines.recalling(name))
        // → 로직 이동: RoutineRunner.request()
        startRun(RunRequest(RoutineId.SEAT_MEMORY, profile = name, source = Source.VOICE, seat = seat))
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: reset
     * 목적: 시연을 처음으로 돌릴 때 대화를 지운다. 시트는 움직이지 않는다.
     */
    fun reset() {
        session = null
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: begin
     * 목적: 맞추기를 시작한다. 막혀 있으면 이유를 말하고 시작하지 않는다.
     * 인자: seat — 좌석
     *       name — 미리 정한 이름. 없으면 null
     *       say — 시작 말("어떻게 맞춰 드릴까요?")을 할지
     * 반환: 시작했으면 true
     */
    private fun begin(seat: Seat, name: String?, say: Boolean): Boolean {
        blocked()?.let {
            session = null
            speaker.say(it)
            return false
        }
        session = Session(seat, Stage.ADJUST, name)
        asking.ask(adjustCard()) // → 로직 이동: Asking.ask()
        if (say) speaker.say(SeatLines.HOW)
        return true
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: finish
     * 목적: 다 맞췄을 때. 이름이 정해져 있으면 바로 저장하고, 아니면 묻는다
     *       (③-4).
     * 인자: now — 지금 대화
     */
    private suspend fun finish(now: Session) {
        asking.answered(QuestionKind.SEAT_ADJUST)
        val name = now.name // 미리 정한 이름
        if (name != null) {
            save(now, name)
            return
        }
        session = now.copy(stage = Stage.NAME)
        asking.ask(nameCard()) // → 로직 이동: Asking.ask()
        speaker.say(SeatLines.ASK_NAME)
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: save
     * 목적: 지금 좌석의 축 위치를 읽어 이름으로 저장하고 알린다. 하나도 읽지
     *       못하면 기억하지 못했다고 말한다.
     * 인자: now — 지금 대화
     *       name — 이름
     * 미구현: 프로토타입이라 위치는 앱이 시간으로 움직이며 추정한 값이다. 시연
     *       준비에서 시작 자세로 옮겨 두어야 추정 값이 생긴다(`Prep.driverSeat`)
     */
    private suspend fun save(now: Session, name: String) {
        asking.answered(QuestionKind.SEAT_NAME)
        val positions = AXES.keys.mapNotNull { axis -> // 읽은 축 위치
            (runCatching { car.read(Target.SeatPosition(now.seat, axis)) }.getOrNull() as? Int)?.let { axis to it }
        }.toMap()
        session = null
        if (positions.isEmpty()) {
            speaker.say(SeatLines.UNREADABLE)
            return
        }
        profiles.save(SeatProfile(name, positions)) // → 로직 이동: SeatProfiles.save()
        speaker.say(SeatLines.saved(name))
        asking.notice(SeatLines.savedNotice(name)) // → 로직 이동: Asking.notice()
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: restore
     * 목적: 이번에 움직인 것을 반대 순서·반대 방향으로 되돌린다([원래대로]).
     * 인자: now — 지금 대화
     * 근거: 인용 — docs/SCREEN.md 4장 ③ 규칙 "[원래대로]는 이번에 움직인 시간만큼
     *       되돌립니다"
     */
    private suspend fun restore(now: Session) {
        now.moves.asReversed().forEach { (axis, percent) ->
            runCatching { car.apply(Command(Target.SeatNudge(now.seat, axis), -percent)) } // → 로직 이동: CarPort.apply()
        }
        close(SeatLines.RESTORED)
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: close
     * 목적: 대화를 끝내고 카드를 내린 뒤 말한다.
     * 인자: say — 할 말
     */
    private fun close(say: String) {
        session?.let { s ->
            val kind = when (s.stage) { // 내릴 카드 종류
                Stage.ADJUST -> QuestionKind.SEAT_ADJUST
                Stage.NAME -> QuestionKind.SEAT_NAME
                Stage.NEW_PERSON -> QuestionKind.SEAT_NEW_PERSON
            }
            asking.answered(kind)
        }
        session = null
        speaker.say(say)
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: blocked
     * 목적: 지금 시트를 움직이면 안 되는 이유. 달리는 중, P단이 아님.
     * 반환: 이유 문장. 움직여도 되면 null
     */
    private fun blocked(): String? {
        val state = car.state.value // 지금 차 상태
        return when {
            !state.stopped -> SeatLines.BLOCKED_MOVING
            state.gear != Gear.PARK -> SeatLines.BLOCKED_GEAR
            else -> null
        }
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: adjustCard
     * 목적: 맞추는 중 카드. 움직인 축은 체크와 마지막으로 한 일("앞으로 옮김")을
     *       보인다. 버튼 [다 됐어요] [원래대로].
     * 반환: 질문
     */
    private fun adjustCard(): Question {
        val now = session // 지금 대화
        val rows = AXES.map { (axis, icon) -> // 축마다 칸
            val last = now?.moves?.lastOrNull { it.first == axis } // 그 축에 마지막으로 한 일
            QuestionRow(
                icon = icon,
                text = SeatLines.axisName(axis),
                note = last?.let { SeatLines.moved(axis, it.second > 0) },
                done = last != null,
            )
        }
        return Question(
            kind = QuestionKind.SEAT_ADJUST,
            title = SeatLines.adjustTitle(now?.seat ?: Seat.DRIVER),
            detail = SeatLines.ADJUST_DETAIL,
            rows = rows,
            choices = listOf(Choice(SeatLines.DONE, Reply.YES), Choice(SeatLines.RESTORE, Reply.NO)),
        )
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: nameCard
     * 목적: 이름 묻는 카드. 칸 없이 [기억 안 함] 하나.
     * 반환: 질문
     * 근거: 인용 — docs/SCREEN.md 4장 ③-4
     */
    private fun nameCard() = Question(
        kind = QuestionKind.SEAT_NAME,
        title = SeatLines.NAME_TITLE,
        detail = SeatLines.NAME_DETAIL,
        choices = listOf(Choice(SeatLines.NAME_SKIP, Reply.NO)),
    )

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: newPersonCard
     * 목적: 처음 보는 이름일 때 같이 맞출지 묻는 카드.
     * 반환: 질문
     */
    private fun newPersonCard(): Question {
        val name = session?.name.orEmpty() // 처음 보는 이름
        return Question(
            kind = QuestionKind.SEAT_NEW_PERSON,
            title = SeatLines.NEW_TITLE,
            detail = SeatLines.unknown(name),
            choices = listOf(Choice(SeatLines.NEW_YES, Reply.YES), Choice(SeatLines.NEW_NO, Reply.NO)),
        )
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: cleanName
     * 목적: 들은 말에서 이름만 뽑는다. 첫 낱말에서 "님", "이야", "요" 같은 끝을
     *       뗀다.
     * 인자: text — 들은 말
     * 반환: 이름. 비면 null
     */
    private fun cleanName(text: String): String? {
        val word = text.trim().split(Regex("""\s+""")).firstOrNull()?.trimEnd('.', '!', '?', ',') // 첫 낱말
            ?: return null
        val name = NAME_ENDINGS.fold(word) { w, end -> // 끝을 뗀 이름
            if (w.length > end.length && w.endsWith(end)) w.dropLast(end.length) else w
        }
        return name.ifEmpty { null }
    }
}

/**
 * 작성: 2026-11-13 15:00:00 · 박희정
 * 변수: NAME_ENDINGS
 * 목적: 이름 뒤에 붙는 말. 긴 것부터 뗀다("건준이에요" → "건준").
 * 근거: 미정 — 시연 대사와 흔한 대답만 넣었다
 */
private val NAME_ENDINGS = listOf("이에요", "예요", "입니다", "이야", "이요", "님", "야", "요")
