package com.westone.sunstone.car

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pleos SDK는 결과를 onComplete·onFailed 콜백으로 준다. 루틴은 suspend로 기다리고 싶어서 감싼다.
 * 응답이 없으면 [timeoutMs] 뒤에 예외로 끝낸다(에뮬레이터에서 콜백이 안 오는 경우가 있다).
 */
internal suspend fun <T> awaitCallback(
    timeoutMs: Long = 3_000,
    block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
): T = withTimeout(timeoutMs) {
    suspendCancellableCoroutine { cont ->
        block(
            { value -> if (cont.isActive) cont.resume(value) },
            { e -> if (cont.isActive) cont.resumeWithException(e) },
        )
    }
}
