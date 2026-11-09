package com.westone.sunstone.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 클래스: AaosSpeedSource
 * 목적: 차 속도를 AAOS Car API(`PERF_VEHICLE_SPEED`)로 읽어 km/h로 넘긴다.
 *       Pleos Vehicle SDK에 속도 API가 없어서 따로 둔다. 권한이 없거나 차가
 *       아니면 아무것도 넘기지 않는다.
 * 인자: context — 앱 Context. Car 서비스 연결에 쓴다
 *       onSpeedKmh — 속도가 바뀔 때마다 부를 함수(km/h)
 * 근거: 표준 — `android.car.permission.CAR_SPEED`는 런타임 권한이라
 *       `MainActivity`가 요청한 뒤 `start()`를 부른다
 */
class AaosSpeedSource(context: Context, private val onSpeedKmh: (Float) -> Unit) : AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-09 18:49:00 · 박건준
         * 변수: MPS_TO_KMH
         * 목적: m/s를 km/h로 바꾸는 배수.
         * 근거: 표준 — `PERF_VEHICLE_SPEED` 단위는 m/s
         */
        const val MPS_TO_KMH = 3.6f
    }

    /** AAOS Car 서비스 연결. 차가 아니면 null */
    private val car: Car? = runCatching { Car.createCar(context.applicationContext) }.getOrNull()

    /** 차량 속성 창구. 연결이 없거나 서비스가 없으면 null */
    private val manager: CarPropertyManager? =
        runCatching { car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull()

    /** 속도 값을 받아 km/h로 바꿔 넘긴다. 오류 알림은 무시한다 */
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { onSpeedKmh(it * MPS_TO_KMH) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: start
     * 목적: 속도 구독을 시작한다. 권한을 받은 뒤 부른다. 실패하면 logcat에만
     *       남기고 속도 없이 진행한다(데모 `speed`로 넣을 수 있다).
     */
    fun start() {
        runCatching {
            manager?.registerCallback(
                callback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL,
            )
        }.onFailure { Log.w("SunstoneCar", "속도 구독 실패: ${it.message}") }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: close
     * 목적: 속도 구독을 끊고 Car 서비스 연결을 놓는다.
     */
    override fun close() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
    }
}
