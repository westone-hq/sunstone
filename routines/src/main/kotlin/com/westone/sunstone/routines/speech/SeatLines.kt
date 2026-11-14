package com.westone.sunstone.routines.speech

import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis

/**
 * 작성: 2026-11-13 15:00:00 · 박희정
 * 클래스: SeatLines
 * 목적: 시트 맞추기(시나리오 ③)가 하는 말과 카드 글자를 한곳에 모은다. 문장을
 *       바꿀 때는 이 파일만 고친다.
 * 근거: 인용 — docs/SCREEN.md 4장 ③ 표. 2026-11-13 박희정 추천안(같은 날
 *       확인): ③-1에 "다 되면 '됐어'라고 해 주세요." 덧붙이기, 움직인 뒤
 *       "네." 대신 한 일 말하기
 */
object SeatLines {

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 변수: HOW
     * 목적: 시트 맞추기를 시작할 때 하는 말.
     * 근거: 인용 — docs/SCREEN.md 4장 ③-1 "어떻게 맞춰 드릴까요?"에 패널 설명을
     *       말로도 붙였다
     */
    const val HOW = "어떻게 맞춰 드릴까요? 다 되면 '됐어'라고 해 주세요."

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 변수: ADJUST_DETAIL, DONE, RESTORE
     * 목적: 맞추는 중 카드의 설명과 두 버튼.
     * 근거: 인용 — docs/SCREEN.md 4장 ③-1 설명, 버튼 [다 됐어요] [원래대로]
     */
    const val ADJUST_DETAIL = "다 되면 ‘됐어’라고 말씀해 주세요."
    const val DONE = "다 됐어요"
    const val RESTORE = "원래대로"

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 변수: ASK_NAME, NAME_TITLE, NAME_DETAIL, NAME_SKIP
     * 목적: 맞춘 자세를 누구 이름으로 기억할지 묻는 말과 카드 글자.
     * 근거: 인용 — docs/SCREEN.md 4장 ③-4
     */
    const val ASK_NAME = "이 자세를 누구 이름으로 기억할까요?"
    const val NAME_TITLE = "누구 자세로 기억할까요?"
    const val NAME_DETAIL = "이름을 말씀해 주세요."
    const val NAME_SKIP = "기억 안 함"

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 수정: 2026-11-14 03:00:00 · 박건준
     * 변수: RESTORED, SKIPPED, UNREADABLE, BLOCKED_MOVING, BLOCKED_GEAR
     * 목적: 원래대로 돌렸을 때, 기억하지 않을 때, 위치를 몰라 기억하지 못할 때,
     *       달리는 중·P가 아닐 때 하는 말.
     * 근거: 인용 — docs/SCREEN.md 4장 ③ 규칙 "주행 중에 … '차가 멈춘 뒤에
     *       할게요.'", 프로젝트 규칙 "시트는 P단에서만". 나머지는 미정(대사집에
     *       없는 경우)
     */
    const val RESTORED = "원래대로 돌렸어요."
    const val SKIPPED = "알겠어요. 기억하지 않을게요."
    const val UNREADABLE = "시트 위치를 몰라서 기억하지 못했어요."
    const val BLOCKED_MOVING = "차가 멈춘 뒤에 할게요."
    const val BLOCKED_GEAR = "P단에서 맞출게요."

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 변수: NEW_TITLE, NEW_YES, NEW_NO
     * 목적: 처음 보는 이름일 때 카드 제목과 두 버튼.
     * 근거: 미정 — docs/SCREEN.md 4장 ③ 규칙 문장에 맞춰 정했다
     */
    const val NEW_TITLE = "같이 맞춰 볼까요?"
    const val NEW_YES = "같이 맞추기"
    const val NEW_NO = "괜찮아요"

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: adjustTitle
     * 목적: 맞추는 중 카드 제목("운전석 시트 맞추는 중").
     * 인자: seat — 좌석
     * 반환: 제목
     * 근거: 인용 — docs/SCREEN.md 4장 ③-1
     */
    fun adjustTitle(seat: Seat) = "${seat.label} 시트 맞추는 중"

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: axisName
     * 목적: 카드 칸에 보일 축 이름(앞뒤, 높이, 등받이).
     * 인자: axis — 축
     * 반환: 이름
     */
    fun axisName(axis: SeatAxis) = if (axis == SeatAxis.BACKREST_ANGLE) "등받이" else axis.label

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: moved
     * 목적: 축을 움직인 뒤 칸 아래 글자와 말의 앞부분("앞으로 옮김").
     * 인자: axis — 축
     *       forward — 앞·위·세움이면 true
     * 반환: 글자
     * 근거: 인용 — docs/SCREEN.md 4장 ③-2·③-3 "앞으로 옮김", "등받이 세움"
     */
    fun moved(axis: SeatAxis, forward: Boolean) = when (axis) {
        SeatAxis.FORE_AFT -> if (forward) "앞으로 옮김" else "뒤로 옮김"
        SeatAxis.HEIGHT -> if (forward) "높임" else "낮춤"
        SeatAxis.BACKREST_ANGLE -> if (forward) "등받이 세움" else "등받이 눕힘"
        else -> "${axis.label} 바꿈"
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: movedSay
     * 목적: 축을 움직인 뒤 하는 말("앞으로 옮겼어요.").
     * 인자: axis — 축
     *       forward — 앞·위·세움이면 true
     * 반환: 문장
     * 근거: 미정 — 2026-11-13 박희정 추천안: "네." 대신 한 일을 말한다
     */
    fun movedSay(axis: SeatAxis, forward: Boolean) = when (axis) {
        SeatAxis.FORE_AFT -> if (forward) "앞으로 옮겼어요." else "뒤로 옮겼어요."
        SeatAxis.HEIGHT -> if (forward) "높였어요." else "낮췄어요."
        SeatAxis.BACKREST_ANGLE -> if (forward) "등받이 세웠어요." else "등받이 눕혔어요."
        else -> "${withObject(axis.label)} 바꿨어요."
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: saved, savedNotice
     * 목적: 이름으로 기억한 뒤 하는 말과 토스트.
     * 인자: name — 이름
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 4장 ③-5
     */
    fun saved(name: String) = "$name 님 자세로 기억했어요."
    fun savedNotice(name: String) = "$name 님 자세를 기억했어요"

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: recalling, recalled, recallTitle
     * 목적: 저장된 자세로 맞출 때 시작·끝 말과 실행 이름("희정 님 자세로").
     * 인자: name — 이름
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 4장 ③-6, ③-7
     */
    fun recalling(name: String) = "$name 님 자세로 맞출게요."
    fun recalled(name: String) = "$name 님 자세로 맞췄어요."
    fun recallTitle(name: String) = "$name 님 자세로"

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: unknown
     * 목적: 처음 보는 이름일 때 같이 맞출지 묻는 말.
     * 인자: name — 이름
     * 반환: 문장
     * 근거: 인용 — docs/SCREEN.md 4장 ③ 규칙 "희정 님 자세는 아직 없어요. 같이
     *       맞춰 볼까요?"
     */
    fun unknown(name: String) = "$name 님 자세는 아직 없어요. 같이 맞춰 볼까요?"
}
