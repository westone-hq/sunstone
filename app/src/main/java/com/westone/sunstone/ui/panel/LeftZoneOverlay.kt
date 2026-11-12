package com.westone.sunstone.ui.panel

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 작성: 2026-11-12 19:00:00 · 박건준
 * 클래스: LeftZoneOverlay
 * 목적: 왼쪽 구역(운전석 쪽 시스템 주행 화면) 위에 패널·듣는 중 막대·토스트·
 *       마이크 칸을 띄운다. 창을 넷으로 나눠, 그릴 것이 없는 창은 크기가 0이
 *       되어 아래 주행 화면 터치를 막지 않는다.
 *       - 패널 — 상태줄 아래 24, 좌우 24
 *       - 듣는 중 막대 — 왼쪽 구역 맨 아래, 폭 전체, 높이 160
 *       - 토스트 — 왼쪽 구역 아래 가운데, 아래에서 184(터치 받지 않음)
 *       - 마이크 칸 — 막대가 없을 때 왼쪽 아래
 *       앱이 뒤에 있어도(내비가 앞) 떠 있다. 그릴 것이 없는 창은 숨기고 터치를
 *       받지 않게 해 아래 주행 화면을 막지 않는다.
 * 인자: context — 앱 Context
 *       scope — 창을 숨기고 보이는 일을 돌린다(화면 스레드)
 *       panels — 그릴 값과 버튼 처리
 * 근거: 측정 — Pleos 에뮬레이터 v2.1.0에서 `TYPE_APPLICATION_OVERLAY` 창이
 *       왼쪽 구역 위에 보이고, 터치를 받고, 내비가 앞에 있어도 보였다
 *       (docs/api-spike.md 왼쪽 구역 그리기 행, relay/files/screen/
 *       01-overlay-app.png·02-overlay-navi.png)
 * 미구현: 프로토타입이라 다른 앱 위에 그리기 허가를 adb(`appops set …
 *       SYSTEM_ALERT_WINDOW allow`)로 준다. 실제 차에서 일반 앱이 이 허가를
 *       받을 수 있는지는 미확인이다. 허가가 없으면 `show()`가 false를 돌려주고
 *       `SunstoneScreen`이 앱 화면 안 왼쪽에 같은 패널을 그린다
 */
