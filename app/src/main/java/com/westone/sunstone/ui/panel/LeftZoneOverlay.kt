package com.westone.sunstone.ui.panel

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: LeftZoneOverlay
 * 목적: 왼쪽 구역(운전석 쪽 시스템 주행 화면) 위에 카드·듣는 중 줄·토스트·
 *       마이크 칸을 띄운다. 카드가 뜨는 동안은 미디어 줄 위까지 왼쪽 구역을
 *       쓰고, 볼일이 끝나면 내려 주행 화면(차 그림, 빠른 버튼, 미디어)을
 *       돌려준다. 창은 둘이고 저마다 자기 자리만 잡는다.
 *       - 마이크 칸 창 — 상태줄 바로 아래 왼쪽 112×112
 *       - 카드 창 — 좌우 24, 상태줄 아래 86(화면 176, 운전대 표시 아이콘 아래
 *         16), 폭 805, 높이는 그릴 것만큼(카드 928, 켜짐 표시 88, 듣는 중
 *         줄만 88). 듣는 중 줄과 토스트는 카드 맨 아래에 붙거나 혼자 뜬다
 *       창은 그림자 자리(`SHADOW`)만큼 둘레를 더 잡는다. 그릴 것이 없는 창은
 *       숨기고 터치를 받지 않아, 그 밖의 자리 터치는 시스템 화면으로 간다.
 *       보이는 창은 터치를 받는다(토스트 줄도). 앱이 뒤에 있어도(내비가 앞)
 *       떠 있다.
 * 인자: context — 앱 Context
 *       scope — 창 보이기를 바꾸는 일을 돌린다(화면 스레드)
 *       panels — 그릴 값과 버튼 처리
 * 근거: 인용 — docs/SCREEN.md 6장: 왼쪽 구역을 가리지 않고 카드와 판만 위에
 *       뜬다. 측정 — Pleos 에뮬레이터 v2.1.0에서 `TYPE_APPLICATION_OVERLAY`
 *       창이 왼쪽 구역 위에 보이고, 터치를 받고, 내비가 앞에 있어도 보였다
 *       (docs/api-spike.md 왼쪽 구역 그리기 행). 터치를 받지 않는
 *       (`FLAG_NOT_TOUCHABLE`) 다른 앱 오버레이 창은 시스템이 불투명도를 0.8로
 *       낮춰 토스트 뒤의 미디어 줄이 비쳤다(2026-11-13 v2-S3 캡처). 그래서
 *       보이는 창은 터치를 받게 둔다. 2026-11-13 박건준 지시: 카드가 뜨는
 *       동안 왼쪽 구역을 다 쓰고 듣는 중 줄은 카드 맨 아래로
 * 미구현: 프로토타입이라 다른 앱 위에 그리기 허가를 adb(`appops set …
 *       SYSTEM_ALERT_WINDOW allow`)로 준다. 실제 차에서 일반 앱이 이 허가를
 *       받을 수 있는지는 미확인이다. 허가가 없으면 `show()`가 false를 돌려주고
 *       `SunstoneScreen`이 앱 화면 안 왼쪽에 같은 카드를 그린다
 */
