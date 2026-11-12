package com.westone.sunstone.connect

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 작성: 2026-11-12 14:55:00 · 박건준
 * 수정: 2026-11-12 20:06:00 · 박건준
 * 클래스: VoicePlayer
 * 목적: 말을 한 줄로 세워 들어온 순서대로 하나씩 낸다. 녹음본과 TTS(글자를
 *       소리로 읽는 기능)를 섞어도 순서가 바뀌지 않는다. 앞 말이 끝나야 다음
 *       말을 낸다.
 *       - `enqueue()` — 줄 끝에 말을 더한다
 *       - `stop()` — 지금 말을 끊고 줄을 비운다(시연을 처음으로 돌릴 때)
 *       - `speaking` — 말하는 중인지(시연이 말이 끝나길 기다릴 때)
 *       - `starts` — 말 하나가 시작될 때 그 말의 꼬리표(화면이 패널을 그
 *         말과 함께 띄울 때)
 * 인자: context — 앱 Context. 녹음본과 TTS에 쓴다
 * 근거: 측정 — 녹음본만 줄을 서고 TTS는 바로 읽던 때는 녹음본이 없는 문장이
 *       앞 문장보다 먼저 나갈 수 있었고, 시연을 처음으로 돌려도 남은 녹음본이
 *       계속 재생됐다(2026-11-12 app/wash)
 * 미구현: 프로토타입이라 Pleos TTS 대신 안드로이드 기본 TTS를 쓴다. 에뮬레이터는
 *       TTS 초기화가 실패해서(-1) 녹음본이 없는 말은 소리 없이 건너뛴다
 */
class VoicePlayer(context: Context) : AutoCloseable {

