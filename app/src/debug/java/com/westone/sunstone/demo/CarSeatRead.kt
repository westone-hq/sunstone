package com.westone.sunstone.demo

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 작성: 2026-11-09 18:51:00 · 박건준
 * 클래스: CarSeatRead
 * 목적: 시트 위치를 Pleos SDK 대신 AAOS `CarPropertyManager`(차량 속성을
 *       직접 읽는 안드로이드 창구)로 읽어 본다. SDK 시트 위치 읽기가 늘 0을
 *       돌려줘서, 다른 경로로는 읽히는지 확인하는 실측용이라 debug 빌드에만
 *       있다. 축마다 `seatread,속성,설정,값`을 logcat에 남긴다.
 * 인자: context — 앱 Context. Car 서비스 연결에 쓴다
 * 근거: 측정 — docs/api-spike.md 시트 위치 읽기 행
 */
internal class CarSeatRead(private val context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-09 18:51:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 18:51:00 · 박건준
         * 변수: DRIVER
         * 목적: 운전석 area 번호.
         * 근거: 표준 — AAOS `VehicleAreaSeat.SEAT_ROW_1_LEFT`
         */
        const val DRIVER = VehicleAreaSeat.SEAT_ROW_1_LEFT

        /**
         * 작성: 2026-11-09 18:51:00 · 박건준
         * 변수: SEAT_CUSHION_SIDE_SUPPORT_POS
         * 목적: 쿠션 옆 받침 위치 속성 번호. 공개 SDK(API 35 android.car)에
         *       이름이 없어 번호로 둔다.
         * 근거: 표준 — AOSP VehicleProperty 0x0BA9 | SYSTEM | SEAT | INT32
         */
        const val SEAT_CUSHION_SIDE_SUPPORT_POS = 0x15400BA9

        /**
         * 작성: 2026-11-09 18:51:00 · 박건준
         * 변수: PROPS
         * 목적: 읽어 볼 시트 위치 속성(이름, 번호). 콘솔에 있는 축(앞뒤·높이·
         *       등받이·쿠션 각도·허리)과 SDK가 다루는 축을 함께 둔다.
         * 근거: 표준 — AAOS `VehiclePropertyIds` (API 34)
         */
        val PROPS = listOf(
            "SEAT_FORE_AFT_POS" to VehiclePropertyIds.SEAT_FORE_AFT_POS,
            "SEAT_HEIGHT_POS" to VehiclePropertyIds.SEAT_HEIGHT_POS,
            "SEAT_BACKREST_ANGLE_1_POS" to VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
            "SEAT_BACKREST_ANGLE_2_POS" to VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
            "SEAT_TILT_POS" to VehiclePropertyIds.SEAT_TILT_POS,
            "SEAT_DEPTH_POS" to VehiclePropertyIds.SEAT_DEPTH_POS,
            "SEAT_LUMBAR_FORE_AFT_POS" to VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS,
            "SEAT_LUMBAR_SIDE_SUPPORT_POS" to VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
            "SEAT_CUSHION_SIDE_SUPPORT_POS" to SEAT_CUSHION_SIDE_SUPPORT_POS,
            "SEAT_HEADREST_HEIGHT_POS_V2" to VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS_V2,
        )
    }

    /**
     * 작성: 2026-11-09 18:51:00 · 박건준
     * 함수: run
     * 목적: 운전석 축마다 속성 설정(지원 area·범위)과 지금 값을 읽어 남긴다.
     *       권한이 없으면 예외 이름과 문장을 값 칸에 적는다.
     * 반환: 확인한 속성 수. Car 서비스에 연결하지 못하면 0
     */
    fun run(): Int {
        val car = runCatching { Car.createCar(context.applicationContext) }.getOrNull() // Car 서비스 연결
        val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager // 차량 속성 창구
        if (manager == null) {
            Log.w(TAG, "seatread: CarPropertyManager 없음")
            return 0
        }
        try {
            PROPS.forEach { (name, id) ->
                val config = runCatching { manager.getCarPropertyConfig(id)?.let { "areas=${it.areaIds.toList()}" } }
                    .getOrElse { "${it.javaClass.simpleName}: ${it.message}" } ?: "설정 없음" // 속성 설정
                val value = runCatching { manager.getProperty<Any>(id, DRIVER).value.toString() }
                    .getOrElse { "${it.javaClass.simpleName}: ${it.message}" } // 운전석 값 또는 오류
                Log.i(TAG, "seatread,$name,$config,$value")
            }
        } finally {
            car.disconnect()
        }
        Log.i(TAG, "seatread 끝: ${PROPS.size}개")
        return PROPS.size
    }
}