class LeftZoneOverlay(
    private val context: Context,
    private val scope: CoroutineScope,
    private val panels: PanelPresenter,
) {

    private companion object {
        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 변수: ZONE_WIDTH
         * 목적: 왼쪽 구역 폭(px = dp). 창 위치는 상태줄(90) 아래, 하단 막대
         *       (140) 위 영역을 기준으로 잰다.
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0 `dumpsys activity`: 주행 화면
         *       task 0~853, 160dpi(시안 854와 1 차이). 오버레이 창의 위·아래
         *       기준은 상태줄·하단 막대를 뺀 영역이었다(2026-11-12 캡처)
         */
        const val ZONE_WIDTH = 853

        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 변수: GAP, TOAST_BOTTOM, SHADOW
         * 목적: 패널 바깥 여백 24, 토스트 아래 위치 184, 그림자를 그릴 창 여백 8.
         * 근거: 인용 — docs/SCREEN.md 1장 위치, 시안 template.html `.noti`·`.toast`
         */
        const val GAP = 24
        const val TOAST_BOTTOM = 184
        const val SHADOW = 8

        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 변수: TAG
         * 목적: 앱 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "Sunstone"
    }

    /** 창 관리자 */
    private val windows = context.getSystemService(WindowManager::class.java)

    /** 띄운 창들. 띄우기 전이면 비어 있다 */
    private val views = mutableListOf<ComposeView>()

    /** 창마다 보이기·숨기기를 맞추는 작업들. 창을 내리면 멈춘다 */
    private val watchers = mutableListOf<Job>()

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 함수: show
     * 목적: 창 넷을 띄운다. 이미 띄웠으면 그대로 둔다.
     * 반환: 떠 있으면 true. 허가가 없거나 실패하면 false
     */
    fun show(): Boolean {
        if (views.isNotEmpty()) return true
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "다른 앱 위에 그리기 허가 없음, 앱 화면 안에 패널을 그린다")
            return false
        }
        return runCatching {
            val panelWidth = ZONE_WIDTH - 2 * (GAP - SHADOW) // 패널 창 폭, 그림자 여백 포함
            add(Gravity.TOP, GAP - SHADOW, GAP - SHADOW, panelWidth, true, { it.panel != null }) {
                val ui by panels.ui.collectAsStateWithLifecycle() // 그릴 값
                ui.panel?.let { p -> Box(Modifier.padding(SHADOW.dp)) { AlertPanel(p, panels::press) } }
            }
            add(Gravity.BOTTOM, 0, TOAST_BOTTOM, ZONE_WIDTH, false, { it.toast != null }) {
                val ui by panels.ui.collectAsStateWithLifecycle() // 그릴 값
                ui.toast?.let { t ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PanelToast(t) }
                }
            }
            add(Gravity.BOTTOM, 0, 0, ZONE_WIDTH, true, { it.listen.state != ListenState.OFF }) {
                val ui by panels.ui.collectAsStateWithLifecycle() // 그릴 값
                if (ui.listen.state != ListenState.OFF) ListenBar(ui.listen)
            }
            add(Gravity.BOTTOM, GAP, GAP, null, true, { it.listen.state == ListenState.OFF }) {
                MicCell(panels::tapMic)
            }
            Log.i(TAG, "왼쪽 구역 창을 띄움")
            true
        }.onFailure { Log.w(TAG, "왼쪽 구역 창 실패: ${it.message}"); hide() }.getOrDefault(false)
    }

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 함수: hide
     * 목적: 띄운 창을 모두 내린다.
     */
    fun hide() {
        watchers.forEach { it.cancel() }
        watchers.clear()
        views.forEach { runCatching { windows.removeView(it) } }
        views.clear()
    }

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 함수: add
     * 목적: 창 하나를 더한다. 높이는 내용만큼이다. 포커스는 받지 않아
     *       내비·주행 화면의 키 입력을 가로채지 않는다. 그릴 것이 없으면 창을
     *       숨기고 터치를 받지 않게 바꾼다.
     * 인자: vertical — 위(TOP)에서 재는지 아래(BOTTOM)에서 재는지
     *       x — 왼쪽에서 거리(px)
     *       y — 위 또는 아래에서 거리(px)
     *       width — 폭(px). null이면 내용만큼
     *       touchable — 보일 때 터치를 받으면 true
     *       visible — 이 창에 그릴 것이 있는지
     *       content — 창에 그릴 내용
     * 근거: 측정 — 내용만 비우면 창 크기가 줄지 않아 마이크 칸이 듣는 중
     *       막대 위에 남았다(2026-11-12 캡처 r1-S2)
     */
    private fun add(
        vertical: Int,
        x: Int,
        y: Int,
        width: Int?,
        touchable: Boolean,
        visible: (ScreenUi) -> Boolean,
        content: @Composable () -> Unit,
    ) {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or // 창 성질
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams( // 창 자리와 크기
            width ?: WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = vertical or Gravity.START
            this.x = x
            this.y = y
        }
        val view = ComposeView(context).apply { // 창 하나
            setViewTreeLifecycleOwner(Owner)
            setViewTreeSavedStateRegistryOwner(Owner)
            setContent { Box(if (width != null) Modifier.width(width.dp) else Modifier) { content() } }
        }
        windows.addView(view, params)
        views += view
        watchers += scope.launch {
            panels.ui.map(visible).distinctUntilChanged().collect { on ->
                view.visibility = if (on) View.VISIBLE else View.GONE
                params.flags = if (on && touchable) flags else flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { windows.updateViewLayout(view, params) }
            }
        }
    }

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 클래스: Owner
     * 목적: 화면(Activity) 밖 창에서 Compose를 돌리려고 늘 켜진 수명 주기를
     *       준다. 앱이 살아 있는 동안 창도 살아 있다.
     */
    private object Owner : SavedStateRegistryOwner {
        /** 늘 RESUMED인 수명 주기 */
        private val registry = LifecycleRegistry(this)

        /** 저장 상태. 창은 다시 만들지 않아 비워 둔다 */
        private val saved = SavedStateRegistryController.create(this)

        init {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 함수: lifecycle
         * 목적: 늘 RESUMED인 수명 주기를 준다.
         */
        override val lifecycle: Lifecycle get() = registry

        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 함수: savedStateRegistry
         * 목적: 비어 있는 저장 상태를 준다.
         */
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    }
}
