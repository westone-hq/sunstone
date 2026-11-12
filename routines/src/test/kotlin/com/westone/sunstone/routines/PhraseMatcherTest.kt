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

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 긍정 대답
     * 목적: 대사집의 "응", "그래"와 흔한 변형을 긍정으로 알아듣는지 본다.
     */
    @Test fun `긍정 대답`() = listOf("응", "그래", "네", "네 맞춰줘", "좋아요", "응 해줘", "그래요", "부탁해").forEach {
        assertEquals(it, VoiceAction.Answer(yes = true), PhraseMatcher.match(it))
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 거절 대답
     * 목적: 부정어를 루틴 이름보다 먼저 봐서 "세차 안 해"도 거절로 알아듣는지
     *       본다.
     */
    @Test fun `거절 대답`() = listOf("아니", "괜찮아", "아니요 괜찮아요", "세차 안 해", "나중에").forEach {
        assertEquals(it, VoiceAction.Answer(yes = false), PhraseMatcher.match(it))
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: 길안내 요청
     * 목적: 대사집 #1과 흔한 변형에서 목적지를 뽑는지 본다. "세차장까지 가자"는
     *       세차 모드가 아니라 길안내다.
     */
    @Test fun `길안내 요청`() = mapOf(
        "파라다이스 호텔 부산까지 길안내 해줘" to "파라다이스 호텔 부산",
        "강남역으로 안내해 줘" to "강남역",
        "서울역까지 가 줘" to "서울역",
        "세차장까지 가자" to "세차장",
    ).forEach { (text, destination) ->
        assertEquals(text, VoiceAction.Navigate(destination), PhraseMatcher.match(text))
    }

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: 대답이 아닌 말
     * 목적: "어"로 시작하는 다른 말을 긍정으로 잘못 보지 않는지 본다.
     */
    @Test fun `대답이 아닌 말`() = listOf("어디야", "네비 켜줘", "응급실").forEach {
        assertNull(it, PhraseMatcher.match(it))
    }
}
