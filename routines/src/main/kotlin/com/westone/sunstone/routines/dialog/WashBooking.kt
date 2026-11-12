package com.westone.sunstone.routines.dialog

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: WashBooking
 * 목적: 길안내 대화가 주유소에 세차를 예약했는지 기억한다. 세차 모드는
 *       예약한 주유소에 도착했을 때만 주유소에서 세차를 권한다(세차장은
 *       예약 없이 늘 권한다). 주유만 하러 들른 주유소에서 매번 묻지 않으려고
 *       둔다.
 * 근거: 인용 — Tab 1 5.1 대화 흐름: 장소에 루틴 예약 → 도착 시 권하기
 */
class WashBooking {

    /** 주유소에 세차를 예약했으면 true. 길안내 대화가 켜고, 세차 모드가 실행되면 끈다 */
    @Volatile
    var atGasStation: Boolean = false
        private set

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: reserve
     * 목적: 경유지 주유소에 세차를 예약한다.
     */
    fun reserve() {
        atGasStation = true
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: clear
     * 목적: 예약을 지운다. 새 길안내를 시작하거나 세차를 마쳤을 때 부른다.
     */
    fun clear() {
        atGasStation = false
    }
}
