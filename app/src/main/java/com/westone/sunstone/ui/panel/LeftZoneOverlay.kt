package com.westone.sunstone.ui.panel

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
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
 * 수정: 2026-11-12 20:12:00 · 박건준
 * 클래스: LeftZoneOverlay
 * 목적: 왼쪽 구역(운전석 쪽 시스템 주행 화면) 위에 패널·듣는 중 막대·토스트·
 *       마이크 칸을 띄운다. 창은 둘이다.
 *       - 구역 창 — 패널이나 듣는 중 막대가 떠 있으면 상태줄 아래부터 하단
 *         막대 위까지(853×1210)를 불투명 배경으로 덮고 패널·막대를 그린다.
 *         아무것도 없으면 상태줄 바로 아래 왼쪽 마이크 칸(112) 크기로 줄여
 *         주행 화면을 막지 않는다
 *       - 토스트 창 — 왼쪽 구역 아래 가운데, 아래에서 184. 터치를 받지 않고
 *         구역 창 위에 뜬다. 토스트가 없으면 숨긴다
 *       앱이 뒤에 있어도(내비가 앞) 떠 있다. 맨 위 속도·기어 줄(상태줄 자리)은
 *       가리지 않는다.
 * 인자: context — 앱 Context
 *       scope — 창 크기·보이기를 바꾸는 일을 돌린다(화면 스레드)
 *       panels — 그릴 값과 버튼 처리
 * 근거: 측정 — Pleos 에뮬레이터 v2.1.0에서 `TYPE_APPLICATION_OVERLAY` 창이
 *       왼쪽 구역 위에 보이고, 터치를 받고, 내비가 앞에 있어도 보였다
 *       (docs/api-spike.md 왼쪽 구역 그리기 행, relay/files/screen/
 *       01-overlay-app.png·02-overlay-navi.png). 시안 `.drive.busy`는 패널이
 *       뜨면 주행 화면 원래 내용을 숨긴다
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
         * 수정: 2026-11-12 19:52:00 · 박건준
         * 변수: ZONE_WIDTH, ZONE_HEIGHT
         * 목적: 왼쪽 구역 폭과, 상태줄 아래부터 하단 막대 위까지 높이(px = dp).
         *       창 위치는 상태줄(90) 아래, 하단 막대(140) 위 영역을 기준으로 잰다.
         * 근거: 측정 — Pleos 에뮬레이터 v2.1.0 `dumpsys activity`: 주행 화면
         *       task 0~853 × 0~1300, 상태줄 inset 90, 160dpi(시안 854와 1 차이).
         *       오버레이 창의 위·아래 기준은 상태줄·하단 막대를 뺀 영역이었다
         *       (2026-11-12 캡처)
         */
        const val ZONE_WIDTH = 853
        const val ZONE_HEIGHT = 1210

        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 수정: 2026-11-12 19:52:00 · 박건준
         * 변수: TOAST_BOTTOM
         * 목적: 토스트 아래 위치 184.
         * 근거: 인용 — docs/SCREEN.md 1장 위치, 시안 template.html `.toast`
         */
        const val TOAST_BOTTOM = 184

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

    /** 창 크기·보이기를 맞추는 작업들. 창을 내리면 멈춘다 */
    private val watchers = mutableListOf<Job>()

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 수정: 2026-11-12 19:52:00 · 박건준
     * 함수: show
     * 목적: 구역 창과 토스트 창을 띄운다. 이미 띄웠으면 그대로 둔다.
     * 반환: 떠 있으면 true. 허가가 없거나 실패하면 false
     */
    fun show(): Boolean {
        if (views.isNotEmpty()) return true
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "다른 앱 위에 그리기 허가 없음, 앱 화면 안에 패널을 그린다")
            return false
        }
        return runCatching {
            addZone()
            addToast()
            Log.i(TAG, "왼쪽 구역 창을 띄움")
            true
        }.onFailure { Log.w(TAG, "왼쪽 구역 창 실패: ${it.message}"); hide() }.getOrDefault(false)
    }

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 수정: 2026-11-12 19:52:00 · 박건준
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
     * 작성: 2026-11-12 19:52:00 · 박건준
     * 수정: 2026-11-12 20:12:00 · 박건준
     * 함수: addZone
     * 목적: 구역 창을 더한다. 덮을 때(`zoneFull`)는 구역 전체 크기로, 아니면
     *       마이크 칸 크기로 상태줄 바로 아래 왼쪽에 둔다. 포커스는 받지 않아 내비·주행
     *       화면의 키 입력을 가로채지 않는다.
     */
    private fun addZone() {
        val params = layout(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) // 창 자리와 크기
        val view = compose { // 구역 창
            val ui by panels.ui.collectAsStateWithLifecycle() // 그릴 값
            LeftZone(ui, panels::press, panels::tapMic)
        }
        place(params, full = false)
        windows.addView(view, params)
        views += view
        watchers += scope.launch {
            panels.ui.map(::zoneFull).distinctUntilChanged().collect { full ->
                place(params, full)
                runCatching { windows.updateViewLayout(view, params) }
            }
        }
    }

    /**
     * 작성: 2026-11-12 19:52:00 · 박건준
     * 함수: addToast
     * 목적: 토스트 창을 더한다. 폭은 구역과 같고 가운데에 그린다. 터치를 받지
     *       않는다. 토스트가 없으면 숨긴다.
     * 근거: 측정 — 내용만 비우면 창 크기가 줄지 않아, 없을 때는 창을 숨긴다
     *       (2026-11-12 캡처 r1-S2)
     */
    private fun addToast() {
        val params = layout( // 창 자리와 크기
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        ).apply {
            width = ZONE_WIDTH
            gravity = Gravity.BOTTOM or Gravity.START
            y = TOAST_BOTTOM
        }
        val view = compose { // 토스트 창
            val ui by panels.ui.collectAsStateWithLifecycle() // 그릴 값
            ui.toast?.let { t ->
                Box(Modifier.width(ZONE_WIDTH.dp), contentAlignment = Alignment.Center) { PanelToast(t) }
            }
        }
        windows.addView(view, params)
        views += view
        watchers += scope.launch {
            panels.ui.map { it.toast != null }.distinctUntilChanged().collect { on ->
                view.visibility = if (on) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * 작성: 2026-11-12 19:52:00 · 박건준
     * 수정: 2026-11-12 20:12:00 · 박건준
     * 함수: place
     * 목적: 구역 창 자리와 크기를 정한다.
     * 인자: params — 바꿀 창 값
     *       full — 구역 전체를 덮으면 true, 마이크 칸만이면 false
     */
    private fun place(params: WindowManager.LayoutParams, full: Boolean) {
        params.width = if (full) ZONE_WIDTH else WindowManager.LayoutParams.WRAP_CONTENT
        params.height = if (full) ZONE_HEIGHT else WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
    }

    /**
     * 작성: 2026-11-12 19:52:00 · 박건준
     * 함수: layout
     * 목적: 다른 앱 위에 뜨는 창 값을 만든다. 크기는 내용만큼으로 시작한다.
     * 인자: flags — 창 성질(포커스·터치)
     * 반환: 창 값
     */
    private fun layout(flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    /**
     * 작성: 2026-11-12 19:52:00 · 박건준
     * 함수: compose
     * 목적: 화면(Activity) 밖 창에서 돌 Compose 뷰를 만든다.
     * 인자: content — 그릴 내용
     * 반환: 뷰
     */
    private fun compose(content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewTreeLifecycleOwner(Owner)
        setViewTreeSavedStateRegistryOwner(Owner)
        setContent(content)
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
