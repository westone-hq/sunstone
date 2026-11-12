package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 수정: 2026-11-12 18:18:00 · 박건준
 * 클래스: WashPart
 * 목적: 세차 모드가 맞추는 대상 하나와 그 대상을 말할 때 쓰는 낱말들. 사람이
 *       해야 하는 대상을 "사이드미러는 직접 접어 주세요."로 말할 때 쓴다.
 *       앱이 한 대상은 나열하지 않는다(패널 목록이 보여 준다).
 * 인자: target — 대상 이름("사이드미러는 직접 …"의 앞부분)
 *       please — 사람에게 부탁할 때 동사("직접 접어 주세요"의 "접어")
 * 근거: 인용 — docs/SCREEN.md 1장 말과 화면 나누기: 말은 1~2문장, 무엇을
 *       바꿨는지는 패널 목록이 보여 준다
 */
enum class WashPart(val target: String, val please: String) {
    WINDOWS("창문", "닫아"),
    SUNROOF("선루프", "닫아"),
    WIPER("와이퍼", "꺼"),
    RECIRCULATION("내기 순환", "켜"),
    DOOR_LOCK("문", "잠가"),
    MIRROR("사이드미러", "접어"),
}

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 수정: 2026-11-12 18:18:00 · 박건준
 * 클래스: WashLines
 * 목적: 세차 모드가 하는 말과 패널 글자를 한곳에 모은다. 문장을 바꿀 때는 이
 *       파일만 고친다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #3~#5 대사, docs/SCREEN.md 3장 ①-8~①-11
 */
object WashLines {

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: ASK_MODE
     * 목적: 예약한 주유소의 세차 입구에 섰을 때 세차 모드를 묻는 말. 패널
     *       제목과 같다.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-9. 예약했으면 세차하는지 다시 묻지 않고
     *       켤지만 한 번 확인한다(2장 다듬을 점 1)
     */
    const val ASK_MODE = "세차 모드 켤까요?"

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: ASK_MODE_DETAIL, ASK_MODE_YES, ASK_MODE_NO
     * 목적: 세차 모드 질문 패널의 설명줄, 채운 버튼, 흰 버튼.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-9, 1장 시안 버튼
     */
    const val ASK_MODE_DETAIL = "켜면 아래처럼 맞춰 드려요."
    const val ASK_MODE_YES = "세차 모드 켜기"
    const val ASK_MODE_NO = "괜찮아요"

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: WAIT_FOR_ENTRANCE
     * 목적: 예약한 주유소에 처음 섰을 때(주유 정차) 말 없이 보일 토스트.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-8. 주유·결제를 방해하지 않으려고 말은
     *       하지 않는다
     */
    const val WAIT_FOR_ENTRANCE = "세차 입구에서 맞춰 드릴게요"

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: STEP_WINDOWS, STEP_WIPER, STEP_RECIRCULATION, STEP_DOOR_LOCK,
     *       STEP_MIRROR, STEP_SUNROOF
     * 목적: 세차 모드 단계 이름. 질문 패널 줄과 진행 패널 줄에 같은 글자로
     *       보인다. 창문 네 개는 한 줄("창문 닫기")로 묶여 보이게 같은 이름을
     *       쓴다.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-9 항목 5줄(1차 시안 그대로)
     */
    const val STEP_WINDOWS = "창문 닫기"
    const val STEP_WIPER = "와이퍼 끄기"
    const val STEP_RECIRCULATION = "내기 순환"
    const val STEP_DOOR_LOCK = "문 잠금"
    const val STEP_MIRROR = "사이드미러 접기"
    const val STEP_SUNROOF = "선루프 닫기"

