package com.westone.sunstone.routines.understand

import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.knowledge.CardBook
import com.westone.sunstone.routines.script.ScriptBook

/** 말 한마디를 앱이 할 일로 바꾼 결과. */
sealed interface VoiceAction {
    data class Run(val request: RunRequest) : VoiceAction
    /** [id]가 null이면 가장 최근에 켠 루틴을 되돌린다. */
    data class Undo(val id: RoutineId?) : VoiceAction
    data class SaveSeat(val name: String) : VoiceAction

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 클래스: Answer
     * 목적: 앱이 권한 것에 대한 대답("응", "응, 세차도 할게", "주유만",
     *       "괜찮아").
     * 인자: reply — 대답 값
     */
    data class Answer(val reply: Reply) : VoiceAction

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 11:06:21 · 박희정
     * 클래스: Navigate
     * 목적: 길안내 요청("파라다이스 호텔 부산까지 길안내 해줘").
     * 인자: destination — 말에서 뽑은 목적지("파라다이스 호텔 부산")
     */
    data class Navigate(val destination: String) : VoiceAction

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 클래스: Ask
     * 목적: 기능을 묻는 질문("세차 모드가 뭐야?"). 차를 바꾸지 않고 기능 카드로
     *       답한다.
     * 인자: cardId — 가장 잘 맞는 기능 카드 ID. 맞는 카드가 없으면 null
     */
    data class Ask(val cardId: String?) : VoiceAction
}

/**
 * "세차할게", "애 태웠어", "엄마 자세로" 같은 말을 루틴으로 바꾼다.
 * 음성 인식(STT·Gleo)이든 화면 입력이든 데모 브로드캐스트든 모두 여기를 지난다.
 * 먼저 대사집(`ScriptBook`) 문장과 똑같은지 보고, 아니면 규칙으로 본다.
 * 규칙은 일부러 단순하게: 띄어쓰기를 빼고 핵심 단어가 들어 있는지 본다.
 */
object PhraseMatcher {

    private val savePatterns = listOf(
        Regex("""(?:자세\s*)?([가-힣A-Za-z0-9]+?)\s*(?:으로|로)\s*저장"""),
        Regex("""([가-힣A-Za-z0-9]+)\s*자세\s*저장"""),
    )
    private val profilePattern = Regex("""([가-힣A-Za-z0-9]+?)\s*(?:자세로|자세\s*로|운전할게|운전한다)""")

    fun match(text: String, source: Source = Source.VOICE): VoiceAction? {
        val compact = text.replace(Regex("""[\s.,!?~]"""), "")
        if (compact.isEmpty()) return null

        ScriptBook.lookup(text)?.let { return it } // → 로직 이동: ScriptBook.lookup()

        if ("저장" in compact) {
            val name = savePatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
            return VoiceAction.SaveSeat(name?.takeUnless { it in GENERIC_WORDS } ?: DEFAULT_NAME)
        }

        undoOf(compact)?.let { return it }

        navigateOf(text)?.let { return it }

        // 질문은 루틴 낱말보다 먼저 본다. "세차 모드가 뭐야?"가 세차 모드를 켜면 안 된다
        if (QUESTION_WORDS.any { it in compact }) {
            return VoiceAction.Ask(CardBook.search(text).firstOrNull()?.id) // → 로직 이동: CardBook.search()
        }

        if (NO_WORDS.any { it in compact }) return VoiceAction.Answer(Reply.NO)
        // "세차도 할게"는 세차 모드 켜기가 아니라 주유 질문의 대답이라 루틴 낱말보다 먼저 본다
        if (ALL_WORDS.any { it in compact }) return VoiceAction.Answer(Reply.ALL)
        if (ONLY_WORDS.any { it in compact }) return VoiceAction.Answer(Reply.ONLY)

        val id = when {
            "세차" in compact -> RoutineId.WASH
            CHILD_WORDS.any { it in compact } -> RoutineId.CHILD_LOCK
            SEAT_WORDS.any { it in compact } -> RoutineId.SEAT_MEMORY
            else -> return if (isYes(compact)) VoiceAction.Answer(Reply.YES) else null
        }
        val profile = if (id == RoutineId.SEAT_MEMORY) {
            profilePattern.find(text)?.groupValues?.get(1)?.let(::dropParticle)?.takeUnless { it in GENERIC_WORDS }
        } else {
            null
        }
        return VoiceAction.Run(RunRequest(id, profile, source))
    }

