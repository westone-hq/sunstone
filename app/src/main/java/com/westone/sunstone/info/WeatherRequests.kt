package com.westone.sunstone.info

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** 일시적인 연결 끊김·시간 초과만 한 번 재시도한다. 인증서 오류는 우회하지 않는다. */
internal object WeatherRequests {
    const val BUDGET_MS = 16_000L

    suspend fun <T> run(fetch: suspend () -> T): T {
        try {
            return fetch()
        } catch (e: IOException) {
            if (e is SSLHandshakeException || e is SSLPeerUnverifiedException) throw e
            Log.w("Sunstone", "날씨 통신 재시도 1/1: ${e.javaClass.simpleName}")
        }
        delay(400)
        return fetch()
    }
}
