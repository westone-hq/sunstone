package com.westone.sunstone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.westone.sunstone.connect.GleoEntry
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.graph

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:50:00 · 박건준
 * 클래스: MainActivity
 * 목적: 앱 화면 하나. 화면을 띄우고, 속도 권한을 받고, Gleo 인텐트를
 *       처리한다. 한 번 뜬 화면에 Gleo 인텐트가 다시 오면 `onNewIntent()`로
 *       받는다(singleTop).
 */
class MainActivity : ComponentActivity() {

    private companion object {
        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:50:00 · 박건준
         * 변수: SPEED
         * 목적: AAOS 속도 권한 이름.
         * 근거: 표준 — AAOS `android.car.permission.CAR_SPEED`(런타임 권한)
         */
        const val SPEED = "android.car.permission.CAR_SPEED"
    }

    /** 속도 권한 요청 창. 허락되면 속도 구독을 시작한다 */
    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) graph.startSpeed() // → 로직 이동: AppGraph.startSpeed()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: onCreate
     * 목적: 화면을 띄우고, 속도 권한을 확인하고, 앱을 연 인텐트가 Gleo 호출이면
     *       처리한다.
     * 인자: savedInstanceState — 안드로이드가 넘기는 이전 화면 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SunstoneScreen(graph) }
        requestSpeed()
        handleGleo(intent)
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
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
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: requestSpeed
     * 목적: 속도 권한이 있으면 바로 구독하고, 없으면 묻는다. 차가 아니라 권한을
     *       물을 수 없으면 속도 없이 진행한다.
     */
    private fun requestSpeed() {
        if (ContextCompat.checkSelfPermission(this, SPEED) == PackageManager.PERMISSION_GRANTED) {
            graph.startSpeed() // → 로직 이동: AppGraph.startSpeed()
        } else {
            runCatching { speedPermission.launch(SPEED) }
        }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:50:00 · 박건준
     * 함수: handleGleo
     * 목적: Gleo(또는 같은 인텐트를 보낸 adb)가 부른 함수를 처리하고 결과를
     *       돌려준다. 요청을 넘기기만 하므로 결과는 늘 성공으로 알린다.
     * 인자: intent — 받은 인텐트. Gleo 호출이 아니면 아무것도 하지 않는다
     */
    private fun handleGleo(intent: Intent?) {
        val action = GleoEntry.parse(intent) ?: return // 알아들은 요청
        graph.handle(action) // → 로직 이동: AppGraph.handle()
        val message = when (action) { // Gleo에 돌려줄 결과 문장
            is VoiceAction.Run -> "${action.request.id} 실행"
            is VoiceAction.Undo -> "되돌리기"
            is VoiceAction.SaveSeat -> "${action.name} 자세 저장"
        }
        GleoEntry.respond(this, intent?.action.orEmpty(), true, message) // → 로직 이동: GleoEntry.respond()
    }
}
