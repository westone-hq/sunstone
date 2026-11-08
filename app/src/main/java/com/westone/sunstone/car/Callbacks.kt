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

/**
 * 문서로 서명을 확정하지 못한 API를 이름으로 부른다.
 * 컴파일은 항상 되고, 없는 API면 false를 돌려서 루틴이 "안내"로 넘어간다.
 * 에뮬레이터 실측(docs/api-spike.md)으로 서명이 확인되면 직접 호출로 바꾼다.
 */
internal object Reflective {
    fun call(target: Any, names: List<String>, vararg args: Any?): Boolean {
        val method = target.javaClass.methods.firstOrNull { it.name in names && it.parameterCount == args.size }
            ?: return false
        method.invoke(target, *args)
        return true
    }

    fun has(target: Any, names: List<String>, argCount: Int): Boolean =
        target.javaClass.methods.any { it.name in names && it.parameterCount == argCount }
}

/** 성공 콜백(인자 2개: area, value)을 받는 set 계열 호출을 suspend로 기다린다. */
internal suspend fun awaitSet(
    target: Any,
    names: List<String>,
    vararg args: Any?,
): Boolean {
    if (!Reflective.has(target, names, args.size + 2)) return false
    awaitCallback<Unit> { ok, fail ->
        val done: (Any?, Any?) -> Unit = { _, _ -> ok(Unit) }
        val failed: (Exception) -> Unit = fail
        Reflective.call(target, names, *args, done, failed)
    }
    return true
}

/** 성공 콜백(인자 1개: value)을 받는 get 계열 호출을 suspend로 기다린다. 없는 API면 null. */
internal suspend fun awaitGet(
    target: Any,
    names: List<String>,
    vararg args: Any?,
): Any? {
    if (!Reflective.has(target, names, args.size + 2)) return null
    return awaitCallback<Any?> { ok, fail ->
        val done: (Any?) -> Unit = ok
        val failed: (Exception) -> Unit = fail
        Reflective.call(target, names, *args, done, failed)
    }
}
