package com.westone.sunstone.routines.speech

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 함수: hasFinalConsonant
 * 목적: 낱말 마지막 글자에 받침이 있는지 본다. 조사(은/는, 을/를)를 고를 때
 *       쓴다.
 * 인자: word — 낱말. 비었거나 마지막 글자가 한글 음절이 아니면 받침 없음으로 본다
 * 반환: 받침이 있으면 true
 * 식: (code − 0xAC00) mod 28 ≠ 0
 *     code — 마지막 글자의 유니코드 값, 28 — 음절마다 붙을 수 있는 받침 수(없음 포함)
 * 근거: 표준 — 유니코드 한글 음절 배열(U+AC00~U+D7A3, 초성×중성×종성 순)
 */
private fun hasFinalConsonant(word: String): Boolean {
    val last = word.lastOrNull() ?: return false // 마지막 글자
    if (last !in '가'..'힣') return false
    return (last - '가') % 28 != 0
}

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 함수: withTopic
 * 목적: 낱말에 은/는을 붙인다. "사이드미러는", "창문은".
 * 인자: word — 낱말
 * 반환: 조사를 붙인 낱말
 */
fun withTopic(word: String): String = word + if (hasFinalConsonant(word)) "은" else "는"

/**
 * 작성: 2026-11-11 14:28:46 · 박희정
 * 함수: withObject
 * 목적: 낱말에 을/를 붙인다. "선루프 뒤를", "운전석 창문을".
 * 인자: word — 낱말
 * 반환: 조사를 붙인 낱말
 */
fun withObject(word: String): String = word + if (hasFinalConsonant(word)) "을" else "를"

/**
 * 작성: 2026-11-11 17:40:00 · 박희정
 * 함수: withCopula
 * 목적: 낱말에 이에요/예요를 붙인다. "현대차 기능이에요", "세차 모드예요".
 * 인자: word — 낱말
 * 반환: 맺음을 붙인 낱말
 */
fun withCopula(word: String): String = word + if (hasFinalConsonant(word)) "이에요" else "예요"

/**
 * 작성: 2026-11-12 18:24:00 · 박건준
 * 함수: withDirection
 * 목적: 낱말에 으로/로를 붙인다. "파라다이스 호텔 부산으로", "서울로".
 *       받침이 없거나 ㄹ받침이면 "로"다.
 * 인자: word — 낱말
 * 반환: 조사를 붙인 낱말
 * 식: ㄹ받침 = (code − 0xAC00) mod 28 = 8
 * 근거: 표준 — 유니코드 한글 음절 배열에서 종성 ㄹ의 순번은 8이다
 */
fun withDirection(word: String): String {
    val last = word.lastOrNull() // 마지막 글자
    val rieul = last != null && last in '가'..'힣' && (last - '가') % 28 == 8 // ㄹ받침인지
    return word + if (hasFinalConsonant(word) && !rieul) "으로" else "로"
}
