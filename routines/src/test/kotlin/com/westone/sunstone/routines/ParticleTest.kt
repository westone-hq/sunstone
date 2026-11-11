package com.westone.sunstone.routines

import com.westone.sunstone.routines.speech.withObject
import com.westone.sunstone.routines.speech.withTopic
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 클래스: ParticleTest
 * 목적: 받침에 따라 은/는, 을/를을 맞게 붙이는지 본다. 이전에는 "선루프 뒤을
 *       닫아 주세요"처럼 틀렸다.
 */
class ParticleTest {

    @Test fun `받침 없으면 는 를`() {
        assertEquals("사이드미러는", withTopic("사이드미러"))
        assertEquals("선루프 뒤를", withObject("선루프 뒤"))
    }

    @Test fun `받침 있으면 은 을`() {
        assertEquals("창문은", withTopic("창문"))
        assertEquals("운전석 창문을", withObject("운전석 창문"))
    }

    @Test fun `한글이 아니면 받침 없음으로 본다`() = assertEquals("ABC는", withTopic("ABC"))
}
