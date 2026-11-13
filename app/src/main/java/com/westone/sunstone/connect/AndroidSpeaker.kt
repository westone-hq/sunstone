package com.westone.sunstone.connect

import android.content.Context
import android.util.Log
import com.westone.sunstone.routines.port.Speaker
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: AndroidSpeaker
 * 목적: 음성 안내(`Speaker`, 기능 구현이 말을 내보내는 창구). 문장마다 녹음본이
 *       있으면 녹음본으로, 없으면 TTS로 읽게 해 한 줄로 세운다.
 *       - 녹음본 찾기 — `VoiceLibrary`
 *       - 순서대로 내기·끊기 — `VoicePlayer`
 * 인자: context — 앱 Context
 * 근거: 인용 — Pleos Playground에서 Pleos TTS는 "준비 중"이다. 음성 설계: 음성
 *       출력 "대사집 문장은 미리 녹음한 파일".
 *       측정 — Pleos 에뮬레이터 v2.1.0의 TTS 엔진은 Pleos 음성 서비스 하나이고
 *       초기화가 -1로 실패해 소리가 나지 않았다(2026-11-11)
 * 미구현: 프로토타입이라 Pleos TTS 대신 녹음본과 안드로이드 기본 TTS를 쓴다.
 *       Pleos TTS가 열리면 `VoicePlayer`의 TTS만 바꾼다
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

    /** 문장 → 녹음본 경로 */
    private val library = VoiceLibrary(context)

    /** 말을 순서대로 내는 줄 */
    private val player = VoicePlayer(context)

    /** 말하는 중이거나 기다리는 말이 있으면 true(값이 바뀌면 알려주는 상태) */
    val speaking: StateFlow<Boolean> get() = player.speaking

    /** 말 하나가 시작될 때마다 그 말의 꼬리표(`VoicePlayer.starts`) */
    val starts: SharedFlow<Any?> get() = player.starts

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 20:06:00 · 박건준
     * 함수: say
     * 목적: 문장 하나를 꼬리표 없이 줄 끝에 세운다. 앞 말이 끝난 뒤 나간다.
     *       화면 스레드에서 부른다.
     * 인자: text — 읽을 문장
     */
    override fun say(text: String) = say(text, null)

    /**
     * 작성: 2026-11-12 20:06:00 · 박건준
     * 함수: say
     * 목적: 문장 하나를 꼬리표와 함께 줄 끝에 세운다. 꼬리표는 그 말이 시작될 때
     *       `starts`로 나간다.
     * 인자: text — 읽을 문장
     *       tag — 말과 함께 보일 것(그때 떠 있던 질문). 없으면 null
     */
    fun say(text: String, tag: Any?) {
        Log.i(TAG, "말: $text")
        player.enqueue(VoicePlayer.Line(text, library.find(text), tag)) // → 로직 이동: VoicePlayer.enqueue()
    }

    /**
     * 작성: 2026-11-12 14:58:00 · 박건준
     * 함수: stop
     * 목적: 지금 말을 끊고 기다리는 말을 버린다. 시연을 처음으로 돌리거나 장면을
     *       건너뛸 때 앞 말이 뒤늦게 나오지 않게 한다.
     */
    fun stop() = player.stop() // → 로직 이동: VoicePlayer.stop()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 14:58:00 · 박건준
     * 함수: close
     * 목적: 재생과 TTS를 놓는다.
     */
    override fun close() = player.close() // → 로직 이동: VoicePlayer.close()
}
