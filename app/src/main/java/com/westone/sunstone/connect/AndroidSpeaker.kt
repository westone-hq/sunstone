package com.westone.sunstone.connect

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.westone.sunstone.routines.port.Speaker
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 말하기. 지금은 안드로이드 기본 TTS를 쓴다.
 * Pleos TTS는 Playground에서 "준비 중"이라, 시연용 음성은 나중에 미리 녹음한 파일로 바꿀 수 있게
 * [Speaker] 인터페이스 뒤에 숨겨 둔다.
 */
class AndroidSpeaker(context: Context) : Speaker, AutoCloseable {
    private var ready = false
    private val pending = mutableListOf<String>()
    private val ids = AtomicInteger()

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

    private fun setKorean() {
        tts.language = Locale.KOREAN
    }

    override fun say(text: String) {
        Log.i(TAG, "말: $text")
        if (ready) speak(text) else pending += text
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "sunstone-${ids.incrementAndGet()}")
    }

    override fun close() = tts.shutdown()

    private companion object {
        const val TAG = "SunstoneVoice"
    }
}
