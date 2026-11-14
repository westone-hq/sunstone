package com.westone.sunstone.info

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.westone.sunstone.routines.model.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 클래스: WeatherLocation
 * 목적: 날씨 요청 시 새 위치 업데이트를 기다린다. 마지막 저장 위치는 사용하지 않는다.
 * 근거: 표준 — SDK 1.1.3 LocationData.timestampMillis. 측정 시각도 확인한다.
 */
class WeatherLocation(private val context: Context) {
    /** 함수: current
     * 목적: 권한·위치 실패 시 임의 좌표 없이 null을 반환하고 구독을 해제한다.
     */
    suspend fun current(): GeoPoint? {
        val fused = FusedLocation(context.applicationContext) // 요청 수명 동안만 연결
        var listener: FusedLocationListener? = null // finally에서 해제할 리스너
        return try {
            withTimeoutOrNull(4_000) {
                fused.initialize()
                delay(1_000)
                suspendCancellableCoroutine { continuation ->
                    listener = object : FusedLocationListener {
                        /** 함수: onChanged
                         * 목적: 유효한 새 위치 콜백 한 번만 수락한다.
                         */
                        override fun onChanged(data: LocationData?) {
                            if (!continuation.isActive) return
                            val point = data?.let { GeoPoint(it.latitude, it.longitude) } // 수신 좌표
                            val age = data?.let { System.currentTimeMillis() - it.timestampMillis }
                            if (age == null || age !in 0..120_000L) return
                            continuation.resume(point?.takeIf {
                                it.lat.isFinite() && it.lng.isFinite() && it.lat in -90.0..90.0 && it.lng in -180.0..180.0
                            })
                        }
                        /** 함수: onError
                         * 목적: 위치 실패를 현재 위치 없음으로 알린다.
                         */
                        override fun onError(e: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                    fused.registerFusedLocationCallback(listener!!)
                }
            }
        } finally {
            listener?.let { runCatching { fused.unregisterFusedLocationCallback(it) }
                .onFailure { Log.w("Sunstone", "날씨 위치 해제 실패: ${it.message}") } }
            runCatching { fused.release() }.onFailure { Log.w("Sunstone", "날씨 위치 종료 실패: ${it.message}") }
        }
    }
}
