package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 클래스: WashPart
 * 목적: 세차 모드가 맞추는 대상 하나와 그 대상을 말할 때 쓰는 낱말들. 끝에
 *       한 번에 말하는 문장([WashLines.report])을 만들 때 쓴다.
 * 인자: target — 대상 이름("사이드미러는 직접 …"의 앞부분)
 *       doing — 이어 말할 때 꼴("창문 닫고")
 *       did — 문장을 끝낼 때 꼴("문 잠갔어요")
 *       please — 사람에게 부탁할 때 동사("직접 접어 주세요"의 "접어")
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #4 "창문 닫고, 와이퍼 끄고, 내기 순환으로
 *       바꾸고, 문 잠갔어요. 사이드미러는 직접 접어 주세요."
 */
enum class WashPart(val target: String, val doing: String, val did: String, val please: String) {
    WINDOWS("창문", "창문 닫고", "창문 닫았어요", "닫아"),
    SUNROOF("선루프", "선루프 닫고", "선루프 닫았어요", "닫아"),
    WIPER("와이퍼", "와이퍼 끄고", "와이퍼 껐어요", "꺼"),
    RECIRCULATION("내기 순환", "내기 순환으로 바꾸고", "내기 순환으로 바꿨어요", "켜"),
    DOOR_LOCK("문", "문 잠그고", "문 잠갔어요", "잠가"),
    MIRROR("사이드미러", "사이드미러 접고", "사이드미러 접었어요", "접어"),
}

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 클래스: WashLines
 * 목적: 세차 모드가 하는 말을 한곳에 모은다. 문장을 바꿀 때는 이 파일만
 *       고친다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #3~#5 대사
 */
object WashLines {

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: ARRIVED_GAS_STATION
     * 목적: 주유소에 도착해 멈췄을 때 세차 모드를 권하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #3을 2026-11-11 박희정이 고쳤다
     *       ("주유소에 도착했어요." → "세차하시나요?")
     */
    const val ARRIVED_GAS_STATION = "세차하시나요? 세차하실 거면 세차 모드로 맞춰 드릴까요?"

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: ARRIVED_CAR_WASH
     * 목적: 세차장에 도착해 멈췄을 때 세차 모드를 권하는 말.
     * 근거: 미정 — Tab 1 대사집에 세차장 장면이 없어 주유소 문장에 맞춰 정했다
     */
    const val ARRIVED_CAR_WASH = "세차장에 도착했어요. 세차 모드로 맞춰 드릴까요?"

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: N_HOLD
     * 목적: 자동 세차기에서 N 유지를 켜 달라는 안내. 세차장에서만 한다.
     * 근거: 인용 — 현대 세차 모드 기능 카드(Tab 1 3.2) 주의: 자동 세차기에서는
     *       N 유지 필요, 시동 끈 뒤 3분 안에 운전석 문을 열면 P로 바뀜
     */
    const val N_HOLD =
        "자동 세차기라면 N에서 스티어링 휠 버튼을 길게 눌러 N 유지를 켜 주세요. " +
            "시동을 끈 뒤 3분 안에는 운전석 문을 열지 마세요."

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: FINISHED
     * 목적: 세차를 마치고 출발해서 원래대로 돌린 뒤 하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #5
     */
    const val FINISHED = "세차 모드 끝낼게요. 원래대로 돌려놨어요."

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 함수: report
     * 목적: 세차 모드를 마친 뒤 한 번에 할 말을 만든다.
     *       1. 앱이 한 대상을 "창문 닫고, …, 문 잠갔어요."로 잇는다
     *       2. 사람이 해야 하는 대상마다 "사이드미러는 직접 접어 주세요."를 붙인다
     *       3. 덧붙일 안내(N 유지)가 있으면 마지막에 붙인다
     * 인자: done — 앱이 한 대상, 실행한 순서대로
     *       manual — 사람이 해야 하는 대상, 실행한 순서대로
     *       extra — 끝에 덧붙일 안내 문장들
     * 반환: 한 번에 말할 문장. 할 말이 없으면 빈 글자
     */
    fun report(done: List<WashPart>, manual: List<WashPart>, extra: List<String> = emptyList()): String {
        val sentences = mutableListOf<String>() // 차례로 이을 문장
        if (done.isNotEmpty()) {
            sentences += (done.dropLast(1).map { it.doing } + done.last().did).joinToString(", ") + "."
        }
        manual.forEach { sentences += "${withTopic(it.target)} 직접 ${it.please} 주세요." }
        sentences += extra
        return sentences.joinToString(" ")
    }
}