    private companion object {
        /**
         * 작성: 2026-11-12 14:55:00 · 박건준
         * 변수: TAG
         * 목적: 음성 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneVoice"
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 수정: 2026-11-12 20:06:00 · 박건준
     * 클래스: Line
     * 목적: 줄에 선 말 하나.
     * 인자: text — 말(로그와 TTS에 쓴다)
     *       asset — 녹음본 assets 경로. null이면 TTS로 읽는다
     *       tag — 말과 함께 보일 것(그때 떠 있던 질문). 말이 시작되면
     *       `starts`로 내보낸다. 없으면 null
     */
    class Line(val text: String, val asset: String?, val tag: Any? = null)

    /** 앱 assets. 녹음본을 연다 */
    private val assets = context.applicationContext.assets

    /** 화면 스레드. 재생이 끝났다는 알림을 여기로 옮겨 줄을 한 스레드에서만 만진다 */
    private val main = Handler(Looper.getMainLooper())

    /** 기다리는 말. 화면 스레드에서만 만진다 */
    private val queue = ArrayDeque<Line>()

    /** 지금 재생 중인 녹음본. 녹음본을 재생하지 않을 땐 null */
    private var player: MediaPlayer? = null

    /** 지금 내고 있는 말. 쉬고 있으면 null */
    private var current: Line? = null

    /** 지금 TTS로 읽는 말의 번호. 늦게 온 끝남 알림을 거르는 데 쓴다. TTS로 읽지 않을 땐 null */
    private var speakingId: String? = null

    /** TTS 읽기마다 붙이는 번호 */
    private var seq = 0

    /** TTS 상태. null이면 아직 준비 중, true면 쓸 수 있음, false면 쓸 수 없음 */
    private var ttsReady: Boolean? = null

    /** 말하는 중인지 바꾸는 곳 */
    private val busy = MutableStateFlow(false)

    /** 말하는 중이거나 기다리는 말이 있으면 true(값이 바뀌면 알려주는 상태) */
    val speaking: StateFlow<Boolean> = busy

    /** 시작한 말의 꼬리표를 내보내는 곳. 늦게 받는 쪽도 최근 몇 개는 받는다 */
    private val started = MutableSharedFlow<Any?>(replay = 4)

    /** 말 하나가 시작될 때마다 그 말의 꼬리표(한 번씩 흘러가는 값). 건너뛴 말도 낸다 */
    val starts: SharedFlow<Any?> = started

    /** 기본 TTS. 준비가 끝나면 한국어로 맞추고, 기다리던 줄을 다시 돌린다 */
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady == true) setUpTts() else Log.w(TAG, "TTS 초기화 실패: $status")
            if (current == null) playNext()
        }
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: enqueue
     * 목적: 줄 끝에 말을 더한다. 쉬고 있었으면 바로 낸다. 화면 스레드에서 부른다.
     * 인자: line — 낼 말
     */
    fun enqueue(line: Line) {
        queue.addLast(line)
        busy.value = true
        if (current == null) playNext()
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: stop
     * 목적: 지금 말을 끊고 기다리는 말을 모두 버린다. 화면 스레드에서 부른다.
     */
    fun stop() {
        queue.clear()
        player?.release()
        player = null
        speakingId = null
        if (ttsReady == true) tts.stop()
        current = null
        busy.value = false
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: close
     * 목적: 재생을 멈추고 TTS를 놓는다.
     */
    override fun close() {
        stop()
        tts.shutdown()
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 수정: 2026-11-12 20:06:00 · 박건준
     * 함수: playNext
     * 목적: 줄 맨 앞 말을 낸다. 녹음본은 재생하고, 아니면 TTS로 읽는다. 내기
     *       전에 그 말의 꼬리표를 `starts`로 알린다.
     *       - TTS가 아직 준비 중이면 그 말에서 멈춰 기다린다(준비되면 다시 부른다)
     *       - TTS를 쓸 수 없거나 녹음본을 열지 못하면 그 말은 건너뛴다
     *       - 줄이 비면 쉰다
     */
    private fun playNext() {
        player?.release()
        player = null
        speakingId = null
        current = null
        val next = queue.firstOrNull() // 낼 말, 줄이 비었으면 null
        if (next == null) {
            busy.value = false
            return
        }
        if (next.asset == null && ttsReady == null) return
        queue.removeFirst()
        current = next
        started.tryEmit(next.tag)
        val started = if (next.asset != null) playRecorded(next.asset) else speak(next) // 시작했는지
        if (!started) {
            Log.w(TAG, "소리 없이 건너뜀(${if (next.asset == null) "녹음본 없음" else "재생 실패"}): ${next.text}")
            playNext()
        }
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: playRecorded
     * 목적: 녹음본 하나를 음성 안내 소리로 재생한다. 끝나면 다음 말로 간다.
     * 인자: asset — assets 경로
     * 반환: 재생을 시작했으면 true
     */
    private fun playRecorded(asset: String): Boolean {
        player = runCatching {
            assets.openFd(asset).use { fd ->
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
        }.onFailure { Log.w(TAG, "녹음본 재생 실패: $asset ${it.message}") }.getOrNull()
        return player != null
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: speak
     * 목적: TTS로 말 하나를 읽는다. 끝나면(`UtteranceProgressListener`) 다음
     *       말로 간다.
     * 인자: line — 읽을 말
     * 반환: 읽기를 시작했으면 true. TTS를 쓸 수 없으면 false
     */
    private fun speak(line: Line): Boolean {
        if (ttsReady != true) return false
        val id = "sunstone-${++seq}" // 이 읽기의 번호
        speakingId = id
        return tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: finished
     * 목적: TTS 읽기 하나가 끝났을 때(다른 스레드). 지금 읽는 말이면 화면
     *       스레드에서 다음 말로 간다.
     * 인자: utteranceId — 끝난 읽기의 번호
     */
    private fun finished(utteranceId: String?) {
        main.post { if (utteranceId != null && utteranceId == speakingId) playNext() }
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 함수: setUpTts
     * 목적: TTS를 한국어로 맞추고, 읽기가 끝나거나 실패하면 다음 말로 가게 한다.
     *       끊은 뒤 늦게 온 알림은 번호가 달라 무시한다.
     */
    private fun setUpTts() {
        tts.language = Locale.KOREAN
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            override fun onError(utteranceId: String?) = finished(utteranceId)
        })
    }
}
