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

class MainActivity : ComponentActivity() {

    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) graph.startSpeed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SunstoneScreen(graph) }
        requestSpeed()
        handleGleo(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

    private fun requestSpeed() {
        if (ContextCompat.checkSelfPermission(this, SPEED) == PackageManager.PERMISSION_GRANTED) {
            graph.startSpeed()
        } else {
            runCatching { speedPermission.launch(SPEED) }
        }
    }

    /** Gleo(또는 같은 인텐트를 쏜 adb)가 부른 함수를 처리하고 결과를 돌려준다. */
    private fun handleGleo(intent: Intent?) {
        val action = GleoEntry.parse(intent) ?: return
        graph.handle(action)
        val message = when (action) {
            is VoiceAction.Run -> "${action.request.id} 실행"
            is VoiceAction.Undo -> "되돌리기"
            is VoiceAction.SaveSeat -> "${action.name} 자세 저장"
        }
        GleoEntry.respond(this, intent?.action.orEmpty(), true, message)
    }

    private companion object {
        const val SPEED = "android.car.permission.CAR_SPEED"
    }
}
