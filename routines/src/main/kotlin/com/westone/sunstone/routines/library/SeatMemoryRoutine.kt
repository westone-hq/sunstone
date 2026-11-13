package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.engine.StepOutcome
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.SeatProfile
import com.westone.sunstone.routines.port.SeatProfiles
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.SeatLines

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-13 15:00:00 · 박희정
 * 클래스: SeatMemoryRoutine
 * 목적: 시트 메모리. 사람 이름으로 저장한 자세로 운전석이나 조수석을 맞춘다
 *       (시나리오 ③-6·③-7). 차 메모리 버튼은 1~2개뿐이고 누가 몇 번인지 잊어서
 *       앱이 이름으로 기억한다.
 *       - 정차 중 P단에서만 움직인다
 *       - 축마다 하나씩 맞추고(카드 칸이 차례로 체크), 끝나면 한 번 말하고
 *         토스트를 낸다
 *       - 저장된 위치가 없고 차 메모리 번호만 있으면 그 번호를 부른다
 *       - 맞춘 뒤 다시 부르면 새로 맞춘다. 되돌리기는 마지막 실행 기준
 * 인자: profiles — 사람별 자세 저장소
 *       asking — 끝났을 때 토스트를 낸다. 없으면 말만 한다
 * 근거: 인용 — docs/SCREEN.md 4장 ③-6·③-7, CLAUDE.md "시트는 P단에서만"
 */
class SeatMemoryRoutine(
    private val profiles: SeatProfiles,
    private val asking: Asking? = null,
) : Routine {

    private companion object {
        /**
         * 작성: 2026-11-13 15:00:00 · 박희정
         * 변수: ORDER, ICONS
         * 목적: 맞추는 축 순서와 카드 칸 아이콘. 저장된 다른 축(쿠션 각도 등)은
         *       맞추지 않는다(시트 맞추기 대화는 이 세 축만 저장한다).
         * 근거: 인용 — docs/SCREEN.md 4장 ③-6 줄 앞뒤·높이·등받이
         */
        val ORDER = listOf(SeatAxis.FORE_AFT, SeatAxis.HEIGHT, SeatAxis.BACKREST_ANGLE)
        val ICONS = mapOf(
            SeatAxis.FORE_AFT to "seat",
            SeatAxis.HEIGHT to "seat-height",
            SeatAxis.BACKREST_ANGLE to "seat-back",
        )
    }

    override val id = RoutineId.SEAT_MEMORY
    override val title = "시트 메모리"
    override val intro = "자세 맞출게요."
    override val reportsAtEnd = true
    override val afterUndo = listOf(SeatLines.RESTORED)
    override val staysActive = false

    /** 지금 맞추는 사람 이름. `plan()`이 넣고 `report()`가 말에 쓴다. 없으면 null */
    @Volatile
    private var current: String? = null

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: blockedReason
     * 목적: 달리는 중이거나 P단이 아니면 움직이지 않는다.
     * 인자: state — 지금 차 상태
     * 반환: 이유 문장. 해도 되면 null
     */
    override fun blockedReason(state: CarState): String? = when {
        !state.stopped -> SeatLines.BLOCKED_MOVING
        state.gear != Gear.PARK -> SeatLines.BLOCKED_GEAR
        else -> null
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: runTitle
     * 목적: 화면 카드 이름("희정 님 자세로" 맞추는 중).
     * 인자: request — 실행 요청
     * 반환: 실행 이름. 이름이 없으면 루틴 이름
     */
    override fun runTitle(request: RunRequest): String = request.profile?.let(SeatLines::recallTitle) ?: title

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: plan
     * 목적: 저장된 자세의 축마다 맞추는 단계를 만든다(요청한 좌석). 앞뒤 →
     *       높이 → 등받이 순서. 위치가 없고 메모리 번호만 있으면 그 번호를
     *       부른다. 자세가 없으면 안내 단계 하나.
     * 인자: request — 실행 요청. `profile`(이름)과 `seat`(좌석)을 쓴다
     *       car — 쓰지 않는다
     * 반환: 실행할 단계들
     */
    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> {
        val name = request.profile ?: profiles.names().firstOrNull() // 맞출 사람
        current = name
        val profile = name?.let(profiles::get)
            ?: return listOf(Step.Guide("저장된 자세 없음", "저장된 자세가 없어요."))
        if (profile.positions.isNotEmpty()) {
            return ORDER.filter { it in profile.positions }.map { axis ->
                Step.Control(
                    title = SeatLines.axisName(axis),
                    command = Command(Target.SeatPosition(request.seat, axis), profile.positions.getValue(axis)),
                    fallback = "시트 ${axis.label}를 직접 맞춰 주세요.",
                    icon = ICONS[axis],
                )
            }
        }
        val slot = profile.memorySlot
            ?: return listOf(Step.Guide("저장된 자세 없음", "${profile.name} 자세가 비어 있어요."))
        return listOf(
            Step.Control(
                title = "메모리 ${slot}번",
                command = Command(Target.SeatMemory(request.seat), slot),
                fallback = "시트 메모리 ${slot}번 버튼을 눌러 주세요.",
                restoreOnUndo = false,
                icon = "seat",
            ),
        )
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: report
     * 목적: 다 맞춘 뒤 "희정 님 자세로 맞췄어요."라고 말하고 같은 토스트를 낸다
     *       (③-7). 하나도 못 맞췄으면 말하지 않는다(단계 안내가 대신한다).
     * 인자: outcomes — 단계마다의 결과
     * 반환: 말할 문장
     */
    override fun report(outcomes: List<StepOutcome>): List<String> {
        val name = current ?: return emptyList() // 맞춘 사람
        if (outcomes.none { it.result?.ok == true }) return emptyList()
        asking?.notice(SeatLines.recalled(name).removeSuffix(".")) // → 로직 이동: Asking.notice()
        return listOf(SeatLines.recalled(name))
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 함수: capture
     * 목적: 지금 운전석 자세를 [name]으로 저장한다("지금 자세 엄마로 저장해줘").
     * 인자: name — 이름
     *       car — 위치를 읽는다
     *       memorySlot — 차 메모리 번호. 없으면 null
     * 반환: 저장한 자세. 읽을 수 있는 축이 하나도 없으면 null
     */
    suspend fun capture(name: String, car: CarPort, memorySlot: Int? = null): SeatProfile? {
        val positions = SeatAxis.entries.mapNotNull { axis -> // 읽은 축 위치
            (car.read(Target.SeatPosition(Seat.DRIVER, axis)) as? Int)?.let { axis to it }
        }.toMap()
        if (positions.isEmpty() && memorySlot == null) return null
        return SeatProfile(name, positions, memorySlot).also(profiles::save)
    }
}
