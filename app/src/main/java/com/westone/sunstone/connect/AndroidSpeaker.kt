package com.westone.sunstone.connect

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.westone.sunstone.routines.port.Speaker
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-10 12:56:20 · 박건준
 * 클래스: AndroidSpeaker
 * 목적: 음성 안내. 안드로이드 기본 TTS(글자를 소리로 읽는 기능)로 한국어를
 *       읽는다. TTS가 준비되기 전에 온 문장은 모았다가 준비되면 읽는다.
 * 인자: context — 앱 Context
 * 근거: 인용 — Pleos Playground에서 Pleos TTS는 "준비 중"이라 기본 TTS를
 *       쓴다. 시연 음성을 녹음 파일로 바꿀 수 있게 `Speaker` 뒤에 둔다
 * 미구현: 프로토타입이라 Pleos TTS 대신 안드로이드 기본 TTS로 읽는다. Pleos TTS가
 *       열리면 `Speaker` 구현만 바꾼다
 */
class AndroidSpeaker(context: Context) : Speaker, AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-08 18:03:56 · 박건준
         * 수정: 2026-11-09 18:49:00 · 박건준
         * 변수: TAG
         * 목적: 음성 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneVoice"
    }

    /** TTS 준비가 끝났는지. 초기화 콜백이 채운다 */
    private var ready = false

    /** 준비 전에 온 문장. 준비되면 읽고 비운다 */
    private val pending = mutableListOf<String>()

    /** 읽기 요청마다 붙이는 번호 */
    private val ids = AtomicInteger()

    /** 기본 TTS. 준비되면 한국어로 맞추고 모아 둔 문장을 읽는다 */
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            setKorean()
            pending.forEach(::speak)
        } else {
            Log.w(TAG, "TTS 초기화 실패: $status")
        }
        pending.clear()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: say
     * 목적: 문장 하나를 읽는다. 앞 문장이 끝난 뒤 이어서 읽는다.
     * 인자: text — 읽을 문장
     */
    override fun say(text: String) {
        Log.i(TAG, "말: $text")
        if (ready) speak(text) else pending += text
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: close
     * 목적: TTS를 놓는다.
     */
    override fun close() = tts.shutdown()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: setKorean
     * 목적: TTS 언어를 한국어로 맞춘다.
     */
    private fun setKorean() {
        tts.language = Locale.KOREAN
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: speak
     * 목적: TTS 줄 뒤에 문장을 붙인다.
     * 인자: text — 읽을 문장
     */
    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "sunstone-${ids.incrementAndGet()}")
    }
}
