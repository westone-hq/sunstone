package com.westone.sunstone.routines.dialog

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.westone.sunstone.routines.port.MemoryPort
import java.time.LocalDate

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 수정: 2026-11-13 15:00:00 · 박희정
 * 수정: 2026-11-14 16:00:00 · 박건준
 * 클래스: QuestionKind
 * 목적: 앱이 먼저 묻는 질문의 종류. 같은 길안내 안에서 거절한 질문을 다시
 *       묻지 않을 때 이 값으로 가린다.
 *       - `FUEL_STOP` — 주유(조건이 맞으면 세차도 같이) 할까요(길안내 대화)
 *       - `WASH_MODE` — 세차 모드를 켤게요/켤까요(세차 모드 권하기)
 *       - `WASH_RAIN` — 비 소식이 있는데 그래도 세차할까요(운전자가 먼저
 *         세차하겠다고 할 때)
 *       - `WASH_DONE` — 세차 끝났나요(세차 모드가 오래 켜져 있을 때)
 *       - `CHILD_LOCK` — 뒷자리에 누가 탔을 때 뒷창문 잠글까요
 *       - `CHILD_LOCK_AGAIN` — 하원길에 아침처럼 다시 잠글까요
 *       - `SEAT_ADJUST` — 시트를 말로 맞추는 중([다 됐어요] [원래대로])
 *       - `SEAT_NAME` — 맞춘 자세를 누구 이름으로 기억할까요
 *       - `SEAT_NEW_PERSON` — 처음 보는 이름이면 같이 맞춰 볼까요
 * 근거: 인용 — docs/SCREEN.md 6장 시나리오 ① 다시 짠 흐름: 질문을 세 번에서
 *       한 번으로 줄이고 세차 추천은 주유 질문 안에 넣는다.
 *       docs/SCREEN.md 4장 창구에 더해지는 것: CHILD_LOCK, CHILD_LOCK_AGAIN,
 *       SEAT_NAME, SEAT_NEW_PERSON. 맞추는 중 패널도 같은 카드로 보여
 *       SEAT_ADJUST를 더했다(2026-11-13 박희정). docs/SPEC.md 4·5장:
 *       WASH_RAIN, WASH_DONE
 */
enum class QuestionKind {
    FUEL_STOP, WASH_MODE, WASH_RAIN, WASH_DONE, CHILD_LOCK, CHILD_LOCK_AGAIN, SEAT_ADJUST, SEAT_NAME, SEAT_NEW_PERSON;

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 변수: offer
     * 목적: 앱이 먼저 권한 질문이면 true. 거절하면 그날은 다시 권하지 않고,
     *       대답이 없으면 닫는다. 운전자가 시작한 일(시트 맞추기)과 확인 질문은
     *       아니다.
     * 근거: 인용 — docs/SPEC.md 3장 "같은 권유", "대답이 없을 때"
     */
    val offer: Boolean get() = this == FUEL_STOP || this == WASH_MODE || this == CHILD_LOCK || this == CHILD_LOCK_AGAIN

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 변수: closesUnanswered
     * 목적: 대답이 없으면 닫는 질문이면 true. 권한 질문과 확인 질문이다. 시트를
     *       맞추는 중인 카드는 닫지 않는다.
     */
    val closesUnanswered: Boolean get() = offer || this == WASH_RAIN || this == WASH_DONE
}

/**
 * 작성: 2026-11-13 03:25:00 · 박건준
 * 클래스: Reply
 * 목적: 질문에 대한 대답 값. 말로 해도 버튼으로 해도 이 값 하나가 된다.
 *       - `YES` — 받아들임("응", "그래"). 질문의 첫 번째(채운) 고를 것이 된다
 *       - `ALL` — 권한 것을 다 함("응, 세차도 할게")
 *       - `ONLY` — 앞의 것만 함("주유만")
 *       - `NO` — 거절("괜찮아", "취소", 닫기)
 * 근거: 인용 — docs/SCREEN.md 6장: 주유 질문은 대답이 두 가지(세차까지,
 *       주유만)가 된다
 */
enum class Reply { YES, ALL, ONLY, NO }

