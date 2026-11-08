package com.westone.sunstone.routines.understand

import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Source

/** 말 한마디를 앱이 할 일로 바꾼 결과. */
sealed interface VoiceAction {
    data class Run(val request: RunRequest) : VoiceAction
    /** [id]가 null이면 가장 최근에 켠 루틴을 되돌린다. */
    data class Undo(val id: RoutineId?) : VoiceAction
    data class SaveSeat(val name: String) : VoiceAction
}

/**
 * "세차할게", "애 태웠어", "엄마 자세로" 같은 말을 루틴으로 바꾼다.
 * 음성 인식(STT·Gleo)이든 화면 입력이든 데모 브로드캐스트든 모두 여기를 지난다.
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

        if ("저장" in compact) {
            val name = savePatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
            return VoiceAction.SaveSeat(name?.takeUnless { it in GENERIC_WORDS } ?: DEFAULT_NAME)
        }

        undoOf(compact)?.let { return it }

        val id = when {
            "세차" in compact -> RoutineId.WASH
            CHILD_WORDS.any { it in compact } -> RoutineId.CHILD_LOCK
            SEAT_WORDS.any { it in compact } -> RoutineId.SEAT_MEMORY
            else -> return null
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

    /** "아빠가 운전할게"의 "가"처럼 이름 뒤 조사를 뗀다. */
    private fun dropParticle(name: String): String =
        if (name.length > 1 && name.last() in "가는은이") name.dropLast(1) else name

    private val CHILD_WORDS = listOf("애태웠", "아이태웠", "애탔", "아이탔", "차일드락", "아이탑승")
    private val SEAT_WORDS = listOf("운전바꿀", "운전교대", "자세로", "시트메모리", "운전할게")
    private val GENERIC_WORDS = setOf("지금", "이", "내", "현재", "자세")
    private const val DEFAULT_NAME = "나"
}