    /**
     * 작성: 2026-11-12 19:40:00 · 박건준
     * 변수: DONE_WINDOWS, DONE_WIPER, DONE_RECIRCULATION, DONE_DOOR_LOCK,
     *       DONE_SUNROOF
     * 목적: 단계를 마친 뒤 진행 패널 줄 글자(끝난 꼴, "~어요" 없이 짧게).
     *       사이드미러는 사람이 할 일이라 이름 그대로 둔다.
     * 근거: 인용 — 시안 sunstone-ui.html 준비됨 상태 줄("창문 닫음", "와이퍼 끔")
     */
    const val DONE_WINDOWS = "창문 닫음"
    const val DONE_WIPER = "와이퍼 끔"
    const val DONE_RECIRCULATION = "내기 순환"
    const val DONE_DOOR_LOCK = "문 잠금"
    const val DONE_SUNROOF = "선루프 닫음"

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: MIRROR_HINT
     * 목적: 사이드미러 줄 오른쪽에 미리 보일 안내. 앱이 접을 수 없어 사람이
     *       해야 한다.
     * 근거: 인용 — docs/SCREEN.md 1장 "직접 ~ 주세요"는 제안할 때부터 미리
     *       보여 준다
     */
    const val MIRROR_HINT = "직접 접어 주세요"

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 변수: ARRIVED_CAR_WASH
     * 목적: 세차장에 도착해 멈췄을 때 세차 모드를 권하는 말.
     * 근거: 미정 — Tab 1 대사집에 세차장 장면이 없어 주유소 문장에 맞춰 정했다
     */
    const val ARRIVED_CAR_WASH = "세차장에 도착했어요. 세차 모드로 맞춰 드릴까요?"

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 변수: BLOCKED_MOVING, BLOCKED_GEAR, BLOCKED_DOOR
     * 목적: 세차 모드를 시작하지 않을 때(또는 하다가 멈출 때) 그 이유를 말한다.
     *       달리는 중, P·N이 아님, 문이 열림.
     * 근거: 인용 — 세차 모드 기능 카드(Tab 1 3.2) 조건. 앞의 두 문장은
     *       2026-11-08 박건준 초안 그대로
     */
    const val BLOCKED_MOVING = "차가 멈춘 뒤에 할게요."
    const val BLOCKED_GEAR = "P나 N에 두시면 시작할게요."
    const val BLOCKED_DOOR = "문을 모두 닫으시면 시작할게요."

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
     * 수정: 2026-11-12 18:18:00 · 박건준
     * 변수: FINISHED
     * 목적: 세차를 마치고 D로 바꿔 원래대로 돌린 뒤 하는 말. 출발 전에 미러를
     *       확인하게 한다.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-11
     */
    const val FINISHED = "세차 모드 끝냈어요. 사이드미러 펴졌는지 확인해 주세요."

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: RESTORED_NOTICE
     * 목적: 원래대로 돌린 뒤 보일 토스트.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-11
     */
    const val RESTORED_NOTICE = "원래대로 돌려놨어요"

    /**
     * 작성: 2026-11-12 18:18:00 · 박건준
     * 변수: MODE_SET
     * 목적: 세차 모드를 맞춘 뒤 앞에 하는 말. 바꾼 것은 나열하지 않는다.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-10, 2장 다듬을 점 3(S5는 5개 나열)
     */
    const val MODE_SET = "세차 모드 맞췄어요."

    /**
     * 작성: 2026-11-11 14:28:46 · 박희정
     * 수정: 2026-11-12 18:18:00 · 박건준
     * 함수: report
     * 목적: 세차 모드를 마친 뒤 한 번에 할 말을 만든다.
     *       1. 앱이 하나라도 했으면 "세차 모드 맞췄어요."
     *       2. 사람이 해야 하는 대상마다 "사이드미러는 직접 접어 주세요."를 붙인다
     *       3. 덧붙일 안내(N 유지)가 있으면 마지막에 붙인다
     * 인자: done — 앱이 한 대상. 비었는지만 본다
     *       manual — 사람이 해야 하는 대상, 실행한 순서대로
     *       extra — 끝에 덧붙일 안내 문장들
     * 반환: 한 번에 말할 문장. 할 말이 없으면 빈 글자
     * 근거: 인용 — docs/SCREEN.md 3장 ①-10 "세차 모드 맞췄어요. 사이드미러는
     *       직접 접어 주세요."
     */
    fun report(done: List<WashPart>, manual: List<WashPart>, extra: List<String> = emptyList()): String {
        val sentences = mutableListOf<String>() // 차례로 이을 문장
        if (done.isNotEmpty()) sentences += MODE_SET
        manual.forEach { sentences += "${withTopic(it.target)} 직접 ${it.please} 주세요." }
        sentences += extra
        return sentences.joinToString(" ")
    }
}