/**
 * 작성: 2026-11-13 03:25:00 · 박건준
 * 클래스: Fact
 * 목적: 제안 카드의 근거 칸 하나. 왜 권하는지 숫자로 한눈에 보인다.
 * 인자: icon — 아이콘 이름(fuel, calendar, sun). 화면이 그림으로 바꾼다
 *       text — 칸 글자("연료 18%", "세차 36일 전")
 * 근거: 인용 — docs/SCREEN.md 6장 NHTSA 2/12 규칙: 카드는 제목, 근거 칸 셋,
 *       버튼 둘로 2초 안에 읽히게
 */
data class Fact(val icon: String, val text: String)

/**
 * 작성: 2026-11-13 03:25:00 · 박건준
 * 클래스: Choice
 * 목적: 카드 버튼 하나(고를 것). 첫 번째가 채운 버튼이다.
 * 인자: label — 버튼 글자("주유하고 세차")
 *       reply — 누르면 되는 대답
 *       waitMs — 0보다 크면 이 시간(밀리초)이 지나면 저절로 고른 것으로 본다.
 *       화면은 버튼 안에 시간이 차오르게 그린다
 * 근거: 인용 — docs/SCREEN.md 6장 Pleos 기본 내비 '안내 시작': 기다리는 시간이
 *       버튼에 차오른 뒤 스스로 시작한다
 */
data class Choice(val label: String, val reply: Reply, val waitMs: Long = 0)

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 수정: 2026-11-13 15:00:00 · 박희정
 * 클래스: QuestionRow
 * 목적: 제안 카드의 할 일 칸 하나(세차 모드 다섯 칸).
 * 인자: icon — 아이콘 이름(window, wiper, air, lock, mirror). 화면이 그림으로
 *       바꾼다
 *       text — 칸 글자("창문", "미러")
 *       manual — 사람이 직접 해야 하는 칸이면 true(사이드미러)
 *       note — 칸 아래 작은 글자("직접", "앞으로 옮김"). 없으면 null
 *       done — 이미 한 칸이면 true(시트 맞추는 중에 움직인 축). 화면은 체크로
 *       그린다
 * 근거: 인용 — docs/SCREEN.md 6장 할 일 보여 주기: 가로 5칸 한 줄, "직접"은
 *       사람 몫
 */
data class QuestionRow(
    val icon: String,
    val text: String,
    val manual: Boolean = false,
    val note: String? = null,
    val done: Boolean = false,
)

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: Question
 * 목적: 대답을 기다리는 질문 하나. 화면은 이 값으로 카드를 그리고, 말로 해도
 *       버튼으로 해도 같은 대답(`Reply`)이 된다.
 * 인자: kind — 질문 종류
 *       title — 카드 제목
 *       detail — 설명(최대 2줄). 비어 있으면 그리지 않는다
 *       emphasis — 설명 안에서 굵게 보일 낱말(주유소 이름). 없으면 null
 *       facts — 근거 칸들. 없으면 빈 목록
 *       rows — 할 일 칸들. 없으면 빈 목록
 *       choices — 버튼들. 첫 번째가 채운 버튼. 닫기는 늘 거절(`Reply.NO`)
 * 근거: 인용 — docs/SCREEN.md 6장 카드 짜임(앱 아이콘, 제목, 설명, 근거 칸,
 *       버튼 둘)
 */
