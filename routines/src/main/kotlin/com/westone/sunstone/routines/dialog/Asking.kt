package com.westone.sunstone.routines.dialog

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 클래스: QuestionKind
 * 목적: 앱이 먼저 묻는 질문의 종류. 같은 길안내 안에서 "괜찮아요"로 끝난
 *       질문을 다시 묻지 않을 때 이 값으로 가린다.
 *       - `FUEL_STOP` — 주유소를 경유지로 넣을까요(길안내 대화)
 *       - `WASH_RECOMMEND` — 거기서 세차도 할까요(길안내 대화)
 *       - `WASH_MODE` — 세차 모드 켤까요(세차 모드 권하기)
 * 근거: 인용 — docs/SCREEN.md 3장 패널 세 가지(연료 제안, 세차 추천, 세차 모드)
 */
enum class QuestionKind { FUEL_STOP, WASH_RECOMMEND, WASH_MODE }

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 클래스: QuestionRow
 * 목적: 질문 패널의 항목 한 줄.
 * 인자: icon — 아이콘 이름(fuel, wash, calendar, sun, window, wiper, air, lock,
 *       mirror). 화면이 그림으로 바꾼다
 *       text — 줄 글자("말죽거리주유소", "창문 닫기")
 *       manual — 사람이 직접 해야 하는 줄이면 true(사이드미러)
 *       note — 줄 오른쪽 작은 글자("세차 가능", "직접 접어 주세요"). 없으면 null
 * 근거: 인용 — docs/SCREEN.md 1장 "칸이 뜻하는 것": "직접 ~ 주세요"는 제안할
 *       때부터 미리 보여 준다
 */
data class QuestionRow(val icon: String, val text: String, val manual: Boolean = false, val note: String? = null)

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 클래스: Question
 * 목적: 대답을 기다리는 질문 하나. 화면은 이 값으로 패널을 그리고, 말로 해도
 *       버튼으로 해도 같은 대답이 된다.
 * 인자: kind — 질문 종류
 *       title — 패널 제목
 *       detail — 설명줄. 비어 있으면 그리지 않는다
 *       rows — 항목 줄들
 *       yesLabel — 채운 버튼(받아들이기)
 *       noLabel — 흰 버튼(괜찮아요)
 */
data class Question(
    val kind: QuestionKind,
    val title: String,
    val detail: String,
    val rows: List<QuestionRow>,
    val yesLabel: String,
    val noLabel: String,
)

/**
 * 작성: 2026-11-12 18:10:00 · 박건준
 * 클래스: Asking
 * 목적: 앱이 지금 묻고 있는 질문과 토스트 문장을 한곳에 둔다. 길안내 대화와
 *       루틴 권하기가 같은 것 하나에 질문을 올리고, 대답이 오면 내린다.
 *       - `question` — 대답을 기다리는 질문. null이면 패널 없음
 *       - `notices` — 토스트로 잠깐 보일 문장
 *       - "괜찮아요"·닫기로 끝난 종류는 `newTrip()` 전까지 다시 묻지 않는다
 * 근거: 인용 — docs/SCREEN.md 3장 대화 규칙: "괜찮아요"나 닫기를 누른 제안은
 *       이번 길에서 다시 하지 않는다
 */
class Asking {

    /** 지금 질문을 바꾸는 곳. 길안내 대화와 루틴 실행기가 쓴다 */
    private val current = MutableStateFlow<Question?>(null)

    /** 대답을 기다리는 질문(값이 바뀌면 알려주는 상태). null이면 패널 없음 */
    val question: StateFlow<Question?> = current

    /** 토스트 문장을 내보내는 곳. 화면이 늦게 받아도 몇 개는 남겨 둔다 */
    private val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 토스트 문장들(한 번씩 흘러가는 값) */
    val notices: SharedFlow<String> = toasts

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
        if (q.kind in declined) return false
        current.value = q
        return true
    }

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
     * 함수: decline
     * 목적: 거절한 질문을 내리고, 이번 길에서는 다시 묻지 않게 남긴다.
     * 인자: kind — 질문 종류
     */
    @Synchronized
    fun decline(kind: QuestionKind) {
        declined += kind
        answered(kind)
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
     * 작성: 2026-11-12 18:10:00 · 박건준
     * 함수: newTrip
     * 목적: 새 길안내를 시작하거나 시연을 처음으로 돌릴 때 질문과 거절 기록을
     *       지운다.
     */
    @Synchronized
    fun newTrip() {
        declined.clear()
        current.value = null
    }
}
