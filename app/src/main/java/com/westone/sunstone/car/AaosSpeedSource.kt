package com.westone.sunstone.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 속도는 Pleos Vehicle SDK에 없어서 AAOS Car API(PERF_VEHICLE_SPEED, m/s)로 읽는다.
 * 권한 android.car.permission.CAR_SPEED는 런타임 권한이라 MainActivity에서 요청한다.
 * 권한이 없거나 차가 아니면 조용히 아무것도 하지 않는다.
 */
class AaosSpeedSource(context: Context, private val onSpeedKmh: (Float) -> Unit) : AutoCloseable {

    private val car: Car? = runCatching { Car.createCar(context.applicationContext) }.getOrNull()
    private val manager: CarPropertyManager? =
        runCatching { car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull()

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { onSpeedKmh(it * 3.6f) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    fun start() {
        runCatching {
            manager?.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        }.onFailure { Log.w("SunstoneSpeed", "속도 구독 실패: ${it.message}") }
    }

    override fun close() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
    }
}
