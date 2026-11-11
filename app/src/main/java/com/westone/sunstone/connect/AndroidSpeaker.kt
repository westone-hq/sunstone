package com.westone.sunstone.connect

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.westone.sunstone.routines.port.Speaker
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-11 17:40:00 · 박희정
 * 클래스: AndroidSpeaker
 * 목적: 음성 안내. 문장마다 미리 녹음한 파일(assets/voice/<문장 MD5>.wav)이
 *       있으면 그 파일을 재생하고, 없으면 안드로이드 기본 TTS(글자를 소리로
 *       읽는 기능)로 한국어를 읽는다. 녹음본은 차례로 이어 재생하고, TTS가
 *       준비되기 전에 온 문장은 모았다가 준비되면 읽는다.
 * 인자: context — 앱 Context
 * 근거: 인용 — Pleos Playground에서 Pleos TTS는 "준비 중"이라 기본 TTS를
 *       쓴다. Tab 1 5.2 음성 출력 "대사집 문장은 미리 녹음한 파일".
 *       측정 — Pleos 에뮬레이터 v2.1.0의 TTS 엔진은 Pleos 음성 서비스 하나이고
 *       초기화가 -1로 실패해 소리가 나지 않았다(2026-11-11)
 * 미구현: 프로토타입이라 Pleos TTS 대신 안드로이드 기본 TTS로 읽는다. Pleos TTS가
 *       열리면 `Speaker` 구현만 바꾼다. 녹음본은 tools/make-voice.ps1이 Windows
 *       한국어 음성으로 만든 것이라 대사집 밖 문장은 에뮬레이터에서 소리가 없다
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

        /**
         * 작성: 2026-11-11 17:40:00 · 박희정
         * 변수: VOICE_DIR
         * 목적: 녹음본이 있는 assets 폴더 이름. tools/make-voice.ps1이 채운다.
         */
        const val VOICE_DIR = "voice"
    }

    /** 앱 assets. 녹음본을 연다 */
    private val assets = context.applicationContext.assets

    /** 있는 녹음본 파일 이름들(<문장 MD5>.wav). 앱이 켜질 때 한 번 읽는다 */
    private val recorded: Set<String> = runCatching { assets.list(VOICE_DIR)?.toSet() }.getOrNull().orEmpty()

    /** 재생을 기다리는 녹음본 파일 이름들. 화면 스레드에서만 만진다 */
    private val queue = ArrayDeque<String>()

    /** 지금 재생 중인 녹음본. 쉬고 있으면 null */
    private var player: MediaPlayer? = null

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
     * 수정: 2026-11-11 17:40:00 · 박희정
     * 함수: say
     * 목적: 문장 하나를 읽는다. 녹음본이 있으면 녹음본을, 없으면 TTS로 읽는다.
     *       앞 문장이 끝난 뒤 이어서 읽는다. 화면 스레드에서 부른다.
     * 인자: text — 읽을 문장
     */
    override fun say(text: String) {
        Log.i(TAG, "말: $text")
        val file = "${md5(text)}.wav" // 이 문장의 녹음본 이름
        when {
            file in recorded -> {
                queue.addLast(file)
                if (player == null) playNext()
            }
            ready -> speak(text)
            else -> pending += text
        }
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-11 17:40:00 · 박희정
     * 함수: close
     * 목적: 녹음본 재생과 TTS를 놓는다.
     */
    override fun close() {
        player?.release()
        player = null
        tts.shutdown()
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: playNext
     * 목적: 기다리는 녹음본을 하나 꺼내 재생한다. 끝나면 다음 것을 재생하고,
     *       없으면 쉰다. 파일을 열지 못하면 그 파일은 건너뛴다.
     */
    private fun playNext() {
        player?.release()
        player = null
        val file = queue.removeFirstOrNull() ?: return // 재생할 녹음본, 없으면 쉰다
        player = runCatching {
            assets.openFd("$VOICE_DIR/$file").use { fd ->
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    setOnCompletionListener { playNext() }
                    prepare()
                    start()
                }
            }
        }.onFailure { Log.w(TAG, "녹음본 재생 실패: $file ${it.message}") }.getOrNull()
        if (player == null) playNext()
    }

    /**
     * 작성: 2026-11-11 17:40:00 · 박희정
     * 함수: md5
     * 목적: 문장 UTF-8의 MD5를 소문자 16진수로 만든다. 녹음본 파일 이름이다.
     * 인자: text — 문장
     * 반환: 32글자 16진수
     * 근거: 표준 — tools/make-voice.ps1의 `Get-Md5`와 같은 방식이어야 파일을
     *       찾는다
     */
    private fun md5(text: String): String = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

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
