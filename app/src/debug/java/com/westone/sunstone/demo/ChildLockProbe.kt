package com.westone.sunstone.demo

import android.car.Car
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 작성: 2026-11-11 11:52:00 · 박건준
 * 클래스: ChildLockProbe
 * 목적: 뒷문 차일드락을 AAOS `DOOR_CHILD_LOCK_ENABLED`로 읽고 써 본다.
 *       Pleos SDK `Door`에는 차일드락 함수가 없어서 표준 속성으로 시도한다.
 *       실측용이라 debug 빌드에만 있다. 결과는 `childlock,` 줄로 남긴다.
 *       1. 속성 설정(지원 area·권한)
 *       2. 뒷문 좌우 읽기 → 반대 값 쓰기 → 다시 읽기 → 원래 값 쓰기
 * 인자: context — 앱 Context. Car 서비스 연결에 쓴다
 * 근거: 표준 — AAOS 14 `DOOR_CHILD_LOCK_ENABLED`(area는 문),
 *       읽기·쓰기 모두 `android.car.permission.CONTROL_CAR_DOORS`
 */
internal class ChildLockProbe(private val context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-11 11:52:00 · 박건준
         * 변수: TAG
         * 목적: 결과 줄의 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneCar"

        /**
         * 작성: 2026-11-11 11:52:00 · 박건준
         * 변수: REAR_DOORS
         * 목적: 확인할 뒷문(이름, AAOS 문 area).
         */
        val REAR_DOORS = listOf(
            "뒷문 왼쪽" to 0x10, // DOOR_ROW_2_LEFT
            "뒷문 오른쪽" to 0x40, // DOOR_ROW_2_RIGHT
        )

        /**
         * 작성: 2026-11-11 11:52:00 · 박건준
         * 변수: CHILD_LOCK
         * 목적: `DOOR_CHILD_LOCK_ENABLED` 속성 번호. 앱이 쓰는 `android.car`
         *       스텁에 상수가 없어 숫자로 둔다.
         * 근거: 측정 — 에뮬레이터 `dumpsys car_service --hal`에
         *       0x16200b03 DOOR_CHILD_LOCK_ENABLED, access 0x3(읽기·쓰기)
         */
        const val CHILD_LOCK = 0x16200b03
    }

    /**
     * 작성: 2026-11-11 11:52:00 · 박건준
     * 함수: run
     * 목적: 클래스 설명의 1~2단계를 한다. 한 단계가 실패해도 다음으로
     *       넘어가고, 끝나면 Car 연결을 놓는다.
     */
    fun run() {
        val car = runCatching { Car.createCar(context.applicationContext) }.getOrNull() // Car 서비스 연결
        val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager // 차량 속성 창구
        if (manager == null) {
            Log.i(TAG, "childlock,연결,CarPropertyManager 없음")
            return
        }
        val id = CHILD_LOCK // 차일드락 속성
        try {
            val config = runCatching { // 속성 설정, 없으면 "설정 없음"
                manager.getCarPropertyConfig(id)?.let { "areas=${it.areaIds.toList()},access=${it.access}" }
                    ?: "설정 없음"
            }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }
            log("설정", "차", config)
            REAR_DOORS.forEach { (name, area) ->
                val before = read(manager, id, area) // 원래 값, 못 읽으면 null
                log("읽기", name, before ?: "실패")
                val target = before != true // 써 볼 값(원래와 반대, 모르면 true)
                runCatching { manager.setBooleanProperty(id, area, target) }
                    .onSuccess { log("쓰기", name, "$target 보냄") }
                    .onFailure { log("쓰기", name, "${it.javaClass.simpleName}: ${it.message}") }
                Thread.sleep(500)
                log("다시 읽기", name, read(manager, id, area) ?: "실패")
                if (before != null) runCatching { manager.setBooleanProperty(id, area, before) }
            }
        } finally {
            car.disconnect()
            Log.i(TAG, "childlock 끝")
        }
    }

    /**
     * 작성: 2026-11-11 11:52:00 · 박건준
     * 함수: read
     * 목적: 문 하나의 차일드락 값을 읽는다. 실패하면 이유를 남긴다.
     * 반환: 켜짐 true, 꺼짐 false, 못 읽으면 null
     */
    private fun read(manager: CarPropertyManager, id: Int, area: Int): Boolean? =
        runCatching { manager.getBooleanProperty(id, area) }
            .onFailure { log("읽기 실패", "area $area", "${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()

    /**
     * 작성: 2026-11-11 11:52:00 · 박건준
     * 함수: log
     * 목적: `childlock,무엇,문,값` 한 줄을 남긴다. CSV를 깨지 않게 쉼표를 바꾼다.
     */
    private fun log(what: String, door: String, value: Any) {
        Log.i(TAG, "childlock,$what,$door,${value.toString().replace(',', ';')}")
    }
}
