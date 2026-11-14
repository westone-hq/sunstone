package com.westone.sunstone.connect

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.Locale

/**
 * 작성: 2026-11-14 19:30:00 · 박건준
 * 클래스: PleosEar
 * 목적: 운전자 말을 Pleos 음성 인식(SpeechToText, ON_DEVICE)으로 듣는다. 듣는 중
 *       줄이 떠 있는 동안 `listen(true)`로 켜고, 끝난 말 한마디마다 `onHeard`로
 *       넘긴다. 한마디를 넘긴 뒤에도 켜져 있으면 다시 듣는다.
 *       - 마이크 권한이 없거나 초기화·인식이 실패하면 아무것도 하지 않는다
 *         (카드 버튼으로 대답한다)
 *       - 화면 스레드에서만 부른다
 * 인자: context — 앱 Context
 *       onHeard — 끝난 말 한마디를 받는 곳(`AppGraph.hear()`)
 * 근거: 인용 — 공모전 FAQ 사용 가능 SDK에 SpeechToText.
 *       측정 — 에뮬레이터에서 ON_DEVICE가 16kHz로 듣고 중간·최종 결과를 준다.
 *       에뮬레이터를 `-allow-host-audio`로 켜야 마이크 소리가 들어온다
 *       (docs/api-spike.md Pleos 음성 SDK 표)
 * 미구현: 프로토타입이라 인식 어휘를 보강하지 못했다("세차할게"를 "시작할게"로
 *       들은 적이 있다). 잘못 들으면 "잘 못 알아들었어요"가 되고 카드 버튼을
 *       쓴다. 시연 모드에서는 운전자 녹음본이 대신하므로 켜지 않는다
 */
class PleosEar(private val context: Context, private val onHeard: (String) -> Unit) {

    private companion object {
        /**
         * 작성: 2026-11-14 19:30:00 · 박건준
         * 변수: TAG
         * 목적: 음성 logcat 태그.
         * 근거: 표준 — 주석·코드 규칙 10-2 로그 태그 넷
         */
        const val TAG = "SunstoneVoice"
    }

    /** 인식기. 처음 들을 때 만든다. 만들지 못했으면 null */
    private var stt: SpeechToText? = null

    /** 초기화가 끝나 들을 수 있으면 true */
    private var ready = false

    /** 듣기를 원하는 상태면 true(듣는 중 줄이 떠 있고 앱이 말하지 않는 동안) */
    private var wanted = false

    /** 지금 인식을 요청해 둔 상태면 true */
    private var requested = false

    /**
     * 작성: 2026-11-14 19:30:00 · 박건준
     * 함수: listen
     * 목적: 듣기를 켜거나 끈다. 켤 때 인식기가 없으면 만들고, 준비되면 듣기를
     *       요청한다. 끌 때는 요청해 둔 인식을 멈춘다.
     * 인자: on — 들으려면 true
     */
    fun listen(on: Boolean) {
        wanted = on
        if (!on) {
            if (requested) runCatching { stt?.stop() }.onFailure { Log.w(TAG, "듣기 멈춤 실패: ${it.message}") }
            requested = false
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "듣기: 마이크 권한 없음")
            return
        }
        if (stt == null) return start() // → 로직 이동: PleosEar.start()
        request() // → 로직 이동: PleosEar.request()
    }

    /**
     * 작성: 2026-11-14 19:30:00 · 박건준
     * 함수: start
     * 목적: 인식기를 만들고 초기화한다. 성공하면 결과 받는 곳을 붙이고 한국어로
     *       맞춘 뒤 듣기를 요청한다. 리스너는 초기화 뒤에 붙인다.
     * 근거: 측정 — 리스너를 초기화 앞에 붙이면 예외가 나고, `Locale.KOREA`가
     *       아니면 엔진 오류가 난다(docs/api-spike.md Pleos 음성 SDK 표)
     */
    private fun start() {
        val made = runCatching { SpeechToText(context, Mode.ON_DEVICE) }
            .onFailure { Log.w(TAG, "듣기: 인식기를 만들지 못함: ${it.message}") }
            .getOrNull() ?: return // 만든 인식기
        stt = made
        runCatching {
            made.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching {
                        made.addListener(results)
                        made.setLocale(Locale.KOREA)
                    }.onFailure { Log.w(TAG, "듣기: 준비 실패: ${it.message}") }.onSuccess {
                        ready = true
                        Log.i(TAG, "듣기: 준비됨")
                        request()
                    }
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "듣기: 초기화 실패 $error")
                }
            })
        }.onFailure { Log.w(TAG, "듣기: 초기화 예외: ${it.message}") }
    }

    /**
     * 작성: 2026-11-14 19:30:00 · 박건준
     * 함수: request
     * 목적: 듣기를 원하고, 준비됐고, 아직 요청하지 않았으면 인식을 요청한다.
     */
    private fun request() {
        if (!wanted || !ready || requested) return
        runCatching { stt?.request() }
            .onSuccess { requested = true }
            .onFailure { Log.w(TAG, "듣기 요청 실패: ${it.message}") }
    }

    /** 인식 결과 받는 곳. 끝난 말만 넘기고, 인식이 끝나면 아직 원할 때 다시 요청한다 */
    private val results = object : ResultListener {
        override fun onUpdated(text: String, final: Boolean) {
            if (!final || text.isBlank()) return
            Log.i(TAG, "들음: $text")
            if (wanted) onHeard(text.trim())
        }

        override fun onEndedRecognition() {
            requested = false
            request()
        }

        override fun onError(error: ErrorType) {
            Log.w(TAG, "듣기 오류: $error")
            requested = false
        }

        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }
}
