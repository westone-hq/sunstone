package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 클래스: ChildLockPart
 * 목적: 차일드락이 맞추는 대상 하나와 그 대상을 말할 때 쓰는 낱말들
 *       (`ReportPart`). 끝에 한 번에 말하는 문장을 만들 때 쓴다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ② #2 "뒷창문 잠갔어요." 문·공조·블라인드는
 *       대사집에 없어 2026-11-12 박희정이 넣기로 정했다(미정)
 */
enum class ChildLockPart(
    override val target: String,
    override val doing: String,
    override val did: String,
    override val please: String,
) : ReportPart {
    REAR_WINDOW_LOCK("뒷창문", "뒷창문 잠그고", "뒷창문 잠갔어요", "잠가"),
    DOOR_LOCK("문", "문 잠그고", "문 잠갔어요", "잠가"),
    CLIMATE("공조", "공조 켜고", "공조 켰어요", "켜"),
    REAR_BLIND("뒷유리 블라인드", "뒷유리 블라인드 올리고", "뒷유리 블라인드 올렸어요", "올려"),
}

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 클래스: ChildLockLines
 * 목적: 차일드락이 하는 말을 한곳에 모은다. 문장을 바꿀 때는 이 파일만
 *       고친다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ② #1~#5 대사
 */
object ChildLockLines {

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: BOARDED
     * 목적: 뒷좌석에 사람이 타고 문이 닫혔을 때 차일드락을 권하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #1
     */
    const val BOARDED = "뒷좌석에 누가 탔네요. 차일드락 맞춰 드릴까요?"

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: CONTINUE_HOME
     * 목적: 오늘 차일드락을 쓴 적이 있고 집으로 길안내 중일 때, 뒷좌석에 다시
     *       타면 권하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #5
     */
    const val CONTINUE_HOME = "집에 가시는 길이네요. 차일드락 이어서 할까요?"

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: DOOR_CHILD_LOCK
     * 목적: 뒷문 차일드락을 걸어 달라는 안내. 앱이 걸 수 없어 늘 말한다.
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #2 괄호 문장. 측정 — SDK `Door`에
     *       차일드락이 없고 AAOS 속성은 권한이 없다(docs/api-spike.md 차일드락
     *       행)
     */
    const val DOOR_CHILD_LOCK = "뒷문 차일드락은 문 안쪽 스위치로 걸어 주세요."

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: BELT_OFF
     * 목적: 달리는 중 뒷좌석 벨트가 풀렸을 때 하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ② #3
     */
    const val BELT_OFF = "뒷좌석 벨트가 풀렸어요."

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: BLOCKED_MOVING
     * 목적: 달리는 중에 차일드락을 부탁받았을 때 하는 말.
     * 근거: 미정 — 2026-11-08 박건준 초안 그대로
     */
    const val BLOCKED_MOVING = "차가 멈춘 뒤에 할게요."
}