data class Question(
    val kind: QuestionKind,
    val title: String,
    val detail: String,
    val emphasis: String? = null,
    val facts: List<Fact> = emptyList(),
    val rows: List<QuestionRow> = emptyList(),
    val choices: List<Choice>,
) {
    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: pick
     * 목적: 대답 값에 맞는 고를 것을 찾는다.
     *       - `YES` — 첫 번째(채운 버튼)
     *       - `ALL`·`ONLY` — 그 값의 버튼. 없으면 거절이 아닌 첫 번째
     *       - `NO` — 거절 버튼. 없으면 null(닫기와 같다)
     * 인자: reply — 대답 값
     * 반환: 고른 버튼. 거절이면 거절 버튼이나 null
     */
    fun pick(reply: Reply): Choice? = when (reply) {
        Reply.YES -> choices.firstOrNull()
        Reply.NO -> choices.firstOrNull { it.reply == Reply.NO }
        else -> choices.firstOrNull { it.reply == reply } ?: choices.firstOrNull { it.reply != Reply.NO }
    }

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: accepts
     * 목적: 그 대답이 받아들인 것인지 본다.
     * 인자: reply — 대답 값
     * 반환: 고른 버튼이 있고 거절이 아니면 true
     */
    fun accepts(reply: Reply): Boolean = pick(reply)?.reply.let { it != null && it != Reply.NO }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 변수: expires
     * 목적: 대답 없이 `Asking.NO_REPLY_MS`가 지나면 닫을 질문이면 true. 스스로
     *       시작하는 버튼(5초)이 있는 질문은 그 버튼이 끝낸다.
     */
    val expires: Boolean get() = kind.closesUnanswered && choices.none { it.waitMs > 0 }
}

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 수정: 2026-11-13 11:00:00 · 박희정
 * 클래스: Asking
 * 목적: 앱이 지금 묻고 있는 질문과 토스트 문장을 한곳에 둔다. 길안내 대화와
 *       루틴 권하기가 같은 것 하나에 질문을 올리고, 대답이 오면 내린다.
 *       - `question` — 대답을 기다리는 질문. null이면 패널 없음
 *       - `notices` — 토스트로 잠깐 보일 문장
 *       - `alerts` — 경고 토스트로 잠깐 보일 문장(주행 중 벨트 풀림)
 *       - `working` — 대답을 받아 처리하는 중(내비가 경유지를 넣는 중). 화면은
 *         그동안 들은 말 판을 내리지 않고 진행 중을 보인다
 *       - "괜찮아요"·닫기로 끝난 종류는 `newTrip()` 전까지 다시 묻지 않는다.
 *         권한 질문이면 그날은 다시 묻지 않는다
 *       - 대답 없이 닫힌 종류(`expire()`)는 이번 길에서만 다시 묻지 않는다
 * 인자: memory — 거절한 날을 남길 곳. 없으면 날은 기억하지 않는다
 *       today — 오늘 날짜. 테스트에서 고정한다
 * 근거: 인용 — docs/SCREEN.md 3장 대화 규칙: "괜찮아요"나 닫기를 누른 제안은
 *       이번 길에서 다시 하지 않는다. docs/SPEC.md 3장 "같은 권유 하루에 한 번",
 *       "대답이 없을 때 20초 뒤 닫는다. 거절로 치지 않는다"
 */
