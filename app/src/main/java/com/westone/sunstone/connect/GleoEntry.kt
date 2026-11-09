package com.westone.sunstone.connect

import android.content.Context
import android.content.Intent
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Source
import org.json.JSONObject

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 클래스: GleoEntry
 * 목적: Gleo AI(Pleos 음성 비서) 연결. assets/schema.json의 함수 3개를
 *       Gleo가 Activity 인텐트로 부르면 요청으로 바꾸고, 결과를 방송으로
 *       돌려준다.
 * 근거: 인용 — 다른 팀 구현: 인자는 `ACTION_PARAM` 엑스트라에 JSON 글자로
 *       오고, 결과는 `APP_ACTION_RESPONSE` 방송으로 돌려준다
 * 미확인: 공모전 SDK 목록에 Gleo가 없어 실제로 불리는지 확인하지 못했다.
 *       시연은 같은 인텐트를 adb로 보내 `MainActivity.handleGleo()`로
 *       들어오게 한다
 */
object GleoEntry {
    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 변수: RUN, UNDO, SAVE
     * 목적: Gleo 함수 이름(인텐트 action). assets/schema.json·매니페스트와
     *       같아야 한다.
     */
    const val RUN = "RUN_ROUTINE" // 루틴 실행
    const val UNDO = "UNDO_ROUTINE" // 루틴 되돌리기
    const val SAVE = "SAVE_SEAT_PROFILE" // 시트 자세 저장

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 변수: PARAM, RESPONSE
     * 목적: 인자를 담은 엑스트라 이름과 결과 방송 action.
     * 근거: 인용 — 다른 팀 구현과 같은 이름
     */
    private const val PARAM = "ACTION_PARAM" // 인자 JSON 엑스트라
    private const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE" // 결과 방송

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: parse
     * 목적: Gleo 인텐트를 요청으로 바꾼다. 저장 이름이 비어 있으면 "나"로
     *       저장한다.
     * 인자: intent — 받은 인텐트. null이면 Gleo 호출이 아니다
     * 반환: 요청. Gleo 함수가 아니거나 실행할 루틴 이름이 틀리면 null
     */
    fun parse(intent: Intent?): VoiceAction? {
        val action = intent?.action ?: return null // Gleo 함수 이름
        if (action !in setOf(RUN, UNDO, SAVE)) return null
        val json = runCatching { JSONObject(intent.getStringExtra(PARAM) ?: "{}") } // 인자, 깨졌으면 빈 값
            .getOrDefault(JSONObject())
        val routine = runCatching { RoutineId.valueOf(json.optString("routine")) }.getOrNull() // 대상 루틴, 없으면 null
        return when (action) {
            RUN -> routine?.let {
                VoiceAction.Run(RunRequest(it, json.optString("profile").ifBlank { null }, Source.GLEO))
            }
            UNDO -> VoiceAction.Undo(routine)
            else -> VoiceAction.SaveSeat(json.optString("name").ifBlank { "나" })
        }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: respond
     * 목적: 처리 결과를 Gleo에 방송으로 돌려준다.
     * 인자: context — 방송을 보낼 Context
     *       function — 처리한 Gleo 함수 이름
     *       ok — 성공이면 true
     *       message — Gleo가 사용자에게 보일 결과 문장
     */
    fun respond(context: Context, function: String, ok: Boolean, message: String) {
        val response = JSONObject() // 결과 JSON
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
