package com.westone.sunstone.connect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.westone.sunstone.R

/**
 * 작성: 2026-11-11 12:14:00 · 박건준
 * 수정: 2026-11-12 15:04:00 · 박건준
 * 클래스: KeepAwakeService
 * 목적: 알림 하나를 띄워 앱을 깨어 있게 한다. 내비 화면이 앞에 오면 앱이
 *       뒤로 가 얼어서(cached apps freezer) 내비 이벤트·근접 알림을 늦게
 *       받기 때문이다. Pleos 차에서 앱 화면이 뜰 때 켜고 끄지 않는다
 *       (`AppGraph.keepAwake`).
 * 근거: 측정 — 앱이 뒤에서 얼어 경로 시작 이벤트가 6분 44초 늦게 왔고,
 *       길안내·루틴이 없을 때는 주유소 근접 알림을 못 받았다
 *       (docs/api-spike.md 앱 얼림 대응 절). 표준 — Android 14부터
 *       포그라운드 서비스는 종류를 밝혀야 하고, 위치·미디어 같은 정해진
 *       종류에 맞지 않아 `specialUse`로 두고 이유를 매니페스트에 적는다
 */
class KeepAwakeService : Service() {

    companion object {
        /**
         * 작성: 2026-11-11 12:14:00 · 박건준
         * 변수: TAG
         * 목적: 앱 전반 logcat 태그.
         * 근거: 표준 — 로그 태그는 Sunstone·SunstoneCar·SunstoneVoice·SunstoneDemo 넷
         */
        private const val TAG = "Sunstone"

        /**
         * 작성: 2026-11-11 12:14:00 · 박건준
         * 변수: CHANNEL, NOTIFICATION_ID
         * 목적: 알림 채널 이름과 알림 번호. 알림은 하나만 쓴다.
         */
        private const val CHANNEL = "keep_awake"
        private const val NOTIFICATION_ID = 1

        /**
         * 작성: 2026-11-11 12:14:00 · 박건준
         * 함수: start
         * 목적: 서비스를 시작한다. 이미 돌고 있으면 알림만 다시 띄운다. 앱이
         *       뒤에 있을 때 시작이 막히면(Android 12+) logcat에만 남긴다.
         * 인자: context — 앱 Context
         */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, KeepAwakeService::class.java)) }
                .onSuccess { Log.i(TAG, "깨어 있기 시작") }
                .onFailure { Log.w(TAG, "깨어 있기 시작 못 함: ${it.message}") }
        }

    }

    /**
     * 작성: 2026-11-11 12:14:00 · 박건준
     * 함수: onStartCommand
     * 목적: 알림을 띄우고 포그라운드로 올린다. 시스템이 서비스를 죽이면
     *       다시 만들지 않는다(필요하면 앱이 다시 시작한다).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java) // 알림 창구
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.awake_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL) // 띄울 알림
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.awake_title))
            .setContentText(getString(R.string.awake_text))
            .setOngoing(true)
            .build()
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE // 서비스 종류(매니페스트와 같게)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_NOT_STICKY
    }

    /**
     * 작성: 2026-11-11 12:14:00 · 박건준
     * 함수: onBind
     * 목적: 묶어 쓰지 않는다.
     * 반환: 늘 null
     */
    override fun onBind(intent: Intent?): IBinder? = null
}
