package com.westone.sunstone.demo

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 * 작성: 2026-11-13 03:50:00 · 박건준
 * 수정: 2026-11-13 21:00:00 · 박건준
 * 클래스: DriverVoice
 * 목적: 시연 장면의 운전자 대사 녹음본(앱과 다른 목소리)을 낸다. 녹음본은
 *       assets/driver/<문장 MD5>.wav이고 tools/make-voice.ps1이
 *       대사집 `Utterance.text`로 만든다. 앱 안내 말(`VoicePlayer`)과 따로
 *       재생해 앱 말 기록(logcat "말:")에 섞이지 않는다.
 * 인자: context — assets를 읽는다
 * 미구현: 프로토타입이라 운전자 목소리는 녹음본으로 내고, 그 말은 음성 인식
 *       대신 시연이 `AppGraph.hear()`로 넣는다. 에뮬레이터에 마이크 입력이 없다
 *       (docs/api-spike.md 음성 인식 행)
 */
internal class DriverVoice(private val context: Context) {

    private companion object {
        /**
         * 작성: 2026-11-13 03:50:00 · 박건준
         * 변수: DIR
         * 목적: 운전자 녹음본 assets 폴더.
         */
        const val DIR = "driver"

        /**
         * 작성: 2026-11-13 03:50:00 · 박건준
         * 변수: TAG
         * 목적: 데모 logcat 태그.
         */
        const val TAG = "SunstoneDemo"
    }

    /**
     * 작성: 2026-11-13 03:50:00 · 박건준
     * 함수: play
     * 목적: 대사 녹음본을 끝까지 낸다. 녹음본이 없거나 재생하지 못하면 바로
     *       돌아온다.
     * 인자: text — 운전자 대사
     * 반환: 끝까지 냈으면 true
     */
    suspend fun play(text: String): Boolean {
        val fd = runCatching { context.assets.openFd("$DIR/${md5(text)}.wav") }.getOrNull() // 녹음본
        if (fd == null) {
            Log.w(TAG, "운전자 녹음본 없음: $text")
            return false
        }
        val player = MediaPlayer() // 재생기
        return try {
            fd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.prepare()
            suspendCancellableCoroutine { done ->
                player.setOnCompletionListener { if (done.isActive) done.resume(true) }
                player.setOnErrorListener { _, _, _ -> if (done.isActive) done.resume(false); true }
                done.invokeOnCancellation { runCatching { player.stop() } }
                player.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "운전자 녹음본 재생 실패: ${e.message}")
            false
        } finally {
            player.release()
        }
    }

    /**
     * 작성: 2026-11-13 03:50:00 · 박건준
     * 함수: md5
     * 목적: 문장 UTF-8의 MD5를 소문자 16진수로 만든다. 녹음본 파일 이름이다
     *       (`VoiceLibrary`와 같은 방식).
     * 인자: text — 문장
     * 반환: 32자 16진수
     */
    private fun md5(text: String): String = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
