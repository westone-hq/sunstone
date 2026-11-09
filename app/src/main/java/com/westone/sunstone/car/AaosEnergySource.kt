package com.westone.sunstone.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 작성: 2026-11-09 20:18:22 · 박건준
 * 수정: 2026-11-09 20:58:00 · 박건준
 * 클래스: AaosEnergySource
 * 목적: 주행 가능 거리·남은 연료·남은 배터리·탱크와 배터리 용량을 AAOS Car
 *       API로 읽어 넘긴다. Pleos SDK 값과 비교하려고 따로 읽는다. 권한이
 *       없거나 차가 그 속성을 갖고 있지 않으면 그 값만 넘기지 않고 logcat에
 *       이유를 남긴다.
 * 인자: context — 앱 Context. Car 서비스 연결에 쓴다
 *       onValue — 값이 오거나 바뀔 때마다 부를 함수(속성 번호, 값)
 *       onFuelTypes — 연료 종류(`INFO_FUEL_TYPE`)를 읽으면 부를 함수
 * 근거: 표준 — `RANGE_REMAINING`·`FUEL_LEVEL`·`EV_BATTERY_LEVEL`은
 *       `android.car.permission.CAR_ENERGY`(런타임 권한), 용량 두 개는
 *       `CAR_INFO`(설치 때 허가). `MainActivity`가 CAR_ENERGY를 요청한 뒤
 *       `start()`를 부른다
 */
class AaosEnergySource(
    context: Context,
    private val onValue: (Int, Float) -> Unit,
    private val onFuelTypes: (List<Int>) -> Unit,
) : AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-09 20:18:22 · 박건준
         * 변수: TAG
         * 목적: 차 관련 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-09 20:18:22 · 박건준
         * 변수: WATCHED, FIXED
         * 목적: 바뀔 때마다 받을 속성과 한 번만 읽을 속성(용량).
         */
        val WATCHED = listOf( // 바뀌는 값
            VehiclePropertyIds.RANGE_REMAINING,
            VehiclePropertyIds.FUEL_LEVEL,
            VehiclePropertyIds.EV_BATTERY_LEVEL,
        )
        val FIXED = listOf( // 차마다 정해진 값
            VehiclePropertyIds.INFO_FUEL_CAPACITY,
            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
        )
    }

    /** AAOS Car 서비스 연결. 차가 아니면 null */
    private val car: Car? = runCatching { Car.createCar(context.applicationContext) }.getOrNull()

    /** 차량 속성 창구. 연결이 없거나 서비스가 없으면 null */
    private val manager: CarPropertyManager? =
        runCatching { car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull()

    /** 바뀐 값을 넘긴다. 오류 알림은 logcat에만 남긴다 */
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { onValue(value.propertyId, it) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "에너지 속성 오류: ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 수정: 2026-11-09 20:58:00 · 박건준
     * 함수: start
     * 목적: 연료 종류와 용량을 한 번 읽어 넘기고, 바뀌는 값을 구독한다.
     *       속성마다 따로 시도해서 하나가 실패해도 나머지는 받는다.
     * 근거: 표준 — `INFO_FUEL_TYPE`은 `Integer[]`, `CAR_INFO` 권한
     */
    fun start() {
        val m = manager // 차량 속성 창구, 차가 아니면 null
        if (m == null) {
            Log.w(TAG, "에너지: CarPropertyManager 없음")
            return
        }
        runCatching { m.getProperty<Array<Int>>(VehiclePropertyIds.INFO_FUEL_TYPE, 0).value.toList() }
            .onSuccess(onFuelTypes)
            .onFailure { Log.w(TAG, "에너지 읽기 실패 INFO_FUEL_TYPE: ${it.message}") }
        FIXED.forEach { id ->
            runCatching { m.getFloatProperty(id, 0) }
                .onSuccess { onValue(id, it) }
                .onFailure { Log.w(TAG, "에너지 읽기 실패 ${VehiclePropertyIds.toString(id)}: ${it.message}") }
        }
        WATCHED.forEach { id ->
            runCatching { m.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                .onFailure { Log.w(TAG, "에너지 구독 실패 ${VehiclePropertyIds.toString(id)}: ${it.message}") }
        }
    }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 함수: close
     * 목적: 구독을 끊고 Car 서비스 연결을 놓는다.
     */
    override fun close() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
    }
}
