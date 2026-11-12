package com.westone.sunstone.routines.dialog

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-12 18:16:00 · 박건준
 * 클래스: WashBooking
 * 목적: 길안내 대화가 주유소에 세차를 예약했는지와, 그 주유소에서 어디까지
 *       왔는지 기억한다. 세차 모드는 예약한 주유소에서만, 그것도 세차 입구에
 *       섰을 때만 권한다(세차장은 예약 없이 늘 권한다).
 *       1. 예약 — 세차 추천에 "그래 그러자"
 *       2. 주유 정차 — 주유소에 처음 P·N으로 섬. 묻지 않는다(주유·결제 중)
 *       3. 다시 움직임 — 주유소 안에서 속도가 0보다 커짐
 *       4. 세차 입구 정차 — 그 뒤 다시 P·N으로 섬. 이때 세차 모드를 묻는다
 * 근거: 인용 — Tab 1 5.1 대화 흐름: 장소에 루틴 예약 → 도착 시 권하기.
 *       docs/SCREEN.md 2장 다듬을 점 2: 첫 정차는 주유, 다시 움직였다 서면 세차
 *       입구
 * 미구현: 프로토타입이라 주유소 안 두 번째 정차를 세차 입구로 본다. 세차 입구
 *       위치를 알 방법이 없어서다. 시연에서는 두 번째 정차를 장면 준비(S8)로
 *       넣는다. 실제 차에서는 세차장 입구 좌표나 세차장 결제 연동이 필요하다
 */
class WashBooking {

    /** 주유소에 세차를 예약했으면 true. 길안내 대화가 켜고, 세차 모드가 실행되면 끈다 */
    @Volatile
    var atGasStation: Boolean = false
        private set

    /** 예약한 주유소에 처음 섰으면(주유 정차) true. `WashRoutine`이 켠다 */
    @Volatile
    var fuelStopDone: Boolean = false
        private set

    /** 주유 정차 뒤 주유소 안에서 다시 움직였으면 true. `WashRoutine`이 켠다 */
    @Volatile
    var movedAfterFuel: Boolean = false
        private set

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:16:00 · 박건준
     * 함수: reserve
     * 목적: 경유지 주유소에 세차를 예약한다. 정차 기록은 처음부터 센다.
     */
    fun reserve() {
        atGasStation = true
        fuelStopDone = false
        movedAfterFuel = false
    }

    /**
     * 작성: 2026-11-12 18:16:00 · 박건준
     * 함수: markFuelStop
     * 목적: 예약한 주유소에 처음 섰다고 남긴다.
     */
    fun markFuelStop() {
        fuelStopDone = true
    }

    /**
     * 작성: 2026-11-12 18:16:00 · 박건준
     * 함수: markMoved
     * 목적: 주유 정차 뒤 다시 움직였다고 남긴다. 주유 정차 전이면 하지 않는다.
     */
    fun markMoved() {
        if (fuelStopDone) movedAfterFuel = true
    }

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 18:16:00 · 박건준
     * 함수: clear
     * 목적: 예약과 정차 기록을 지운다. 새 길안내를 시작하거나 세차를 마쳤을 때
     *       부른다.
     */
    fun clear() {
        atGasStation = false
        fuelStopDone = false
        movedAfterFuel = false
    }
}
