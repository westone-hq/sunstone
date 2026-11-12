package com.westone.sunstone.demo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * 작성: 2026-11-12 18:05:00 · 박건준
 * 클래스: OverlayProbe
 * 목적: 왼쪽 구역(시스템 주행 화면) 위에 앱 창을 띄울 수 있는지와, 운전대 음성
 *       버튼 키를 앱이 받는지 실측한다. logcat `overlay,` 줄로 남긴다.
 *       - `show` — x=0, y=92, 854×1208 오버레이 창을 띄운다. 창을 누르면
 *         `overlay,touch`, 키가 오면 `overlay,key`를 남긴다
 *       - `hide` — 창을 내린다
 * 인자: context — 앱 Context
 * 근거: 표준 — Android `TYPE_APPLICATION_OVERLAY`는 `SYSTEM_ALERT_WINDOW`
 *       허가(adb `appops set … SYSTEM_ALERT_WINDOW allow`)가 있어야 뜬다
 */
internal class OverlayProbe(private val context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-12 18:05:00 · 박건준
         * 변수: TAG
         * 목적: 데모 logcat 태그.
         */
        const val TAG = "SunstoneDemo"

        /**
         * 작성: 2026-11-12 18:05:00 · 박건준
         * 변수: LEFT_X, LEFT_Y, LEFT_W, LEFT_H
         * 목적: 왼쪽 구역에서 상태줄을 뺀 자리(px).
         * 근거: 인용 — docs/SCREEN.md 1장 위치(왼쪽 구역 폭 854). 높이는 상태줄
         *       아래부터 하단 막대 위(1300)까지
         */
        const val LEFT_X = 0
        const val LEFT_Y = 92
        const val LEFT_W = 854
        const val LEFT_H = 1208

        /** 띄운 확인 창. 수신기는 방송마다 새로 만들어져서 여기에 둔다. 없으면 null */
        var shown: TextView? = null
    }

    /** 창 관리자. 오버레이 창을 더하고 뺀다 */
    private val windows = context.getSystemService(WindowManager::class.java)

    /**
     * 작성: 2026-11-12 18:05:00 · 박건준
     * 함수: show
     * 목적: 확인 창을 띄운다. 허가가 없으면 띄우지 않고 남긴다. 키를 받아 보려고
     *       창이 포커스를 받게 둔다(창 밖 터치는 아래 앱으로 넘긴다).
     */
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show() {
        val allowed = Settings.canDrawOverlays(context) // 다른 앱 위에 그리기 허가
        Log.i(TAG, "overlay,allowed,$allowed")
        if (!allowed || shown != null) return
        val view = object : TextView(context) { // 확인 창, 받은 키를 남긴다
            /**
             * 작성: 2026-11-12 18:05:00 · 박건준
             * 함수: dispatchKeyEvent
             * 목적: 이 창에 온 키를 logcat에 남긴다.
             */
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                Log.i(TAG, "overlay,key,${KeyEvent.keyCodeToString(event.keyCode)},${event.action}")
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            text = "Sunstone 오버레이 확인\n누르면 logcat에 남습니다"
            textSize = 30f
            setTextColor(Color.parseColor("#131417"))
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) Log.i(TAG, "overlay,touch,${e.rawX.toInt()},${e.rawY.toInt()}")
                false
            }
        }
        val params = WindowManager.LayoutParams( // 왼쪽 구역 자리
            LEFT_W, LEFT_H,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = LEFT_X
            y = LEFT_Y
        }
        runCatching { windows.addView(view, params) }
            .onSuccess { shown = view; view.requestFocus(); Log.i(TAG, "overlay,shown") }
            .onFailure { Log.w(TAG, "overlay,failed,${it.message}") }
    }

    /**
     * 작성: 2026-11-12 18:05:00 · 박건준
     * 함수: hide
     * 목적: 확인 창을 내린다.
     */
    fun hide() {
        shown?.let { runCatching { windows.removeView(it) } }
        shown = null
        Log.i(TAG, "overlay,hidden")
    }
}
