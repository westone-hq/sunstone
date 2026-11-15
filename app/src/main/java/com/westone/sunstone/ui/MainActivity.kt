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
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: MainActivity
 * 목적: 하단 막대 Sunstone 아이콘으로 여는 입구. 화면을 그리지 않는다.
 *       왼쪽 구역 창을 띄우고 부르는 카드를 연 뒤 바로 닫혀, 오른쪽 앱 영역은
 *       앞에 있던 내비 화면이 그대로 남는다(투명 테마). 첫 실행이면 차 권한을
 *       묻고 답을 받은 뒤 닫힌다. Gleo 인텐트도 받는다. 왼쪽 구역 창을 띄우지
 *       못하면 닫히지 않고 앱 화면 안에 같은 카드를 그린다(`SunstoneScreen`).
 * 근거: 인용 — 2026-11-13 박건준 지시: Sunstone은 왼쪽 구역만 쓰고 부르면
 *       왼쪽에 루틴 세 줄만 나온다
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

        /**
         * 작성: 2026-11-14 19:30:00 · 박건준
         * 변수: MIC
         * 목적: 마이크 권한 이름. 운전자 말을 듣는 데 쓴다(`PleosEar`). 없으면 듣지
         *       않고 카드 버튼으로만 대답한다.
         * 근거: 표준 — `RECORD_AUDIO`는 런타임 권한
         */
        const val MIC = "android.permission.RECORD_AUDIO"
    }

    /** 차 권한 요청 창(속도·에너지·위치·알림 함께). 허락된 것만 구독을 시작하고, 앱 화면이 없으면 닫는다 */
    private val carPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            results.filterValues { it }.keys.forEach(::startFor)
            asking = false
            closeIfOverlay()
        }

    /** 왼쪽 구역 창 대신 앱 화면 안에 카드를 그리는지. `onCreate()`에서 정한다 */
    private var inApp = false

    /** 차 권한을 묻는 중인지. 답을 받기 전에는 닫지 않는다 */
    private var asking = false

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: onCreate
     * 목적: 왼쪽 구역 창을 띄우고, 하단 막대 아이콘으로 열렸으면 부르는 카드를
     *       연다. 창을 못 띄우면 앱 화면 안에 그린다. 차 권한을 확인하고, 앱을
     *       연 인텐트가 Gleo 호출이면 처리한다.
     * 인자: savedInstanceState — 안드로이드가 넘기는 이전 화면 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph.showPanels() // → 로직 이동: AppGraph.showPanels()
        inApp = !graph.overlayShown.value
        if (inApp) setContent { SunstoneScreen(graph) }
        if (intent?.action == Intent.ACTION_MAIN) graph.panels.tapMic() // → 로직 이동: PanelPresenter.tapMic()
        requestCarPermissions()
        handleGleo(intent)
    }

    /**
     * 작성: 2026-11-12 15:04:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: onStart
     * 목적: 화면이 뜰 때마다 앱을 깨어 있게 한다. 앱이 앞에 있을 때만
     *       포그라운드 서비스를 켤 수 있어서 여기서 부른다. 왼쪽 구역 창은 한 번
     *       뜨면 이 화면이 닫혀도 남는다.
     */
    override fun onStart() {
        super.onStart()
        graph.keepAwake() // → 로직 이동: AppGraph.keepAwake()
    }

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 함수: onResume
     * 목적: 앞에 나온 뒤(서비스를 켠 뒤) 앱 화면이 없으면 닫는다.
     */
    override fun onResume() {
        super.onResume()
        closeIfOverlay()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: onNewIntent
     * 목적: 화면이 떠 있는 동안(권한을 묻는 중, 앱 화면 안에 그리는 중) 온
     *       하단 막대 아이콘·Gleo 호출을 처리한다.
     * 인자: intent — 새로 받은 인텐트
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) graph.panels.tapMic() // → 로직 이동: PanelPresenter.tapMic()
        handleGleo(intent)
    }

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 함수: closeIfOverlay
     * 목적: 왼쪽 구역 창이 떠 있고 권한을 묻는 중이 아니면 화면을 닫는다.
     *       닫히면 앞에 있던 내비 화면이 오른쪽에 그대로 보인다.
     */
    private fun closeIfOverlay() {
        if (!inApp && !asking && !isFinishing) finish()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: requestCarPermissions
     * 목적: 차 권한(속도·에너지·위치·알림) 중 이미 있는 것은 바로 구독하고, 없는 것만
     *       한꺼번에 묻는다. 묻는 동안은 화면을 닫지 않는다. 차가 아니라 권한을
     *       물을 수 없으면 그 값 없이 진행한다.
     */
    private fun requestCarPermissions() {
        val missing = listOf(SPEED, ENERGY, LOCATION, NOTIFY, MIC).filter { permission -> // 아직 허가받지 못한 권한
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (granted) startFor(permission)
            !granted
        }
        if (missing.isEmpty()) return
        asking = runCatching { carPermissions.launch(missing.toTypedArray()) }.isSuccess
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
     * 수정: 2026-11-13 15:00:00 · 박희정
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
            is VoiceAction.Navigate -> getString(R.string.gleo_navigate, action.destination)
            is VoiceAction.Ask -> getString(R.string.gleo_ask)
            is VoiceAction.SeatStart, is VoiceAction.SeatAdjust, is VoiceAction.SeatRecall, is VoiceAction.Name ->
                getString(R.string.gleo_seat)
        }
        GleoEntry.respond(this, intent?.action.orEmpty(), true, message) // → 로직 이동: GleoEntry.respond()
    }
}
