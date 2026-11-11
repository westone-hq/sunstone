package com.westone.sunstone.routines.speech

import com.westone.sunstone.routines.knowledge.FeatureCard

/**
 * 작성: 2026-11-11 17:40:00 · 박희정
 * 클래스: CardLines
 * 목적: 기능 카드로 "이거 뭐야" 질문에 답하는 문장 틀. 카드 칸을 그대로 채우고
 *       지어내지 않는다.
 * 근거: 인용 — Tab 1 2장 참고 문서 단계: "세차 모드가 뭐야?" 같은 질문은 카드로
 *       답한다. 3.2 카드 쓰기 규칙: 앱이 못 하는 것을 명시해 "제가 미러 접어
 *       드릴게요" 같은 거짓말을 막는다
 */
object CardLines {

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: answer
     * 목적: 카드 하나로 답을 만든다.
     *       1. "{이름}은/는 {한 줄 설명}이에요/예요."
     *       2. "{앱이 하는 것}은/는 제가 맞춰 드리고, {안내하는 것}은/는 말로 안내해 드려요."
     * 인자: card — 기능 카드
     * 반환: 문장
     */
    fun answer(card: FeatureCard): String =
        "${withTopic(card.name)} ${withCopula(card.summary)}. " +
            "${withTopic(card.appDoes)} 제가 맞춰 드리고, ${withTopic(card.appGuides)} 말로 안내해 드려요."

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 변수: NO_CARD
     * 목적: 묻는 기능의 카드가 없을 때 하는 말.
     * 근거: 미정 — 대사집에 없는 경우
     */
    const val NO_CARD = "그 기능은 아직 잘 몰라요."
}
