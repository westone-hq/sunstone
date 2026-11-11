package com.westone.sunstone.routines.knowledge

import com.westone.sunstone.routines.engine.RoutineId

/**
 * 작성: 2026-11-11 17:40:00 · 박희정
 * 클래스: FeatureCard
 * 목적: AI 참고 문서의 기능 카드 하나(Tab 1 3.2 기능 카드 형식). 현대차 기능
 *       하나의 하는 일·켜는 법·조건·주의와 앱이 할 수 있는 것을 담는다. "이거
 *       뭐야" 질문의 답과 안내 문장의 근거로 쓴다.
 * 인자: id — 카드 ID
 *       name — 이름("세차 모드")
 *       aliases — 부르는 말(별칭). 검색이 찾게 되는 건 대부분 별칭 덕분이다
 *       summary — 한 줄 설명
 *       does — 하는 일
 *       howTo — 켜는 법
 *       conditions — 조건(매뉴얼 숫자 그대로)
 *       caution — 주의
 *       appDoes — 앱이 직접 하는 것(말할 때 쓰는 꼴)
 *       appGuides — 앱이 할 수 없어 말로 안내하는 것(말할 때 쓰는 꼴)
 *       routine — 관련 루틴
 *       spec — 사양
 *       source — 출처
 *       checked — 확인한 날짜(YYYY-MM-DD)
 * 근거: 인용 — Tab 1 3.2 기능 카드 형식과 카드 쓰기 규칙(한 카드에 기능 하나,
 *       별칭을 넓게, 숫자·조건은 매뉴얼 그대로, 앱이 못 하는 것을 명시)
 */
data class FeatureCard(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val summary: String,
    val does: String,
    val howTo: String,
    val conditions: String,
    val caution: String,
    val appDoes: String,
    val appGuides: String,
    val routine: RoutineId?,
    val spec: String,
    val source: String,
    val checked: String,
)