class LeftZoneOverlay(
    private val context: Context,
    private val scope: CoroutineScope,
    private val panels: PanelPresenter,
) {

    private companion object {
        /**
         * 작성: 2026-11-12 19:00:00 · 박건준
         * 변수: TAG
         * 목적: 앱 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        const val TAG = "Sunstone"
    }

    /** 창 관리자 */
    private val windows = context.getSystemService(WindowManager::class.java)

    /** 띄운 창들. 띄우기 전이면 비어 있다 */
    private val views = mutableListOf<ComposeView>()

    /** 창 보이기를 맞추는 작업들. 창을 내리면 멈춘다 */
    private val watchers = mutableListOf<Job>()

    /**
     * 작성: 2026-11-12 19:00:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: show
     * 목적: 마이크 칸·카드 창을 띄운다. 이미 띄웠으면 그대로 둔다.
     * 반환: 떠 있으면 true. 허가가 없거나 실패하면 false
     */
    fun show(): Boolean {
        if (views.isNotEmpty()) return true
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "다른 앱 위에 그리기 허가 없음, 앱 화면 안에 카드를 그린다")
            return false
        }
        return runCatching {
            addMic()
            addCard()
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
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: addMic
     * 목적: 마이크 칸 창(112×112)을 상태줄 바로 아래 왼쪽에 더한다. 카드나 듣는
     *       중 줄이 떠 있으면 숨긴다.
     */
    private fun addMic() {
        val params = layout(Gravity.TOP or Gravity.START, 0, 0, 112, 112) // 창 자리와 크기
        val view = compose { MicCell(panels::tapMic, panels::hold) } // 마이크 칸 창
        add(view, params) { ui -> if (ui.mic) Touch.ON else Touch.HIDDEN }
    }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: addCard
     * 목적: 카드 창을 더한다. 폭은 카드 폭 + 그림자 둘레, 높이는 그릴 것만큼.
     *       카드가 있으면 카드(듣는 중 줄은 그 안 맨 아래), 없으면 듣는 중
     *       줄만 그린다. 둘 다 없으면 숨긴다.
     */
    private fun addCard() {
        val params = layout( // 창 자리와 크기
            Gravity.TOP or Gravity.START,
            CARD_SIDE - SHADOW,
            CARD_TOP - SHADOW,
            CARD_WIDTH + 2 * SHADOW,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        val view = compose { // 카드 창
            val ui by panels.ui.collectAsStateWithLifecycle() // 그릴 값
            Box(Modifier.padding(SHADOW.dp)) { CardOrBar(ui, panels) }
        }
        add(view, params) { ui -> if (ui.card != null || ui.plate != null) Touch.ON else Touch.HIDDEN }
    }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 클래스: Touch
     * 목적: 창 하나의 보이기와 터치.
     *       - `ON` — 보이고 터치를 받는다
     *       - `HIDDEN` — 숨기고 터치도 받지 않는다(터치는 아래 시스템 화면으로)
     */
    private enum class Touch { ON, HIDDEN }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 함수: add
     * 목적: 창을 띄우고, 그릴 값이 바뀔 때마다 보이기와 터치를 맞춘다.
     * 인자: view — 띄울 뷰
     *       params — 창 자리와 크기
     *       touch — 그릴 값에서 보이기·터치를 정한다
     */
    private fun add(view: ComposeView, params: WindowManager.LayoutParams, touch: (ScreenUi) -> Touch) {
        windows.addView(view, params)
        views += view
        watchers += scope.launch {
            panels.ui.map(touch).distinctUntilChanged().collect { t ->
                view.visibility = if (t == Touch.ON) View.VISIBLE else View.GONE
                params.flags = if (t == Touch.ON) {
                    params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                runCatching { windows.updateViewLayout(view, params) }
            }
        }
    }

    /**
     * 작성: 2026-11-12 19:52:00 · 박건준
     * 수정: 2026-11-13 03:40:00 · 박건준
     * 함수: layout
     * 목적: 다른 앱 위에 뜨는 창 값을 만든다. 포커스는 받지 않아 내비·주행
     *       화면의 키 입력을 가로채지 않는다. 처음에는 터치를 받지 않는다.
     * 인자: gravity — 기준 모서리
     *       x, y — 기준 모서리에서 떨어진 거리(px = dp). 위·아래 기준은
     *       상태줄(90)·하단 막대(140)를 뺀 영역이다
     *       width, height — 창 크기(px = dp). `WRAP_CONTENT`면 내용만큼
     * 반환: 창 값
     */
    private fun layout(gravity: Int, x: Int, y: Int, width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        this.x = x
        this.y = y
    }

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
