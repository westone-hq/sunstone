package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: ChildLockLines
 * 목적: 차일드락이 하는 말과 카드 글자를 한곳에 모은다. 문장을 바꿀 때는 이
 *       파일만 고친다. 말은 1~2문장으로 하고, 무엇을 맞췄는지는 카드 칸이
 *       보여 준다.
 * 근거: 인용 — docs/SCREEN.md 4장 ② 표, 6장 "②③에 같은 짜임을 쓸 때",
 *       1장 말과 화면 나누기
 */
object ChildLockLines {

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 변수: BOARDED
     * 목적: 정차 중 뒷자리에 누가 탔을 때 권하는 말. 탄 사람이 아이인지는 알 수
     *       없어 "누가"로 말한다.
     * 근거: 인용 — docs/SCREEN.md 4장 ②-1
     */
    const val BOARDED = "뒷자리에 누가 탔네요. 뒷창문 잠가 드릴까요?"

    /**
     * 작성: 2026-11-13 14:00:00 · 박희정
     * 변수: KIDS_ASK, KIDS_TITLE, KIDS_DETAIL
     * 목적: 유치원으로 길안내를 시작했을 때 아이가 탔는지 물으며 권하는 말(길안내
     *       말 뒤에 이어 한다)과 카드 제목·설명.
     * 근거: 인용 — 2026-11-13 박건준 요청 "유치원 간다고 하면 아이가 타고
     *       있는지 물어보면서 차일드락". 문장은 2026-11-13 박희정이 정했다
     */
    const val KIDS_ASK = "아이 타고 있나요? 차일드락 맞춰 드릴까요?"
    const val KIDS_TITLE = "차일드락 켤까요?"
    const val KIDS_DETAIL = "아이가 타고 있으면 아래처럼 맞춰 드려요."

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 변수: AGAIN
     * 목적: 오늘 차일드락을 썼고 뒷자리에 누가 탄 채 집으로 길안내를 시작하면
     *       권하는 말(길안내 말 뒤에 이어 한다).
     * 근거: 인용 — docs/SCREEN.md 4장 ②-8
     */
    const val AGAIN = "아침처럼 뒷창문 잠가 드릴까요?"

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 변수: ASK_TITLE, ASK_DETAIL, AGAIN_TITLE, AGAIN_DETAIL, ASK_YES, ASK_NO
     * 목적: 권하기 카드의 제목·설명(처음, 다시)과 두 버튼.
     * 근거: 인용 — docs/SCREEN.md 6장 ② "뒷창문 잠글까요?", 4장 ②-1 설명·버튼,
     *       ②-8 "차일드락 다시 켤까요?" "아침에 켰던 대로 맞춰 드려요."
     */
    const val ASK_TITLE = "뒷창문 잠글까요?"
    const val ASK_DETAIL = "잠그면 아래처럼 맞춰 드려요."
    const val AGAIN_TITLE = "차일드락 다시 켤까요?"
    const val AGAIN_DETAIL = "아침에 켰던 대로 맞춰 드려요."
    const val ASK_YES = "잠가 줘"
    const val ASK_NO = "괜찮아요"

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 변수: STEP_REAR_WINDOW, STEP_DOOR_LOCK, STEP_CLIMATE, STEP_BLIND,
     *       STEP_CHILD_LOCK, MANUAL
     * 목적: 차일드락 단계 이름(카드 할 일 칸 글자)과 사람이 할 칸 아래 글자.
     *       뒷창문 두 개는 같은 이름이라 한 칸으로 보인다.
     * 근거: 인용 — docs/SCREEN.md 6장 ② 칸 둘(뒷창문, 차일드락 "직접"). 문 잠금·
     *       공조·블라인드 칸은 2026-11-13 박희정이 기능을 더 넣기로 정해
     *       더했다(미정)
     */
    const val STEP_REAR_WINDOW = "뒷창문"
    const val STEP_DOOR_LOCK = "문 잠금"
    const val STEP_CLIMATE = "공조"
    const val STEP_BLIND = "블라인드"
    const val STEP_CHILD_LOCK = "차일드락"
    const val MANUAL = "직접"

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 변수: LOCKED, REAR_WINDOW_MANUAL
     * 목적: 다 맞춘 뒤 하는 말. 뒷창문을 잠갔으면 `LOCKED`, 못 잠갔으면 사람에게
     *       부탁한다.
     * 근거: 인용 — docs/SCREEN.md 4장 ②-2, ②-9
     */
    const val LOCKED = "뒷창문 잠갔어요."
    const val REAR_WINDOW_MANUAL = "뒷창문은 직접 잠가 주세요."

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 변수: DOOR_MANUAL, CLIMATE_MANUAL, BLIND_MANUAL
     * 목적: 문 잠금·공조·블라인드를 앱이 못 했을 때 그 단계의 안내. 끝에 한 번에
     *       말하는 루틴이라 말하지 않고 단계 기록에만 남는다.
     * 근거: 미정 — 대사집에 없는 단계라 세차 모드 안내 문장 꼴에 맞췄다
     */
    const val DOOR_MANUAL = "문을 잠가 주세요."
    const val CLIMATE_MANUAL = "공조를 켜 주세요."
    const val BLIND_MANUAL = "뒷유리 블라인드를 올려 주세요."

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: DOOR_CHILD_LOCK
     * 목적: 뒷문 차일드락을 걸어 달라는 안내. 그날 처음 맞출 때만 말한다.
     * 근거: 인용 — docs/SCREEN.md 4장 ②-2(②-9는 말하지 않는다). 측정 — SDK
     *       `Door`에 차일드락이 없고 AAOS 속성은 권한이 없다(docs/api-spike.md
     *       차일드락 행)
     */
    const val DOOR_CHILD_LOCK = "뒷문 차일드락은 문 안쪽 스위치로 걸어 주세요."

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 변수: BELT_OFF
     * 목적: 달리는 중 뒷자리 벨트가 풀렸을 때 하는 말. 토스트에도 같은 문장을
     *       보인다.
     * 근거: 인용 — docs/SCREEN.md 4장 ②-5
     */
    const val BELT_OFF = "뒷자리 안전벨트가 풀렸어요."

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 변수: ARRIVED, UNLOCKED_NOTICE
     * 목적: 목적지에 도착해 P로 서서 원래대로 돌린 뒤 하는 말과 토스트.
     * 근거: 인용 — docs/SCREEN.md 4장 ②-6, ②-10
     */
    const val ARRIVED = "도착했어요. 뒷창문 잠금 풀었어요."
    const val UNLOCKED_NOTICE = "뒷창문 잠금을 풀었어요"

    /**
     * 작성: 2026-11-13 12:00:00 · 박희정
     * 변수: FINISHED
     * 목적: 사람이 끝냈을 때([끝내기], "원래대로") 원래대로 돌린 뒤 하는 말.
     *       도착하지 않았으니 "도착했어요"를 말하지 않는다.
     * 근거: 미정 — 대사집에 없는 경우라 세차 모드 끝낼 때 말("세차 모드
     *       끝낼게요. 원래대로 돌려놨어요.")에 맞췄다
     */
    const val FINISHED = "차일드락 끝낼게요. 뒷창문 잠금 풀었어요."

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 변수: BLOCKED_MOVING
     * 목적: 달리는 중에 차일드락을 부탁받았을 때 하는 말.
     * 근거: 미정 — 2026-11-08 박건준 초안 그대로
     */
    const val BLOCKED_MOVING = "차가 멈춘 뒤에 할게요."
}
