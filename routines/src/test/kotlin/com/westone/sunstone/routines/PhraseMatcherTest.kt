package com.westone.sunstone.routines

import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.RoutineId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhraseMatcherTest {

    private fun run(text: String) = (PhraseMatcher.match(text) as VoiceAction.Run).request

    @Test fun `세차`() = assertEquals(RoutineId.WASH, run("세차할게").id)

    @Test fun `세차 끝은 되돌리기`() =
        assertEquals(VoiceAction.Undo(RoutineId.WASH), PhraseMatcher.match("세차 끝났어"))

    @Test fun `아이 탑승`() = assertEquals(RoutineId.CHILD_LOCK, run("애 태웠어").id)

    @Test fun `누구 자세로`() {
        val r = run("엄마 자세로 맞춰줘")
        assertEquals(RoutineId.SEAT_MEMORY, r.id)
        assertEquals("엄마", r.profile)
    }

    @Test fun `누가 운전할게`() = assertEquals("아빠", run("아빠가 운전할게").profile)

    @Test fun `자세 저장`() {
        assertEquals(VoiceAction.SaveSeat("엄마"), PhraseMatcher.match("지금 자세 엄마로 저장해줘"))
        assertEquals(VoiceAction.SaveSeat("아빠"), PhraseMatcher.match("아빠 자세 저장"))
        assertEquals(VoiceAction.SaveSeat("나"), PhraseMatcher.match("지금 자세 저장해줘"))
    }

    @Test fun `원래대로는 최근 루틴`() = assertEquals(VoiceAction.Undo(null), PhraseMatcher.match("원래대로 해줘"))

    @Test fun `모르는 말`() = assertNull(PhraseMatcher.match("오늘 날씨 어때"))
}