class Asking(
    private val memory: MemoryPort? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    companion object {
        /**
         * 작성: 2026-11-14 16:00:00 · 박건준
         * 변수: NO_REPLY_MS
         * 목적: 질문 카드를 띄운 뒤 대답을 기다리는 시간(밀리초). 지나면 화면이
         *       `expire()`로 닫는다.
         * 근거: 인용 — docs/SPEC.md 3장 "대답이 없을 때 20초 뒤 카드를 닫는다"
         */
        const val NO_REPLY_MS = 20_000L
    }

    /** 지금 질문을 바꾸는 곳. 길안내 대화와 루틴 실행기가 쓴다 */
    private val current = MutableStateFlow<Question?>(null)

    /** 대답을 기다리는 질문(값이 바뀌면 알려주는 상태). null이면 패널 없음 */
    val question: StateFlow<Question?> = current

    /** 토스트 문장을 내보내는 곳. 화면이 늦게 받아도 몇 개는 남겨 둔다 */
    private val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 토스트 문장들(한 번씩 흘러가는 값) */
    val notices: SharedFlow<String> = toasts

    /** 경고 토스트 문장을 내보내는 곳. 차일드락이 쓴다 */
    private val warnings = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 경고 토스트 문장들(한 번씩 흘러가는 값) */
    val alerts: SharedFlow<String> = warnings

    /** 대답을 처리하는 중인지 바꾸는 곳. 길안내 대화가 쓴다 */
    private val busy = MutableStateFlow(false)

    /** 대답을 받아 처리하는 중이면 true(값이 바뀌면 알려주는 상태) */
    val working: StateFlow<Boolean> = busy

    /** 이번 길안내에서 "괜찮아요"로 끝난 질문 종류. `newTrip()`이 비운다 */
    private val declined = mutableSetOf<QuestionKind>()

    /**
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 함수: ask
     * 목적: 질문을 올린다. 이번 길에서 거절한 종류면 올리지 않는다.
     * 인자: q — 올릴 질문
     * 반환: 올렸으면 true. 거절한 종류라 묻지 않아야 하면 false
     */
    @Synchronized
    fun ask(q: Question): Boolean {
        if (q.kind in declined || declinedToday(q.kind)) return false
        current.value = q
        return true
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: declinedToday
     * 목적: 이 종류의 권유를 오늘 거절했는지 본다.
     * 인자: kind — 질문 종류
     * 반환: 오늘 거절했으면 true. 기억이 없으면 false
     */
    fun declinedToday(kind: QuestionKind): Boolean = memory?.declinedOn(kind.name) == today()

    /**
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 함수: waiting
     * 목적: 지금 이 종류의 질문에 대답을 기다리는지 본다.
     * 인자: kind — 질문 종류
     * 반환: 기다리면 true
     */
    fun waiting(kind: QuestionKind): Boolean = current.value?.kind == kind

    /**
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 함수: answered
     * 목적: 받아들인 질문을 내린다. 지금 질문이 그 종류일 때만 내린다.
     * 인자: kind — 질문 종류
     */
    @Synchronized
    fun answered(kind: QuestionKind) {
        if (current.value?.kind == kind) current.value = null
    }

    /**
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: decline
     * 목적: 거절한 질문을 내리고, 이번 길에서는 다시 묻지 않게 남긴다. 앱이
     *       먼저 권한 질문이면 거절한 날도 남겨 그날은 다시 권하지 않는다.
     * 인자: kind — 질문 종류
     */
    @Synchronized
    fun decline(kind: QuestionKind) {
        declined += kind
        if (kind.offer) memory?.recordDeclined(kind.name, today())
        answered(kind)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: expire
     * 목적: 대답 없이 시간이 지난 질문을 내린다. 거절로 치지 않아 날은 남기지
     *       않고, 이번 길에서만 다시 묻지 않는다.
     * 인자: kind — 질문 종류
     * 근거: 인용 — docs/SPEC.md 3장 "대답이 없을 때"
     */
    @Synchronized
    fun expire(kind: QuestionKind) {
        declined += kind
        answered(kind)
    }

    /**
     * 작성: 2026-11-14 16:00:00 · 박건준
     * 함수: forgetDay
     * 목적: 오늘 거절한 권유 기록을 지운다. 시연을 처음으로 돌릴 때 부른다.
     * 미구현: 프로토타입이라 시연을 하루에 여러 번 돌리려고 지운다. 실제
     *       사용에서는 날이 바뀔 때만 다시 권한다
     */
    fun forgetDay() {
        memory?.clearDeclined()
    }

    /**
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 함수: notice
     * 목적: 토스트 문장 하나를 내보낸다. 끝난 일을 알릴 때만 쓴다.
     * 인자: text — 토스트 문장
     * 근거: 인용 — docs/SCREEN.md 1장 위치: 토스트는 작업이 끝났다는 알림에만
     *       쓴다
     */
    fun notice(text: String) {
        toasts.tryEmit(text)
    }

    /**
     * 작성: 2026-11-13 11:00:00 · 박희정
     * 함수: alert
     * 목적: 경고 토스트 문장 하나를 내보낸다. 끝난 일이 아니라 운전자가 알아야
     *       할 일(주행 중 뒷자리 벨트 풀림)을 알린다. 화면은 체크 대신 경고
     *       아이콘으로 그린다.
     * 인자: text — 토스트 문장
     * 근거: 인용 — docs/SCREEN.md 4장 ②-5 "토스트 같은 문장. 주행 중이라 패널
     *       없음". 1장은 토스트를 끝난 일에만 쓰므로 경고는 따로 둔다
     */
    fun alert(text: String) {
        warnings.tryEmit(text)
    }

    /**
     * 작성: 2026-11-13 04:20:00 · 박건준
     * 함수: work
     * 목적: 대답을 처리하는 중인지 알린다. 끝나면(성공이든 실패든) 결과 토스트를
     *       먼저 내고 끈다.
     * 인자: on — 처리하는 중이면 true
     * 근거: 인용 — 2026-11-13 박건준 결정: 내비가 경유지를 넣는 동안(약 9초)
     *       들음 판을 내리지 않고 진행 중을 보인다
     */
    fun work(on: Boolean) {
        busy.value = on
    }

    /**
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 수정: 2026-11-13 04:20:00 · 박건준
     * 함수: newTrip
     * 목적: 새 길안내를 시작하거나 시연을 처음으로 돌릴 때 질문과 거절 기록,
     *       처리 중 표시를 지운다.
     */
    @Synchronized
    fun newTrip() {
        declined.clear()
        current.value = null
        busy.value = false
    }
}
