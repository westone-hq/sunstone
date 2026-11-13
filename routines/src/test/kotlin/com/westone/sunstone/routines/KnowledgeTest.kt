package com.westone.sunstone.routines

import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.knowledge.CardBook
import com.westone.sunstone.routines.speech.CardLines
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 작성: 2026-11-11 17:40:00 · 박희정
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: KnowledgeTest
 * 목적: 기능 카드 찾기와 "이거 뭐야" 질문 답을 본다(AI 참고 문서).
 *       질문 세트는 카드마다 실제로 물을 법한 말로 만든다.
 */
class KnowledgeTest {

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: 별칭으로 세차 모드 카드를 찾는다
     * 목적: 매뉴얼 이름이 아닌 별칭으로 물어도 세차 모드 카드가 첫째로 나오는지
     *       본다(질문 세트).
     */
    @Test
    fun `별칭으로 세차 모드 카드를 찾는다`() = listOf(
        "세차 모드가 뭐야?",
        "세차할 때 모드 뭐예요",
        "자동세차 모드 어떤 기능이야",
        "세차기 모드 설명해 줘",
        "세차모드 뭔데",
    ).forEach { question ->
        assertEquals(question, "wash_mode", CardBook.search(question).firstOrNull()?.id)
        assertEquals(question, VoiceAction.Ask("wash_mode"), PhraseMatcher.match(question))
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: 세차 모드 질문 답은 카드 칸 그대로다
     * 목적: 한 줄 설명과 앱이 하는 것·안내하는 것을 카드 그대로 말하는지 본다.
     *       사이드미러를 앱이 접는다고 말하면 안 된다.
     */
    @Test
    fun `세차 모드 질문 답은 카드 칸 그대로다`() {
        val card = CardBook.byId("wash_mode")!! // 세차 모드 카드
        assertEquals(RoutineId.WASH, card.routine)
        assertEquals(
            "세차 모드는 세차 전에 미러·창문·와이퍼·공조를 한 번에 준비하는 현대차 기능이에요. " +
                "창문·와이퍼·내기 순환·문 잠금은 제가 맞춰 드리고, 사이드미러 접기와 N 유지는 말로 안내해 드려요.",
            CardLines.answer(card),
        )
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: 카드가 없는 기능을 물으면 루틴을 켜지 않는다
     * 목적: "차일드락이 뭐야?"처럼 카드가 없는 기능을 물어도 차일드락을 켜지 않고
     *       질문으로 받는지 본다. 부탁("세차할게")은 그대로 루틴이다.
     */
    @Test
    fun `카드가 없는 기능을 물으면 루틴을 켜지 않는다`() {
        assertEquals(VoiceAction.Ask(null), PhraseMatcher.match("차일드락이 뭐야?"))
        assertTrue(PhraseMatcher.match("세차할게") is VoiceAction.Run)
        assertTrue(CardBook.search("오늘 날씨 어때").isEmpty())
    }
}
