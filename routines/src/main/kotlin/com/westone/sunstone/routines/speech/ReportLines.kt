package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 클래스: ReportPart
 * 목적: 루틴이 맞추는 대상 하나를 말할 때 쓰는 낱말들. 끝에 한 번에 말하는
 *       문장(`ReportLines.report()`)을 만들 때 쓴다.
 *       - `target` — 대상 이름("사이드미러는 직접 …"의 앞부분)
 *       - `doing` — 이어 말할 때 꼴("창문 닫고")
 *       - `did` — 문장을 끝낼 때 꼴("문 잠갔어요")
 *       - `please` — 사람에게 부탁할 때 동사("직접 접어 주세요"의 "접어")
 */
interface ReportPart {
    val target: String
    val doing: String
    val did: String
    val please: String
}

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 클래스: ReportLines
 * 목적: 루틴을 마친 뒤 한 번에 할 말의 틀. 세차 모드(`WashLines`)에서 옮겨 와
 *       차일드락과 함께 쓴다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #4 "창문 닫고, 와이퍼 끄고, 내기 순환으로
 *       바꾸고, 문 잠갔어요. 사이드미러는 직접 접어 주세요."
 */
object ReportLines {

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: report
     * 목적: 루틴을 마친 뒤 한 번에 할 말을 만든다.
     *       1. 앱이 한 대상을 "창문 닫고, …, 문 잠갔어요."로 잇는다
     *       2. 사람이 해야 하는 대상마다 "사이드미러는 직접 접어 주세요."를 붙인다
     *       3. 덧붙일 안내가 있으면 마지막에 붙인다
     * 인자: done — 앱이 한 대상, 실행한 순서대로
     *       manual — 사람이 해야 하는 대상, 실행한 순서대로
     *       extra — 끝에 덧붙일 안내 문장들
     * 반환: 한 번에 말할 문장. 할 말이 없으면 빈 글자
     */
    fun report(done: List<ReportPart>, manual: List<ReportPart>, extra: List<String> = emptyList()): String {
        val sentences = mutableListOf<String>() // 차례로 이을 문장
        if (done.isNotEmpty()) {
            sentences += (done.dropLast(1).map { it.doing } + done.last().did).joinToString(", ") + "."
        }
        manual.forEach { sentences += please(it) }
        sentences += extra
        return sentences.joinToString(" ")
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: please
     * 목적: 앱이 못 한 대상을 사람에게 부탁하는 말("사이드미러는 직접 접어
     *       주세요."). 끝에 한 번에 말할 때와 단계 실패 안내가 함께 쓴다.
     * 인자: part — 대상
     * 반환: 문장
     */
    fun please(part: ReportPart): String = "${withTopic(part.target)} 직접 ${part.please} 주세요."
}
