package com.westone.sunstone.connect

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 작성: 2026-11-12 14:55:00 · 박건준
 * 수정: 2026-11-13 21:00:00 · 박희정
 * 클래스: VoicePlayer
 * 목적: 말을 한 줄로 세워 들어온 순서대로 하나씩 낸다. 말은 Pleos TTS(차 안에서
 *       글자를 소리로 읽는 기능)로 읽고, TTS를 쓸 수 없을 때만 녹음본을
 *       재생한다. 앞 말이 끝나야 다음 말을 낸다.
 *       - `enqueue()` — 줄 끝에 말을 더한다
 *       - `stop()` — 지금 말을 끊고 줄을 비운다(시연을 처음으로 돌릴 때)
 *       - `speaking` — 말하는 중인지(시연이 말이 끝나길 기다릴 때)
 *       - `starts` — 말 하나가 시작될 때 그 말의 꼬리표(화면이 패널을 그
 *         말과 함께 띄울 때)
 * 인자: context — 앱 Context. 녹음본과 TTS에 쓴다
 * 근거: 측정 — Pleos TextToSpeech 3.3.1 ON_DEVICE가 에뮬레이터에서 한국어
 *       (`seoyoon`)로 읽음(docs/api-spike.md Pleos 음성 SDK 표, 2026-11-13).
 *       SERVER는 자격 정보가 없어 실패한다
 * 미구현: 프로토타입이라 TTS가 늦게 준비되거나 실패하면 대사집 녹음본으로
 *       대신한다(시연이 끊기지 않게). 녹음본도 없는 말은 소리 없이 건너뛴다
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

        /**
         * 작성: 2026-11-13 21:00:00 · 박희정
         * 변수: READY_WAIT_MS
         * 목적: TTS 준비를 기다리는 가장 긴 시간(밀리초). 넘으면 준비될 때까지
         *       녹음본으로 낸다.
         * 근거: 측정 — 에뮬레이터 부하가 클 때 엔진 준비가 25초 넘게 걸렸다
         *       (2026-11-13). 첫 장면 말이 그만큼 늦으면 시연이 멈춘 것처럼 보인다
         */
        const val READY_WAIT_MS = 8_000L

        /**
         * 작성: 2026-11-13 21:00:00 · 박희정
         * 변수: GUARD_BASE_MS, GUARD_PER_CHAR_MS
         * 목적: TTS 말 하나가 끝났다는 알림이 오지 않을 때 다음 말로 넘어가는
         *       시간. 기본 시간 + 글자당 시간.
         * 식: 기본 6초 + 글자 수 × 0.25초
         * 근거: 측정 — 31자 문장이 약 3.6초(2026-11-13). 글자당 0.12초의 두 배에
         *       엔진 준비 시간을 더해 넉넉히 잡았다
         */
        const val GUARD_BASE_MS = 6_000L
        const val GUARD_PER_CHAR_MS = 250L
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 수정: 2026-11-13 21:00:00 · 박희정
     * 클래스: Line
     * 목적: 줄에 선 말 하나.
     * 인자: text — 말(로그와 TTS에 쓴다)
     *       asset — 녹음본 assets 경로. TTS를 쓸 수 없을 때 대신 낸다. 없으면 null
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

    /** 지금 TTS로 읽는 말의 번호. 늦게 온 알림을 거르는 데 쓴다. TTS로 읽지 않을 땐 0 */
    private var speakingId = 0

    /** TTS 읽기마다 붙이는 번호 */
    private var seq = 0

    /** TTS 상태. null이면 아직 준비 중, true면 쓸 수 있음, false면 쓸 수 없음 */
    private var ttsReady: Boolean? = null

    /** TTS 준비를 그만 기다리고 녹음본으로 내기 시작했는지 */
    private var gaveUpWaiting = false

    /** 말하는 중인지 바꾸는 곳 */
    private val busy = MutableStateFlow(false)

    /** 말하는 중이거나 기다리는 말이 있으면 true(값이 바뀌면 알려주는 상태) */
    val speaking: StateFlow<Boolean> = busy

    /** 시작한 말의 꼬리표를 내보내는 곳. 늦게 받는 쪽도 최근 몇 개는 받는다 */
    private val started = MutableSharedFlow<Any?>(replay = 4)

    /** 말 하나가 시작될 때마다 그 말의 꼬리표(한 번씩 흘러가는 값). 건너뛴 말도 낸다 */
    val starts: SharedFlow<Any?> = started

    /** Pleos TTS(차 안 엔진). 준비가 끝나면 한국어로 맞추고 기다리던 줄을 다시 돌린다 */
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE).also {
        it.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post { ready(true, null) }
            }

            override fun onFailure(error: ErrorType) {
                main.post { ready(false, error) }
            }
        })
    }

    /** TTS 준비를 그만 기다리게 하는 일. 정해진 시간 안에 준비되면 할 일이 없다 */
    private val giveUp = Runnable {
        if (ttsReady != null) return@Runnable
        Log.w(TAG, "TTS 준비가 늦어 녹음본으로 냄")
        gaveUpWaiting = true
        if (current == null) playNext()
    }

    /** 끝 알림이 오지 않은 TTS 말을 끝난 것으로 보는 일 */
    private val guard = Runnable {
        Log.w(TAG, "TTS 끝 알림 없음, 다음 말로: ${current?.text}")
        playNext()
    }

    init {
        main.postDelayed(giveUp, READY_WAIT_MS)
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
     * 수정: 2026-11-13 21:00:00 · 박희정
     * 함수: stop
     * 목적: 지금 말을 끊고 기다리는 말을 모두 버린다. 화면 스레드에서 부른다.
     */
    fun stop() {
        queue.clear()
        main.removeCallbacks(guard)
        player?.release()
        player = null
        if (speakingId != 0) runCatching { tts.stop() }
        speakingId = 0
        current = null
        busy.value = false
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 수정: 2026-11-13 21:00:00 · 박희정
     * 함수: close
     * 목적: 재생을 멈추고 TTS를 놓는다.
     */
    override fun close() {
        stop()
        main.removeCallbacks(giveUp)
        runCatching { tts.release() }
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 수정: 2026-11-13 21:00:00 · 박희정
     * 함수: playNext
     * 목적: 줄 맨 앞 말을 낸다. TTS로 읽고, TTS를 쓸 수 없으면 녹음본을
     *       재생한다. 내기 전에 그 말의 꼬리표를 `starts`로 알린다.
     *       - TTS가 아직 준비 중이면 그 말에서 멈춰 기다린다(준비되거나
     *         `READY_WAIT_MS`가 지나면 다시 부른다)
     *       - 둘 다 못 하면 그 말은 건너뛴다
     *       - 줄이 비면 쉰다
     */
    private fun playNext() {
        main.removeCallbacks(guard)
        player?.release()
        player = null
        speakingId = 0
        current = null
        val next = queue.firstOrNull() // 낼 말, 줄이 비었으면 null
        if (next == null) {
            busy.value = false
            return
        }
        if (ttsReady == null && !gaveUpWaiting) return
        queue.removeFirst()
        current = next
        started.tryEmit(next.tag)
        val ok = speak(next) || (next.asset != null && playRecorded(next.asset)) // 시작했는지
        if (!ok) {
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
     * 수정: 2026-11-13 21:00:00 · 박희정
     * 함수: speak
     * 목적: Pleos TTS로 말 하나를 읽는다. 끝 알림(`EventListener.onDone`)이
     *       오면 다음 말로 간다. 알림이 오지 않아도 글자 수만큼 기다린 뒤
     *       넘어간다.
     * 인자: line — 읽을 말
     * 반환: 읽기를 시작했으면 true. TTS를 쓸 수 없으면 false
     */
    private fun speak(line: Line): Boolean {
        if (ttsReady != true) return false
        val id = ++seq // 이 읽기의 번호
        val ok = runCatching { tts.speak(line.text, true) } // isFinal: 한 번에 다 보낸다
            .onFailure { Log.w(TAG, "TTS 읽기 실패: ${it.message}") }.isSuccess
        if (!ok) return false
        speakingId = id
        Log.i(TAG, "TTS: ${line.text}")
        main.postDelayed(guard, GUARD_BASE_MS + GUARD_PER_CHAR_MS * line.text.length)
        return true
    }

    /**
     * 작성: 2026-11-13 21:00:00 · 박희정
     * 함수: ready
     * 목적: TTS 초기화 결과를 받는다. 되면 한국어로 맞추고 끝 알림을 붙인다.
     *       기다리던 말이 있으면 낸다. 화면 스레드에서 부른다.
     * 인자: ok — 초기화가 됐는지
     *       error — 실패 이유. 됐으면 null
     * 근거: 측정 — `Locale.KOREAN`(ko)은 엔진이 `unsupported locale`로 거절하고
     *       ko-KR만 받는다. 리스너는 초기화 뒤에 붙여야 한다(앞이면 예외)
     */
    private fun ready(ok: Boolean, error: ErrorType?) {
        main.removeCallbacks(giveUp)
        ttsReady = ok && runCatching {
            tts.setLocale(Locale.KOREA)
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.addEventListener(object : EventListener {
                override fun onStart() = Unit
                override fun onReady() = Unit
                override fun onDone() = finished(false)
                override fun onError(error: ErrorType) {
                    Log.w(TAG, "TTS 오류: $error")
                    finished(true)
                }
                override fun onStop() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
            })
        }.onFailure { Log.w(TAG, "TTS 설정 실패: ${it.message}") }.isSuccess
        Log.i(TAG, if (ttsReady == true) "TTS 준비됨(Pleos ON_DEVICE)" else "TTS 초기화 실패: $error")
        if (current == null) playNext()
    }

    /**
     * 작성: 2026-11-12 14:55:00 · 박건준
     * 수정: 2026-11-13 21:00:00 · 박희정
     * 함수: finished
     * 목적: TTS 읽기 하나가 끝났을 때(다른 스레드). 지금 읽는 말이면 화면
     *       스레드에서 다음 말로 간다. 실패였고 녹음본이 있으면 녹음본을 낸다.
     *       끊은 뒤 늦게 온 알림은 읽는 말이 없어 무시한다.
     * 인자: failed — 읽기가 실패했는지
     */
    private fun finished(failed: Boolean) {
        main.post {
            if (speakingId == 0) return@post
            val line = current // 끝난 말
            if (failed && line?.asset != null) {
                main.removeCallbacks(guard)
                speakingId = 0
                if (playRecorded(line.asset)) return@post
            }
            playNext()
        }
    }
}
