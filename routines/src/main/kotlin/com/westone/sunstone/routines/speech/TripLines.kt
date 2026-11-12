package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: TripLines
 * 목적: 길안내 대화(시나리오 ① 1~3단계)가 하는 말과 카드 글자를 한곳에
 *       모은다. 문장을 바꿀 때는 이 파일만 고친다. 질문은 한 번(주유, 조건이
 *       맞으면 세차도 같이)이다.
 * 근거: 인용 — docs/SCREEN.md 6장 시나리오 ① 다시 짠 흐름 1~3
 */
object TripLines {

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 변수: FUEL_WASH_TITLE, FUEL_WASH_ALL, FUEL_WASH_ONLY
     * 목적: 주유와 세차를 같이 묻는 카드의 제목과 두 버튼(주유하고 세차, 주유만).
     * 근거: 인용 — docs/SCREEN.md 6장 2단계, 시안 template2.html `card()` fuel
     */
    const val FUEL_WASH_TITLE = "주유하고 세차할까요?"
    const val FUEL_WASH_ALL = "주유하고 세차"
    const val FUEL_WASH_ONLY = "주유만"

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 변수: FUEL_TITLE, FUEL_YES, NO_THANKS
     * 목적: 세차 조건이 안 맞을 때 주유만 묻는 카드의 제목과 두 버튼.
     * 근거: 인용 — 2026-11-13 박건준 2차 업그레이드 지시: 세차 조건이 안 맞으면
     *       "주유할까요?" + [경유지로 넣기] [괜찮아요]
     */
    const val FUEL_TITLE = "주유할까요?"
    const val FUEL_YES = "경유지로 넣기"
    const val NO_THANKS = "괜찮아요"

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 변수: CAR_WASH_OK
     * 목적: 주유 카드 설명 뒤에 붙는, 그 주유소에서 세차도 된다는 문장.
     * 근거: 인용 — 시안 template2.html `card()` fuel 설명
     */
    const val CAR_WASH_OK = "세차도 되는 곳이에요."

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 수정: 2026-11-13 03:25:00 · 박건준
     * 변수: WAYPOINT_NOTICE, WAYPOINT_WASH_NOTICE
     * 목적: 주유소를 경유지로 넣은 뒤 판에 보일 토스트. 세차도 예약했으면
     *       뒤의 것.
     * 근거: 인용 — docs/SCREEN.md 6장 3단계
     */
    const val WAYPOINT_NOTICE = "경유지에 넣었어요"
    const val WAYPOINT_WASH_NOTICE = "경유지에 넣고 세차를 예약했어요"

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: ROUTE_FAILED
     * 목적: 내비가 경로를 시작하지 못했을 때 하는 말.
     * 근거: 미정 — Tab 1 대사집에 실패 문장이 없어 짧게 정했다
     */
    const val ROUTE_FAILED = "길안내를 시작하지 못했어요. 다시 말해 주세요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: WAYPOINT_FAILED
     * 목적: 내비가 경유지를 넣지 못했을 때 하는 말.
     * 근거: 미정 — Tab 1 대사집에 실패 문장이 없어 짧게 정했다
     */
    const val WAYPOINT_FAILED = "주유소를 경유지로 넣지 못했어요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: DECLINED
     * 목적: 제안을 거절했을 때 하는 말.
     * 근거: 미정 — Tab 1 대사집에 거절 문장이 없어 짧게 정했다
     */
    const val DECLINED = "알겠어요."

    /**
     * 작성: 2026-11-12 18:24:00 · 박건준
     * 함수: routeStarted
     * 목적: 길안내를 시작했다는 말.
     * 인자: destination — 목적지 이름
     * 반환: "파라다이스 호텔 부산으로 안내할게요."
     * 근거: 인용 — docs/SCREEN.md 6장 1단계
     */
    fun routeStarted(destination: String) = "${withDirection(destination)} 안내할게요."

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: fuelDetail
     * 목적: 주유 카드 설명. 세차되는 곳이면 그 문장을 붙인다.
     * 인자: station — 주유소 이름(카드에서 굵게 보인다)
     *       carWash — 세차도 되는 곳이면 true
     * 반환: "가는 길에 말죽거리주유소가 있어요. 세차도 되는 곳이에요."
     * 근거: 인용 — 시안 template2.html `card()` fuel 설명
     */
    fun fuelDetail(station: String, carWash: Boolean) =
        "가는 길에 ${withSubject(station)} 있어요." + if (carWash) " $CAR_WASH_OK" else ""

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: fuelFact
     * 목적: 연료 근거 칸 글자.
     * 인자: percent — 연료(%)
     * 반환: "연료 18%"
     * 근거: 인용 — docs/SCREEN.md 6장 2단계 근거 칸
     */
    fun fuelFact(percent: Int) = "연료 $percent%"

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: washFact
     * 목적: 마지막 세차 근거 칸 글자.
     * 인자: daysSinceWash — 마지막 세차 뒤 날 수
     * 반환: "세차 36일 전"
     * 근거: 인용 — docs/SCREEN.md 6장 2단계 근거 칸
     */
    fun washFact(daysSinceWash: Long) = "세차 ${daysSinceWash}일 전"

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: noRainFact
     * 목적: 비 소식 근거 칸 글자.
     * 인자: days — 비 소식을 본 기간(일)
     * 반환: "7일 동안 비 없음"
     * 근거: 인용 — docs/SCREEN.md 6장 2단계 근거 칸
     */
    fun noRainFact(days: Int) = "${days}일 동안 비 없음"

    /**
     * 작성: 2026-11-13 03:25:00 · 박건준
     * 함수: fuelOffer
     * 목적: 주유(조건이 맞으면 세차도) 권하는 말. 연료, 주유소, 세차 이유를 한
     *       번에 말한다.
     * 인자: percent — 연료(%)
     *       station — 주유소 이름
     *       daysSinceWash — 마지막 세차 뒤 날 수. 세차를 권하지 않으면 null
     * 반환: "연료가 18%예요. 가는 길에 말죽거리주유소가 있어요. 세차 안 하신 지
     *       36일 됐고 일주일간 비 소식이 없어서 세차도 추천드려요."
     * 근거: 인용 — docs/SCREEN.md 6장 2단계 말(사용자가 정한 세차 추천 문장이
     *       이 질문 안에 들어간다)
     */
    fun fuelOffer(percent: Int, station: String, daysSinceWash: Long?): String {
        val fuel = "연료가 $percent%예요. 가는 길에 ${withSubject(station)} 있어요." // 주유 권하는 앞 두 문장
        if (daysSinceWash == null) return fuel
        return "$fuel 세차 안 하신 지 ${daysSinceWash}일 됐고 일주일간 비 소식이 없어서 세차도 추천드려요."
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:24:00 · 박건준
     * 함수: notFound
     * 목적: 목적지를 찾지 못했을 때 하는 말.
     * 인자: query — 운전자가 말한 목적지
     * 반환: "제주 공항을 찾지 못했어요. 다시 말해 주세요."
     */
    fun notFound(query: String) = "${withObject(query)} 찾지 못했어요. 다시 말해 주세요."
}
