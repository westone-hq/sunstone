package com.westone.sunstone.car

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 작성: 2026-11-09 18:49:00 · 박건준
 * 변수: CALLBACK_TIMEOUT_MS
 * 목적: SDK 콜백을 기다리는 기본 제한 시간(밀리초).
 * 근거: 미정 — 에뮬레이터에서 정상 호출은 0.5초 안에 돌아와서 넉넉히 잡았다
 */
private const val CALLBACK_TIMEOUT_MS = 3_000L

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 함수: awaitCallback
 * 목적: 성공·실패 콜백으로 결과를 주는 Pleos SDK 호출을 비동기로 기다린다.
 *       루틴이 결과를 받은 뒤 다음 단계로 가야 해서 감싼다. 콜백이 두 번
 *       와도 처음 것만 쓴다.
 * 인자: timeoutMs — 기다리는 제한 시간(밀리초)
 *       block — 받은 두 콜백(ok, fail)을 SDK 호출에 넘기는 코드
 * 반환: ok로 받은 값. 실패 콜백이면 그 예외, 시간이 지나면
 *       `TimeoutCancellationException`
 * 근거: 측정 — 에뮬레이터에서 `selectSeatMemory`처럼 콜백이 오지 않는
 *       호출이 있어 제한 시간을 둔다(docs/api-spike.md 시트 메모리 행)
 */
internal suspend fun <T> awaitCallback(
    timeoutMs: Long = CALLBACK_TIMEOUT_MS,
    block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
): T = withTimeout(timeoutMs) {
    suspendCancellableCoroutine { cont ->
        block(
            { value -> if (cont.isActive) cont.resume(value) },
            { e -> if (cont.isActive) cont.resumeWithException(e) },
        )
    }
}
