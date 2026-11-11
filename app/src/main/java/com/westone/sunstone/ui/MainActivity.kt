package com.westone.sunstone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.westone.sunstone.R
import com.westone.sunstone.connect.GleoEntry
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.graph

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 클래스: MainActivity
 * 목적: 앱 화면 하나. 화면을 띄우고, 속도 권한을 받고, Gleo 인텐트를
 *       처리한다. 한 번 뜬 화면에 Gleo 인텐트가 다시 오면 `onNewIntent()`로
 *       받는다(singleTop).
 */
class MainActivity : ComponentActivity() {

    private companion object {
        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:49:00 · 박건준
         * 변수: SPEED
         * 목적: AAOS 속도 권한 이름.
         * 근거: 표준 — AAOS `android.car.permission.CAR_SPEED`(런타임 권한)
         */
        const val SPEED = "android.car.permission.CAR_SPEED"

        /**
         * 작성: 2026-11-09 20:18:22 · 박건준
         * 변수: ENERGY
         * 목적: AAOS 에너지 권한 이름. 주행 가능 거리·남은 연료·남은 배터리를
         *       읽는 데 필요하다.
         * 근거: 표준 — AAOS `android.car.permission.CAR_ENERGY`(런타임 권한,
         *       에뮬레이터 `prot=dangerous`)
         */
        const val ENERGY = "android.car.permission.CAR_ENERGY"

        /**
         * 작성: 2026-11-10 14:36:00 · 박건준
         * 변수: LOCATION
         * 목적: Pleos 위치(FusedLocation) 권한 이름. 현재 위치·근접 알림을
         *       받는 데 필요하다.
         * 근거: 측정 — 권한 없이 부르면 `need Permission
         *       pleos.car.permission.FUSED_LOCATION`, 에뮬레이터 `dangerous`
         */
        const val LOCATION = "pleos.car.permission.FUSED_LOCATION"

        /**
         * 작성: 2026-11-11 12:24:00 · 박건준
         * 변수: NOTIFY
         * 목적: 알림 권한 이름. 길안내·루틴 중 깨어 있기 알림
         *       (`KeepAwakeService`)을 보이게 한다. 없어도 서비스는 돈다.
         * 근거: 표준 — Android 13부터 `POST_NOTIFICATIONS`는 런타임 권한
         */
        const val NOTIFY = "android.permission.POST_NOTIFICATIONS"
    }

    /** 차 권한 요청 창(속도·에너지·위치·알림 함께). 허락된 것만 구독을 시작한다 */
    private val carPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            results.filterValues { it }.keys.forEach(::startFor)
        }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 20:18:22 · 박건준
     * 함수: onCreate
     * 목적: 화면을 띄우고, 차 권한(속도·에너지)을 확인하고, 앱을 연 인텐트가
     *       Gleo 호출이면 처리한다.
     * 인자: savedInstanceState — 안드로이드가 넘기는 이전 화면 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SunstoneScreen(graph) }
        requestCarPermissions()
        handleGleo(intent)
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: onNewIntent
     * 목적: 화면이 떠 있는 동안 온 Gleo 호출을 처리한다.
     * 인자: intent — 새로 받은 인텐트
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 12:24:00 · 박건준
     * 함수: requestCarPermissions
     * 목적: 차 권한(속도·에너지·위치·알림) 중 이미 있는 것은 바로 구독하고, 없는 것만
     *       한꺼번에 묻는다. 차가 아니라 권한을 물을 수 없으면 그 값 없이
     *       진행한다.
     */
    private fun requestCarPermissions() {
        val missing = listOf(SPEED, ENERGY, LOCATION, NOTIFY).filter { permission -> // 아직 허가받지 못한 권한
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (granted) startFor(permission)
            !granted
        }
        if (missing.isNotEmpty()) runCatching { carPermissions.launch(missing.toTypedArray()) }
    }

    /**
     * 작성: 2026-11-09 20:18:22 · 박건준
     * 수정: 2026-11-11 12:24:00 · 박건준
     * 함수: startFor
     * 목적: 허가받은 권한에 맞는 구독을 시작한다. 위치는 시연 장소 근접
     *       알림을 건다. 알림은 할 일이 없다.
     * 인자: permission — 허가받은 권한 이름
     */
    private fun startFor(permission: String) {
        when (permission) {
            SPEED -> graph.startSpeed() // → 로직 이동: AppGraph.startSpeed()
            ENERGY -> graph.startEnergy() // → 로직 이동: AppGraph.startEnergy()
            LOCATION -> graph.startPlaces() // → 로직 이동: AppGraph.startPlaces()
        }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 14:28:46 · 박희정
     * 함수: handleGleo
     * 목적: Gleo(또는 같은 인텐트를 보낸 adb)가 부른 함수를 처리하고 결과를
     *       돌려준다. 요청을 넘기기만 하므로 결과는 늘 성공으로 알린다.
     * 인자: intent — 받은 인텐트. Gleo 호출이 아니면 아무것도 하지 않는다
     */
    private fun handleGleo(intent: Intent?) {
        val action = GleoEntry.parse(intent) ?: return // 알아들은 요청
        graph.handle(action) // → 로직 이동: AppGraph.handle()
        val message = when (action) { // Gleo에 돌려줄 결과 문장
            is VoiceAction.Run -> getString(R.string.gleo_run, action.request.id.name)
            is VoiceAction.Undo -> getString(R.string.gleo_undo)
            is VoiceAction.SaveSeat -> getString(R.string.gleo_save, action.name)
            is VoiceAction.Answer -> getString(R.string.gleo_answer)
        }
        GleoEntry.respond(this, intent?.action.orEmpty(), true, message) // → 로직 이동: GleoEntry.respond()
    }
}
