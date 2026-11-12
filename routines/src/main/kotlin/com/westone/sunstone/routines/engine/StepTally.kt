package com.westone.sunstone.routines.engine

import com.westone.sunstone.routines.port.ControlResult

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 클래스: StepTally
 * 목적: 루틴 단계 결과를 대상별로 모은 것. 끝에 한 번에 말하는 루틴이 "했어요"
 *       쪽과 "직접 해 주세요" 쪽을 나눌 때 쓴다(`tally()`).
 * 인자: done — 앱이 한 대상, 실행한 순서대로
 *       manual — 사람이 해야 하는 대상, 실행한 순서대로
 *       extra — 대상 없이 따로 말할 안내 문장, 실행한 순서대로
 */
data class StepTally<P>(val done: List<P>, val manual: List<P>, val extra: List<String>)

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 함수: tally
 * 목적: 단계 결과를 대상마다 모아 나눈다. 세차 모드에서 옮겨 와 차일드락과
 *       함께 쓴다.
 *       1. 같은 대상 단계(창문 네 개)를 하나로 묶는다
 *       2. 묶은 단계가 모두 됐으면 `done`, 하나라도 안 됐으면 `manual`
 *       3. 대상이 없는 안내 단계는 그 말을 `extra`에 넣는다
 * 인자: outcomes — 단계마다의 결과, 실행한 순서대로
 *       partOf — 단계의 대상. 대상 없이 따로 말할 단계면 null
 *       quietIfMissing — true인 대상은 차에 없을 때(`Unsupported`) 말하지
 *       않는다(에뮬레이터에 없는 선루프)
 * 반환: 나눈 결과
 */
fun <P> tally(
    outcomes: List<StepOutcome>,
    partOf: (Step) -> P?,
    quietIfMissing: (P) -> Boolean = { false },
): StepTally<P> {
    val results = linkedMapOf<P, MutableList<ControlResult?>>() // 대상별 결과, 단계 순서대로
    val extra = mutableListOf<String>() // 따로 말할 안내
    outcomes.forEach { (step, result) ->
        val part = partOf(step) // 이 단계의 대상, 따로 말할 안내면 null
        if (part == null) {
            if (step is Step.Guide) extra += step.say
        } else {
            results.getOrPut(part) { mutableListOf() } += result
        }
    }
    val done = mutableListOf<P>() // 앱이 한 대상
    val manual = mutableListOf<P>() // 사람이 해야 하는 대상
    results.forEach { (part, list) ->
        val present = if (quietIfMissing(part)) list.filterNot { it is ControlResult.Unsupported } else list
        when {
            present.isEmpty() -> Unit
            present.all { it?.ok == true } -> done += part
            else -> manual += part
        }
    }
    return StepTally(done, manual, extra)
}
