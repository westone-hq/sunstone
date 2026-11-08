package com.westone.sunstone.connect

import android.content.Context
import android.content.Intent
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Source
import org.json.JSONObject

/**
 * Gleo AI 연결. assets/schema.json의 함수 3개를 Gleo가 Activity 인텐트로 부른다.
 * 파라미터는 "ACTION_PARAM" 엑스트라에 JSON 문자열로 온다(다른 팀 구현과 같은 방식).
 * 처리 결과는 APP_ACTION_RESPONSE 브로드캐스트로 돌려준다.
 *
 * 공모전 SDK 목록에 Gleo가 없어서 실제로 불리지 않을 수 있다. 같은 인텐트를 adb로 쏴서 시연한다.
 */
object GleoEntry {
    const val RUN = "RUN_ROUTINE"
    const val UNDO = "UNDO_ROUTINE"
    const val SAVE = "SAVE_SEAT_PROFILE"
    private const val PARAM = "ACTION_PARAM"
    private const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    fun parse(intent: Intent?): VoiceAction? {
        val action = intent?.action ?: return null
        if (action !in setOf(RUN, UNDO, SAVE)) return null
        val json = runCatching { JSONObject(intent.getStringExtra(PARAM) ?: "{}") }.getOrDefault(JSONObject())
        val routine = runCatching { RoutineId.valueOf(json.optString("routine")) }.getOrNull()
        return when (action) {
            RUN -> routine?.let {
                VoiceAction.Run(RunRequest(it, json.optString("profile").ifBlank { null }, Source.GLEO))
            }
            UNDO -> VoiceAction.Undo(routine)
            else -> VoiceAction.SaveSeat(json.optString("name").ifBlank { "나" })
        }
    }

    fun respond(context: Context, function: String, ok: Boolean, message: String) {
        val response = JSONObject()
            .put("functionName", function)
            .put("status", if (ok) "SUCCESS" else "FAILURE")
            .put("message", message)
        context.sendBroadcast(
            Intent(RESPONSE)
                .putExtra("packageName", context.packageName)
                .putExtra("response", response.toString()),
        )
    }
}
