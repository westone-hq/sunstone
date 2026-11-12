package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-12 18:24:00 · 박건준
 * 클래스: TripLines
 * 목적: 길안내 대화(시나리오 ① ①-2~①-6)가 하는 말과 패널 글자를 한곳에
 *       모은다. 문장을 바꿀 때는 이 파일만 고친다. 말은 1~2문장, 한 번에 질문
 *       하나다.
 * 근거: 인용 — docs/SCREEN.md 3장 ①-2~①-6 표 문장, 2장 다듬을 점 3
 */
object TripLines {

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 변수: FUEL_TITLE, FUEL_DETAIL, FUEL_YES
     * 목적: 주유 제안 패널의 제목, 설명줄, 채운 버튼.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-3
     */
    const val FUEL_TITLE = "연료가 부족해요"
    const val FUEL_DETAIL = "가는 길에 들를 주유소를 찾았어요."
    const val FUEL_YES = "경유지로 넣기"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 변수: CAR_WASH_OK
     * 목적: 주유소 줄 오른쪽에 붙이는 글자. 세차할 수 있는 곳일 때만.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-3
     */
    const val CAR_WASH_OK = "세차 가능"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 변수: NO_THANKS
     * 목적: 질문 패널의 흰 버튼. 주유 제안과 세차 추천이 같이 쓴다.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-3·①-5
     */
    const val NO_THANKS = "괜찮아요"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 변수: WAYPOINT_NOTICE
     * 목적: 주유소를 경유지로 넣은 뒤 보일 토스트.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-4
     */
    const val WAYPOINT_NOTICE = "경유지에 넣었어요"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 변수: WASH_TITLE, WASH_YES
     * 목적: 세차 추천 패널의 제목과 채운 버튼.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-5
     */
    const val WASH_TITLE = "세차하기 좋은 날이에요"
    const val WASH_YES = "세차도 할게요"

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: ROUTE_FAILED
     * 목적: 내비가 경로를 시작하지 못했을 때 하는 말.
     * 근거: 미정 — 대사집에 없는 경우
     */
    const val ROUTE_FAILED = "길안내를 시작하지 못했어요. 다시 말해 주세요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: WAYPOINT_FAILED
     * 목적: 주유소를 경유지로 넣지 못했을 때 하는 말.
     * 근거: 미정 — 대사집에 없는 경우
     */
    const val WAYPOINT_FAILED = "주유소를 경유지로 넣지 못했어요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: DECLINED
     * 목적: 주유 제안·세차 추천을 거절했을 때 하는 말.
     * 근거: 미정 — 대사집에 없어 권하기 거절과 같게 했다
     */
    const val DECLINED = "알겠어요."

    /**
     * 작성: 2026-11-12 11:58:00 · 박희정
     * 수정: 2026-11-12 18:24:00 · 박건준
     * 변수: WASH_BOOKED
     * 목적: 세차 추천에 "그래 그러자"로 답했을 때 하는 말. 세차 예약을 두고
     *       세차 입구에 서면 세차 모드를 묻는다는 뜻이다.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-6
     */
    const val WASH_BOOKED = "네, 세차 입구에 서시면 세차 모드 맞춰 드릴게요."

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 변수: WASH_BOOKED_NOTICE
     * 목적: 세차를 예약한 뒤 보일 토스트.
     * 근거: 인용 — docs/SCREEN.md 3장 ①-6
     */
    const val WASH_BOOKED_NOTICE = "세차 예약했어요"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 함수: routeStarted
     * 목적: 길안내를 시작했을 때 하는 말. 이것만 말하고, 주유 제안은 따로
     *       말한다.
     * 인자: destination — 목적지 이름
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 3장 ①-2
     */
    fun routeStarted(destination: String) = "${withDirection(destination)} 안내할게요."

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 함수: fuelOffer
     * 목적: 연료가 낮을 때 주유를 묻는 말.
     * 인자: percent — 연료(%)
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 3장 ①-3
     */
    fun fuelOffer(percent: Int) = "연료가 ${percent}%예요. 출발 전에 주유하고 가실래요?"

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:24:00 · 박건준
     * 함수: waypointAdded
     * 목적: 주유소를 경유지로 넣은 뒤 하는 말. 고른 이유는 패널이 보여 줘서
     *       말하지 않는다.
     * 인자: station — 넣은 주유소 이름
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 3장 ①-4
     */
    fun waypointAdded(station: String) = "${withObject(station)} 경유지로 넣었어요."

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 함수: washRecommend
     * 목적: 세차를 추천하며 묻는 말.
     * 인자: daysSinceWash — 마지막 세차 뒤 지난 날 수
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 3장 ①-5
     */
    fun washRecommend(daysSinceWash: Long) =
        "세차 안 하신 지 ${daysSinceWash}일 됐어요. 일주일간 비 소식이 없는데, 거기서 세차도 하실래요?"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 함수: lastWashRow
     * 목적: 세차 추천 패널의 마지막 세차일 줄.
     * 인자: daysSinceWash — 마지막 세차 뒤 지난 날 수
     * 반환: 줄 글자
     * 근거: 인용 — docs/SCREEN.md 3장 ①-5
     */
    fun lastWashRow(daysSinceWash: Long) = "마지막 세차 ${daysSinceWash}일 전"

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 함수: noRainRow
     * 목적: 세차 추천 패널의 날씨 줄.
     * 인자: days — 비 소식을 본 기간(일)
     * 반환: 줄 글자
     * 근거: 인용 — docs/SCREEN.md 3장 ①-5
     */
    fun noRainRow(days: Int) = "${days}일 동안 비 없음"

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: notFound
     * 목적: 목적지를 찾지 못했을 때 하는 말.
     * 인자: query — 운전자가 말한 목적지
     * 반환: 문장
     * 근거: 미정 — 대사집에 없는 경우
     */
    fun notFound(query: String) = "${withObject(query)} 찾지 못했어요. 다시 말해 주세요."
}
