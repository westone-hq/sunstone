package com.westone.sunstone.routines.knowledge

import com.westone.sunstone.routines.engine.RoutineId

/**
 * 작성: 2026-11-11 17:40:00 · 박희정
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: CardBook
 * 목적: AI 참고 문서(기능 카드) 모음과 찾기.
 *       - `cards` — 기능 카드 목록. 지금은 세차 모드 하나
 *       - `search()` — 말에 들어 있는 별칭·핵심어로 점수를 매겨 상위 3개를 고른다
 * 근거: 인용 — AI 참고 문서 설계의 검색 규칙: 카드가 수십~수백 개라 차 안에서는 별칭·핵심어
 *       매칭으로 충분하다(임베딩 서버 없이 오프라인), LLM에는 상위 3개만 넣는다
 * 미구현: 프로토타입이라 시나리오 ①에 필요한 세차 모드 카드만 있다. 카드를
 *       늘리는 순서(Connect-S 매뉴얼 17개 기능 → 기능 카드)는 아직 하지 않았다
 */
object CardBook {

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 수정: 2026-11-14 03:00:00 · 박건준
     * 변수: TOP_K
     * 목적: 찾기에서 돌려줄 최대 카드 수.
     * 근거: 인용 — AI 참고 문서 설계 "LLM을 쓸 때는 상위 3개 카드만 넣는다(BMW CarExpert도
     *       상위 3문단)"
     */
    private const val TOP_K = 3

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 수정: 2026-11-14 03:00:00 · 박건준
     * 변수: cards
     * 목적: 기능 카드 목록.
     */
    val cards: List<FeatureCard> = listOf(
        FeatureCard(
            id = "wash_mode",
            name = "세차 모드",
            aliases = listOf("세차 모드", "세차모드", "세차할 때 모드", "자동세차 모드", "세차기 모드", "세차 기능"),
            summary = "세차 전에 미러·창문·와이퍼·공조를 한 번에 준비하는 현대차 기능",
            does = "미러 접기, 창문·선루프 닫기, 와이퍼 AUTO 해제, 내기 전환, 트렁크 잠금",
            howTo = "설정 → 주행 및 상황별 차량 모드 → 세차 모드",
            conditions = "시동 ON, 20km/h 이하, 문 닫힘",
            caution = "자동 세차기에서는 N 유지 필요. 시동 끈 뒤 3분 안에 운전석 문을 열면 P로 바뀜",
            appDoes = "창문·와이퍼·내기 순환·문 잠금",
            appGuides = "사이드미러 접기와 N 유지",
            routine = RoutineId.WASH,
            spec = "사양 적용 시",
            source = "AI 참고 문서 설계의 기능 카드 예시(세차 모드)",
            checked = "2026-11-11",
        ),
    )

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: search
     * 목적: 말에 들어 있는 카드 별칭·이름 수로 점수를 매겨 높은 순서로 고른다.
     *       띄어쓰기는 무시한다. 긴 별칭이 맞을수록 점수를 더 준다.
     * 인자: text — 운전자가 한 말
     * 반환: 맞는 카드들, 점수 높은 순으로 최대 `TOP_K`개. 없으면 빈 목록
     * 식: score = Σ len(alias) (alias ⊂ text)
     *     alias — 띄어쓰기를 뺀 별칭·이름, text — 띄어쓰기를 뺀 말
     */
    fun search(text: String): List<FeatureCard> {
        val said = compact(text) // 띄어쓰기를 뺀 말
        return cards
            .map { card ->
                val names = (card.aliases + card.name).map(::compact).distinct() // 띄어쓰기를 뺀 별칭·이름
                card to names.filter { it in said }.sumOf { it.length }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(TOP_K)
            .map { it.first }
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: byId
     * 목적: 카드 ID로 카드를 찾는다.
     * 인자: id — 카드 ID
     * 반환: 카드. 없으면 null
     */
    fun byId(id: String): FeatureCard? = cards.firstOrNull { it.id == id }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: compact
     * 목적: 비교하려고 띄어쓰기와 문장부호를 뺀다.
     */
    private fun compact(text: String) = text.replace(Regex("""[\s.,!?~]"""), "")
}