    private fun undoOf(compact: String): VoiceAction.Undo? = when {
        listOf("세차끝", "세차다했", "세차끝났").any { it in compact } -> VoiceAction.Undo(RoutineId.WASH)
        listOf("애내렸", "아이내렸", "차일드락꺼", "차일드락해제").any { it in compact } -> VoiceAction.Undo(RoutineId.CHILD_LOCK)
        listOf("원래대로", "되돌려", "원상복구").any { it in compact } -> VoiceAction.Undo(null)
        else -> null
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: navigateOf
     * 목적: 길안내 요청이면 목적지를 뽑는다. 루틴 낱말보다 먼저 봐서
     *       "세차장까지 안내해 줘"를 세차 모드가 아닌 길안내로 본다.
     * 인자: text — 운전자가 한 말 그대로(띄어쓰기 포함)
     * 반환: 길안내 요청. 아니면 null
     */
    private fun navigateOf(text: String): VoiceAction.Navigate? {
        val destination = NAVIGATE_PATTERNS.firstNotNullOfOrNull { it.find(text.trim())?.groupValues?.get(1) } // 목적지
        return destination?.trim()?.takeIf { it.isNotEmpty() }?.let(VoiceAction::Navigate)
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: isYes
     * 목적: 짧은 긍정 대답인지 본다. 대답 낱말로 시작하고 뒤에 "요", "해줘"
     *       같은 끝맺음만 붙은 말만 받는다. "어디야"처럼 "어"로 시작하는 다른
     *       말을 긍정으로 잘못 보지 않으려고 끝맺음 목록을 둔다.
     * 인자: compact — 띄어쓰기·문장부호를 뺀 말
     * 반환: 긍정 대답이면 true
     */
    private fun isYes(compact: String): Boolean = YES_WORDS.any { word ->
        compact.startsWith(word) && compact.removePrefix(word) in YES_ENDINGS
    }

    /** "아빠가 운전할게"의 "가"처럼 이름 뒤 조사를 뗀다. */
    private fun dropParticle(name: String): String =
        if (name.length > 1 && name.last() in "가는은이") name.dropLast(1) else name

    private val CHILD_WORDS = listOf("애태웠", "아이태웠", "애탔", "아이탔", "차일드락", "아이탑승")
    private val SEAT_WORDS = listOf("운전바꿀", "운전교대", "자세로", "시트메모리", "운전할게")
    private val GENERIC_WORDS = setOf("지금", "이", "내", "현재", "자세")

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 변수: QUESTION_WORDS
     * 목적: 기능을 묻는 말로 보는 낱말(띄어쓰기를 뺀 꼴). 이 낱말이 있으면 차를
     *       바꾸지 않고 기능 카드로 답한다.
     * 근거: 인용 — Tab 1 2장 참고 문서 단계 "세차 모드가 뭐야?" 같은 질문은 카드로
     *       답한다. 5.1 의도 분석의 질문("이거 뭐야")
     */
    private val QUESTION_WORDS = listOf("뭐야", "뭐예요", "뭐에요", "뭔데", "뭔가요", "무슨기능", "설명해", "어떤기능")

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: NAVIGATE_PATTERNS
     * 목적: 길안내 요청 모양. 첫 묶음이 목적지다.
     *       - "OO까지 길안내 해줘", "OO로 안내해 줘", "OO으로 데려다 줘"
     *       - "OO 가자", "OO까지 가 줘"
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #1 "부산 OO리조트까지 길안내 해줘"
     */
    private val NAVIGATE_PATTERNS = listOf(
        Regex("""^(.+?)\s*(?:까지|으로|로)\s*(?:길\s*안내|안내|데려다|가\s*줘|가\s*자)"""),
        Regex("""^(.+?)\s*(?:가자|가\s*줘)\s*[.!?~]*$"""),
    )

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 변수: NO_WORDS
     * 목적: 거절로 보는 말. 루틴 이름보다 먼저 본다("세차 안 해"는 거절).
     *       "취소"는 곧 켜질 세차 모드를 멈추는 말이다. "됐어"는 시트 맞추기에서
     *       "다 맞췄다"는 뜻이라 넣지 않는다.
     * 근거: 인용 — Tab 1 6.1 옆자리 팀: 부정어 먼저, "괜찮아요"는 거절.
     *       docs/SCREEN.md 6장 6단계 "취소"라고 말해도 된다
     */
    private val NO_WORDS = listOf("아니", "괜찮", "안해", "안할", "싫어", "하지마", "나중에", "필요없", "취소")

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 변수: ALL_WORDS, ONLY_WORDS
     * 목적: 주유 질문에 세차까지 하겠다는 말, 앞의 것(주유)만 하겠다는 말
     *       (띄어쓰기를 뺀 꼴).
     * 근거: 인용 — docs/SCREEN.md 6장 3단계 "응, 세차도 할게", "주유만"
     */
    private val ALL_WORDS = listOf("세차도", "세차까지", "둘다")
    private val ONLY_WORDS = listOf("주유만", "기름만", "경유지만")

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: YES_WORDS
     * 목적: 긍정 대답이 시작하는 낱말. 긴 것부터 둬서 "그래요"가 "그래"로 먼저
     *       걸리게 한다.
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #2 "그래", #4 "응"
     */
    private val YES_WORDS = listOf("그렇게", "오케이", "그래", "좋아", "부탁", "ㅇㅇ", "응", "웅", "어", "네", "예")

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: YES_ENDINGS
     * 목적: 긍정 낱말 뒤에 붙어도 되는 끝맺음. 이 밖의 말이 붙으면 대답으로
     *       보지 않는다.
     * 근거: 미정 — 시연 대사와 흔한 변형만 넣었다
     */
    private val YES_ENDINGS = setOf("", "요", "해", "해요", "해줘", "해주세요", "해줘요", "그래", "좋아", "맞춰줘", "할게")
    private const val DEFAULT_NAME = "나"
}
