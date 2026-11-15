package com.westone.sunstone.demo

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit

/**
 * 작성: 2026-11-13 19:50:00 · 박희정
 * 클래스: SpeechProbe
 * 목적: Pleos 음성 SDK(TextToSpeech·SpeechToText 3.3.1)가 에뮬레이터에서
 *       되는지 잰다. 안드로이드 기본 `TextToSpeech`는 초기화가 -1로 실패해서
 *       녹음본으로 대신하고 있다. Pleos SDK는 같은 음성 서비스(caas)에
 *       직접 붙는다. 실측용이라 debug 빌드에만 있다. 결과는 `speech,` 줄.
 *       1. 합성: 모드(ON_DEVICE·HYBRID·SERVER)마다 초기화하고 한 문장 말하기
 *       2. 인식: 모드마다 초기화하고 `LISTEN_MS` 동안 듣기
 * 인자: context — 앱 컨텍스트
 * 근거: 인용 — 공모전 FAQ 사용 가능 SDK(TextToSpeech, SpeechToText).
 *       측정 — docs/api-spike.md TTS 행(기본 TextToSpeech 초기화 -1)
 */
internal class SpeechProbe(private val context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-13 19:50:00 · 박희정
         * 변수: TAG, LINE, INIT_MS, SPEAK_MS, LISTEN_MS
         * 목적: logcat 태그, 말해 볼 문장, 초기화·말하기·듣기를 기다리는
         *       시간(밀리초).
         * 근거: 미정 — 한 문장을 다 말하고 짧은 말을 들을 만한 시간
         */
        const val TAG = "SunstoneVoice"
        const val LINE = "세차 모드 맞췄어요. 사이드미러는 직접 접어 주세요."
        const val INIT_MS = 5_000L
        const val SPEAK_MS = 20_000L
        const val LISTEN_MS = 15_000L
    }

    /**
     * 작성: 2026-11-13 19:50:00 · 박희정
     * 함수: run
     * 목적: 클래스 설명의 1~2단계를 한다. 한 모드가 실패해도 다음으로
     *       넘어가고, 끝나면 놓는다.
     * 인자: what — "tts"·"stt"·그 밖(둘 다). 뒤에 모드 이름("tts ON_DEVICE")을
     *       붙이면 그 모드만. "mic"이면 마이크 진폭만 잰다
     */
    suspend fun run(what: String) {
        val kind = what.substringBefore(' ') // tts·stt·mic
        val only = what.substringAfter(' ', "") // 모드 하나만. 없으면 빈 글자
        if (kind == "mic") return withContext(Dispatchers.IO) { mic() }
        if (kind != "stt") TtsMode.entries.filter { only.isEmpty() || it.name == only }
            .forEach { runCatching { tts(it) }.onFailure { e -> log("tts,$it,예외,$e") } }
        if (kind != "tts") SttMode.entries.filter { only.isEmpty() || it.name == only }
            .forEach { runCatching { stt(it) }.onFailure { e -> log("stt,$it,예외,$e") } }
        log("끝")
    }

    /**
     * 작성: 2026-11-13 19:50:00 · 박희정
     * 함수: tts
     * 목적: 한 모드로 합성을 초기화하고 `LINE`을 말해 본다. 이벤트는 모두 남긴다.
     * 인자: mode — 합성 모드
     */
    private suspend fun tts(mode: TtsMode) {
        val tts = withContext(Dispatchers.Main) { TextToSpeech(context, mode) } // 합성기
        try {
            withContext(Dispatchers.Main) {
                tts.initialize(object : OnInitializeListener {
                    override fun onSuccess() = log("tts,$mode,초기화 성공")
                    override fun onFailure(error: TtsError) = log("tts,$mode,초기화 실패,$error")
                })
            }
            delay(INIT_MS)
            withContext(Dispatchers.Main) {
                tts.addEventListener(object : EventListener {
                    override fun onStart() = log("tts,$mode,시작")
                    override fun onReady() = log("tts,$mode,준비")
                    override fun onDone() = log("tts,$mode,끝남")
                    override fun onError(error: TtsError) = log("tts,$mode,오류,$error")
                    override fun onStop() = log("tts,$mode,멈춤")
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                tts.setLocale(Locale.KOREA)
                tts.speak(LINE, true)
            }
            log("tts,$mode,말하기 요청")
            delay(SPEAK_MS)
        } finally {
            withContext(Dispatchers.Main) { runCatching { tts.stop() }; runCatching { tts.release() } }
        }
    }

    /**
     * 작성: 2026-11-13 19:50:00 · 박희정
     * 함수: stt
     * 목적: 한 모드로 인식을 초기화하고 `LISTEN_MS` 동안 들어 본다. 중간·최종
     *       결과, 말 구간, 오류와 1초마다 가장 큰 소리 크기를 남긴다(마이크
     *       소리가 들어오는지 본다).
     * 인자: mode — 인식 모드
     */
    private suspend fun stt(mode: SttMode) {
        val stt = withContext(Dispatchers.Main) { SpeechToText(context, mode) } // 인식기
        try {
            withContext(Dispatchers.Main) {
                stt.initialize(object : SttInit {
                    override fun onSuccess() = log("stt,$mode,초기화 성공")
                    override fun onFailure(error: SttError) = log("stt,$mode,초기화 실패,$error")
                })
            }
            delay(INIT_MS)
            var peak = 0.0 // 지난 1초 안 가장 큰 소리 크기
            var since = System.currentTimeMillis() // 그 1초가 시작된 때
            withContext(Dispatchers.Main) {
                stt.addListener(object : ResultListener {
                    override fun onUpdated(text: String, final: Boolean) = log("stt,$mode,들음,$final,$text")
                    override fun onUpdatedEpdData(start: Long, end: Long) = log("stt,$mode,말 구간,$start,$end")
                    override fun onStartedRecognition() = log("stt,$mode,인식 시작")
                    override fun onEndedRecognition() = log("stt,$mode,인식 끝")
                    override fun onError(error: SttError) = log("stt,$mode,오류,$error")
                    override fun onReady() = log("stt,$mode,준비")
                    override fun onUpdatedRms(rms: Double) {
                        peak = maxOf(peak, rms)
                        val now = System.currentTimeMillis() // 지금
                        if (now - since < 1_000L) return
                        log("stt,$mode,소리 크기,${"%.1f".format(peak)}")
                        peak = 0.0
                        since = now
                    }
                })
                stt.setLocale(Locale.KOREA)
                stt.request()
            }
            log("stt,$mode,듣기 요청")
            delay(LISTEN_MS)
        } finally {
            withContext(Dispatchers.Main) { runCatching { stt.stop() }; runCatching { stt.release() } }
        }
    }

    /**
     * 작성: 2026-11-13 20:40:00 · 박희정
     * 함수: mic
     * 목적: 앱이 직접 마이크를 5초 녹음해 0.5초마다 가장 큰 진폭(0~32767)을
     *       남긴다. 인식이 결과를 내지 않을 때, 호스트 마이크 소리가
     *       에뮬레이터까지 들어오는지 가린다.
     */
    @android.annotation.SuppressLint("MissingPermission") // adb pm grant로 허가한다
    private fun mic() {
        val rate = 16_000 // 인식 엔진과 같은 표본율
        val size = android.media.AudioRecord.getMinBufferSize(
            rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(rate) // 버퍼 크기(바이트)
        val record = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC, rate,
            android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, size,
        ) // 녹음기
        val buf = ShortArray(rate / 2) // 0.5초 분량
        try {
            record.startRecording()
            log("mic,녹음 시작,${record.recordingState}")
            repeat(10) {
                val n = record.read(buf, 0, buf.size) // 읽은 표본 수
                val peak = (0 until maxOf(n, 0)).maxOfOrNull { i -> kotlin.math.abs(buf[i].toInt()) } ?: -1 // 가장 큰 진폭
                log("mic,${it * 500}ms,읽음 $n,진폭 $peak")
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /**
     * 작성: 2026-11-13 19:50:00 · 박희정
     * 함수: log
     * 목적: 결과 한 줄을 `speech,`로 시작해 남긴다.
     * 인자: line — 남길 내용
     */
    private fun log(line: String) {
        Log.i(TAG, "speech,$line")
    }
}
