package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: TripLines
 * 목적: 길안내 대화(시나리오 ① #1~#2)가 하는 말을 한곳에 모은다. 문장을 바꿀
 *       때는 이 파일만 고친다.
 * 근거: 인용 — Tab 1 4.3 시나리오 ① #1~#2 대사
 */
object TripLines {

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: ROUTE_STARTED
     * 목적: 길안내를 시작했을 때 하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #1 앞 문장
     */
    const val ROUTE_STARTED = "네, 길안내 시작할게요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: FUEL_BEFORE_HIGHWAY
     * 목적: 연료가 낮고 경로에 고속도로(통행료)가 있을 때 주유를 권하는 말.
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #1 뒤 문장
     */
    const val FUEL_BEFORE_HIGHWAY =
        "연료가 부족해서 고속도로 들어가기 전에 주유하시는 게 좋겠어요. 근처 주유소 찾아 드릴까요?"

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: FUEL_ON_THE_WAY
     * 목적: 연료가 낮지만 경로에 고속도로가 없을 때 주유를 권하는 말.
     * 근거: 미정 — 대사집에 없는 경우라 #1 문장에서 고속도로만 뺐다
     */
    const val FUEL_ON_THE_WAY = "연료가 부족해서 가시는 길에 주유하시는 게 좋겠어요. 근처 주유소 찾아 드릴까요?"

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: ROUTE_FAILED
     * 목적: 내비가 경로를 시작하지 못했을 때 하는 말.
     * 근거: 미정 — 대사집에 없는 경우
     */
    const val ROUTE_FAILED = "길안내를 시작하지 못했어요. 다시 말해 주세요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 변수: NO_STATION
     * 목적: 근처 주유소를 찾지 못했을 때 하는 말.
     * 근거: 미정 — 대사집에 없는 경우
     */
    const val NO_STATION = "근처에 주유소를 찾지 못했어요."

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
     * 목적: 주유 제안을 거절했을 때 하는 말.
     * 근거: 미정 — 대사집에 없어 권하기 거절과 같게 했다
     */
    const val DECLINED = "알겠어요."

    /**
     * 작성: 2026-11-12 11:58:00 · 박희정
     * 변수: WASH_BOOKED
     * 목적: 세차 추천에 "그래 그러자"로 답했을 때 하는 말. 세차 예약을 그대로
     *       두고 주유소에 도착하면 세차 모드를 권한다는 뜻이다.
     * 근거: 미정 — 대사집에 없는 단계. 2026-11-12 박희정이 넣기로 정했다
     */
    const val WASH_BOOKED = "네, 주유소 도착하면 세차 모드 맞춰 드릴게요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: notFound
     * 목적: 목적지를 찾지 못했을 때 하는 말.
     * 인자: query — 운전자가 말한 목적지
     * 반환: 문장
     * 근거: 미정 — 대사집에 없는 경우
     */
    fun notFound(query: String) = "${withObject(query)} 찾지 못했어요. 다시 말해 주세요."

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: waypointAdded
     * 목적: 주유소를 경유지로 넣은 뒤 하는 말. 모르는 사실은 문장에서 뺀다.
     *       1. 세차 되는 곳이면 "가격이 괜찮고 세차도 되는", 아니면 "가격이 괜찮은"
     *       2. 마지막 세차일을 알면 "세차 안 하신 지 N일 되셨습니다."
     *       3. 세차를 추천하면 "일주일간 비 소식이 없어 세차 추천드려요."
     * 인자: station — 넣은 주유소 이름
     *       carWash — 세차할 수 있는 주유소인지
     *       daysSinceWash — 마지막 세차 뒤 지난 날 수. 모르면 null
     *       recommendWash — 세차를 추천하는지
     * 반환: 문장
     * 근거: 인용 — Tab 1 4.3 시나리오 ① #2
     */
    fun waypointAdded(station: String, carWash: Boolean, daysSinceWash: Long?, recommendWash: Boolean): String {
        val kind = if (carWash) "가격이 괜찮고 세차도 되는" else "가격이 괜찮은" // 주유소를 고른 이유
        val sentences = mutableListOf("$kind ${withObject(station)} 경유지로 넣었어요.") // 차례로 이을 문장
        if (carWash && daysSinceWash != null) sentences += "세차 안 하신 지 ${daysSinceWash}일 되셨습니다."
        if (recommendWash) sentences += "일주일간 비 소식이 없어 세차 추천드려요."
        return sentences.joinToString(" ")
    }
}
