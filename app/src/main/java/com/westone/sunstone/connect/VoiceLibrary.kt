package com.westone.sunstone.connect

import android.content.Context
import java.security.MessageDigest

/**
 * 작성: 2026-11-12 14:52:00 · 박건준
 * 수정: 2026-11-13 21:00:00 · 박희정
 * 클래스: VoiceLibrary
 * 목적: 문장에 맞는 녹음본을 찾는다. 녹음본은 assets/voice/<문장 MD5>.wav로
 *       있고, 앱이 켜질 때 목록을 한 번 읽는다. 말은 Pleos TTS로 읽고,
 *       녹음본은 TTS를 쓸 수 없을 때 대신 낸다(`VoicePlayer`).
 * 인자: context — 앱 Context. assets를 읽는다
 * 근거: 표준 — tools/make-voice.ps1의 `Get-Md5`와 같은 방식(문장 UTF-8의
 *       MD5, 소문자 16진수)이어야 파일을 찾는다
 * 미구현: 프로토타입이라 대사집 문장만 Windows 한국어 음성으로 미리 녹음해
 *       둔다. TTS 준비가 늦거나 실패할 때 시연이 끊기지 않게 하려는 예비다.
 *       대사를 고치면 tools/make-voice.ps1을 다시 돌려야 한다
 */
class VoiceLibrary(context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-11 17:40:00 · 박희정
         * 수정: 2026-11-12 14:52:00 · 박건준
         * 변수: VOICE_DIR
         * 목적: 녹음본이 있는 assets 폴더 이름. tools/make-voice.ps1이 채운다.
         */
        const val VOICE_DIR = "voice"
    }

    /** 있는 녹음본 파일 이름들(<문장 MD5>.wav). 만들 때 한 번 읽는다. 폴더가 없으면 빈 집합 */
    private val recorded: Set<String> =
        runCatching { context.applicationContext.assets.list(VOICE_DIR)?.toSet() }.getOrNull().orEmpty()

    /**
     * 작성: 2026-11-12 14:52:00 · 박건준
     * 함수: find
     * 목적: 문장의 녹음본 assets 경로를 찾는다.
     * 인자: text — 문장. 글자 하나라도 다르면 다른 문장이다
     * 반환: "voice/<MD5>.wav". 녹음본이 없으면 null
     */
    fun find(text: String): String? = "${md5(text)}.wav".takeIf { it in recorded }?.let { "$VOICE_DIR/$it" }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 수정: 2026-11-12 14:52:00 · 박건준
     * 함수: md5
     * 목적: 문장 UTF-8의 MD5를 소문자 16진수로 만든다. 녹음본 파일 이름이다.
     * 인자: text — 문장
     * 반환: 32글자 16진수
     */
    private fun md5(text: String): String = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
