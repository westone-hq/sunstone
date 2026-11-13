package com.westone.sunstone.routines.library

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.Choice
import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.Question
import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.dialog.QuestionRow
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.engine.Routine
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.Step
import com.westone.sunstone.routines.engine.StepOutcome
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.CarPort
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.MemoryPort
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.speech.ChildLockLines
import java.time.LocalDate

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-13 14:00:00 · 박희정
 * 클래스: ChildLockRoutine
 * 목적: 차일드락(아이 탑승). 뒷문 차일드락은 앱이 걸 수 없어 안내하고, 대신
 *       앱이 할 수 있는 것(뒷창 잠금, 문 잠금, 공조, 뒷유리 블라인드)을 한 번에
 *       한다.
 *       - 유치원으로 길안내를 시작하면 아이가 탔는지 물으며 카드로 권한다
 *       - 그날 처음: 정차 중 뒷자리에 타고 뒷문이 닫히면 카드로 권한다
 *       - 그날 다시: 뒷자리에 누가 탄 채 정차 중 길안내를 시작하면 권한다.
 *         집이면 "아침처럼"으로 권한다(`RoutineRunner.recheck()`가 부른다)
 *       - 다 하고 나서 1~2문장으로 말하고, 맞춘 것은 카드 칸이 보여 준다
 *       - 달리는 중 뒷자리 벨트가 풀리면 말과 토스트로 알린다
 *       - 길안내 목적지에 도착해 P로 서면 원래대로 돌리고 알린다. [끝내기]로
 *         끝내면 도착 말 없이 끝낸다고 말한다. 문 잠금은 풀지 않는다
 * 인자: route — 지금 길안내 목적지. `TripDialog`와 같은 것을 넘긴다
 *       memory — 차일드락을 쓴 날을 남기고 읽는다
 *       asking — 벨트 토스트를 낸다. `RoutineRunner`·`TripDialog`와 같은 것을
 *       넘긴다
 *       today — 오늘 날짜. 테스트에서 고정한다
 * 근거: 인용 — docs/SCREEN.md 4장 ② 표와 "정한 것"(하원길 판단, 뒷문
 *       차일드락), 6장 ② 카드. 문 잠금·공조·블라인드는 2026-11-13 박희정이
 *       더 넣기로 정했다(미정)
 */
class ChildLockRoutine(
    private val route: CurrentRoute,
    private val memory: MemoryPort,
    private val asking: Asking,
    private val today: () -> LocalDate,
) : Routine {

    override val id = RoutineId.CHILD_LOCK
    override val title = "차일드락"
    override val intro = "아이 탈 준비할게요."
    override val reportsAtEnd = true
    override val afterUndo = listOf(ChildLockLines.FINISHED)
    override val autoUndoSay = listOf(ChildLockLines.ARRIVED)
    override val undoNotice = ChildLockLines.UNLOCKED_NOTICE

    /**
     * 마지막으로 본 길안내 목적지. 권하기를 볼 때마다 바꾸고, 도착해 되돌릴 때도 넣는다. 그 뒤 목적지가
     * 바뀌면 "새 길안내를 시작했다"로 본다. 처음이면 null
     */
    @Volatile
    private var seenDestination: PlaceHit? = null

    /** 방금 무엇으로 권했는지. `suggestion()`이 정하고 `question()`이 읽는다. 권하지 않았으면 null */
    @Volatile
    private var ask: Ask? = null

    /** 뒷자리에 탈 때 이미 물었으면 true. 유치원 길안내 때 다시 묻지 않으려고 둔다. 뒷자리가 비면 지운다 */
    @Volatile
    private var askedAtBoarding = false

    /**
     * 작성: 2026-11-13 14:00:00 · 박희정
     * 클래스: Ask
     * 목적: 차일드락을 무엇으로 권했는지. 말과 카드 글자가 다르다.
     *       - `BOARDED` — 뒷자리에 탐
     *       - `KIDS` — 유치원으로 길안내를 시작함(아이가 탔는지 물음)
     *       - `AGAIN` — 오늘 썼고 집으로 길안내를 시작함("아침처럼")
     */
    private enum class Ask { BOARDED, KIDS, AGAIN }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-14 03:00:00 · 박건준
     * 함수: blockedReason
     * 목적: 달리는 중이면 하지 않는다. 단계마다 다시 본다.
     * 인자: state — 지금 차 상태
     * 반환: 이유 문장. 해도 되면 null
     * 근거: 인용 — 프로젝트 규칙 "차를 바꾸는 일은 정차 중에만"
     */
    override fun blockedReason(state: CarState): String? =
        if (!state.stopped) ChildLockLines.BLOCKED_MOVING else null

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: plan
     * 목적: 차일드락 단계를 만든다. 뒷창 잠금(좌·우, 한 칸) → 문 잠금 → 공조 →
     *       뒷유리 블라인드 → 뒷문 차일드락 안내 순서. 이 차에 없는
     *       블라인드(읽을 수 없음)는 넣지 않는다.
     * 인자: request — 실행 요청. 쓰지 않는다
     *       car — 블라인드가 있는지 보려고 읽는다
     * 반환: 실행할 단계들
     * 근거: 측정 — 공조는 좌석별 전원이 없어 차 전체 공조가 켜진다. 블라인드는
     *       에뮬레이터에 없다(docs/api-spike.md 차일드락 공조·블라인드 행). 없는
     *       것은 빼는 방식은 세차 모드 선루프와 같다
     */
    override suspend fun plan(request: RunRequest, car: CarPort): List<Step> {
        val hasBlind = car.read(Target.RearBlind) != null // 뒷유리 블라인드가 있는지
        return listOfNotNull(
            rearWindow(Window.REAR_LEFT),
            rearWindow(Window.REAR_RIGHT),
            Step.Control(
                ChildLockLines.STEP_DOOR_LOCK, Command(Target.DoorLock, true), ChildLockLines.DOOR_MANUAL,
                restoreOnUndo = false, icon = "lock",
            ),
            Step.Control(
                ChildLockLines.STEP_CLIMATE, Command(Target.Climate(Seat.REAR_LEFT), true),
                ChildLockLines.CLIMATE_MANUAL, icon = "air",
            ),
            if (hasBlind) {
                Step.Control(
                    ChildLockLines.STEP_BLIND, Command(Target.RearBlind, true), ChildLockLines.BLIND_MANUAL,
                    icon = "blind",
                )
            } else {
                null
            },
            Step.Guide(ChildLockLines.STEP_CHILD_LOCK, ChildLockLines.DOOR_CHILD_LOCK, "child", ChildLockLines.MANUAL),
        )
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: report
     * 목적: 다 하고 나서 할 말을 만든다. 뒷창문을 잠갔으면 "뒷창문 잠갔어요.",
     *       못 잠갔으면 직접 잠가 달라고 한다. 그날 처음이면 뒷문 차일드락 안내를
     *       붙인다. 앱이 하나라도 했으면 오늘을 남긴다.
     * 인자: outcomes — 단계마다의 결과
     * 반환: 말할 문장 하나
     * 근거: 인용 — docs/SCREEN.md 4장 ②-2, ②-9
     */
    override fun report(outcomes: List<StepOutcome>): List<String> {
        val windows = outcomes.filter { (it.step as? Step.Control)?.command?.target is Target.WindowLock } // 뒷창
        val locked = windows.isNotEmpty() && windows.all { it.result?.ok == true } // 뒷창문을 다 잠갔는지
        val firstToday = memory.lastChildLockDate() != today() // 오늘 처음 맞추는지
        if (outcomes.any { it.result?.ok == true }) {
            memory.recordChildLock(today()) // → 로직 이동: MemoryPort.recordChildLock()
        }
        val sentences = listOfNotNull( // 차례로 이을 문장
            if (locked) ChildLockLines.LOCKED else ChildLockLines.REAR_WINDOW_MANUAL,
            if (firstToday) ChildLockLines.DOOR_CHILD_LOCK else null,
        )
        return listOf(sentences.joinToString(" "))
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 14:00:00 · 박희정
     * 함수: suggestion
     * 목적: 정차 중일 때 차일드락을 권한다. 한 번에 하나만 묻는다.
     *       1. 유치원으로 길안내를 시작하면 아이가 탔는지 물으며 권한다. 탄
     *          사람이 아이인지는 차가 알 수 없어 탑승 여부와 상관없이 묻는다
     *       2. 오늘 처음이면 뒷문이 닫히는 순간 뒷자리에 사람이 있으면 권한다.
     *          탈 때 이미 물었으면 1은 다시 묻지 않는다(뒷자리가 빌 때까지)
     *       3. 오늘 썼으면 탈 때는 묻지 않고, 뒷자리에 누가 있는 채 길안내를
     *          새로 시작하면 권한다. 목적지가 집이면 "아침처럼"으로 권한다
     * 인자: before — 바뀌기 전 차 상태
     *       now — 지금 차 상태
     * 반환: 권할 말. 권하지 않으면 null
     * 근거: 인용 — 2026-11-13 박건준 요청 "유치원 간다고 하면 아이가 타고
     *       있는지 물어보면서 차일드락", docs/SCREEN.md 4장 ②-1, ②-8, "정한 것"
     *       하원길 판단. 대화 정책: 주행 중 새 제안 금지. 탑승으로 묻는 것도
     *       두기로 2026-11-13 박희정이 정했다
     * 미구현: 오늘 썼는데 길안내 없이 타면 권하지 않는다. 대사집에 없는 경우라
     *       정하지 않았다
     */
    override fun suggestion(before: CarState, now: CarState): String? {
        val destination = route.destination // 지금 길안내 목적지, 없으면 null
        val newRoute = destination != null && destination != seenDestination // 길안내를 새로 시작했는지
        seenDestination = destination
        if (!now.rearOccupied) askedAtBoarding = false
        if (!now.stopped) return null
        val usedToday = memory.lastChildLockDate() == today() // 오늘 차일드락을 썼는지
        ask = when {
            newRoute && destination?.kind == Place.KIDS -> if (askedAtBoarding) null else Ask.KIDS
            newRoute && usedToday && now.rearOccupied -> if (destination?.kind == Place.HOME) Ask.AGAIN else Ask.BOARDED
            !usedToday && now.rearOccupied && before.anyRearDoorOpen && !now.anyRearDoorOpen -> Ask.BOARDED
            else -> null
        }
        if (ask == Ask.BOARDED) askedAtBoarding = true
        return when (ask) {
            Ask.KIDS -> ChildLockLines.KIDS_ASK
            Ask.AGAIN -> ChildLockLines.AGAIN
            Ask.BOARDED -> ChildLockLines.BOARDED
            null -> null
        }
    }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 수정: 2026-11-13 14:00:00 · 박희정
     * 함수: question
     * 목적: 권할 때 올릴 카드. 할 일 칸은 뒷창문, 문 잠금, 공조, 차일드락
     *       ("직접")이다. 제목·설명은 무엇으로 권했는지에 따라 다르다.
     *       - 탑승 — "뒷창문 잠글까요?"
     *       - 유치원 길안내 — "차일드락 켤까요?"
     *       - 하원길 — "차일드락 다시 켤까요?"
     * 인자: now — 지금 차 상태. 쓰지 않는다
     * 반환: 차일드락 질문
     * 근거: 인용 — docs/SCREEN.md 4장 ②-1·②-8 패널, 6장 ② 칸
     * 미구현: 블라인드는 실행해 봐야 있는지 알 수 있어 카드 칸에 미리 넣지 않고,
     *       있으면 맞추는 중 카드에만 보인다
     */
    override fun question(now: CarState): Question = Question(
        kind = if (ask == Ask.AGAIN) QuestionKind.CHILD_LOCK_AGAIN else QuestionKind.CHILD_LOCK,
        title = when (ask) {
            Ask.KIDS -> ChildLockLines.KIDS_TITLE
            Ask.AGAIN -> ChildLockLines.AGAIN_TITLE
            else -> ChildLockLines.ASK_TITLE
        },
        detail = when (ask) {
            Ask.KIDS -> ChildLockLines.KIDS_DETAIL
            Ask.AGAIN -> ChildLockLines.AGAIN_DETAIL
            else -> ChildLockLines.ASK_DETAIL
        },
        rows = listOf(
            QuestionRow("window", ChildLockLines.STEP_REAR_WINDOW),
            QuestionRow("lock", ChildLockLines.STEP_DOOR_LOCK),
            QuestionRow("air", ChildLockLines.STEP_CLIMATE),
            QuestionRow("child", ChildLockLines.STEP_CHILD_LOCK, manual = true, note = ChildLockLines.MANUAL),
        ),
        choices = listOf(Choice(ChildLockLines.ASK_YES, Reply.YES), Choice(ChildLockLines.ASK_NO, Reply.NO)),
    )

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 11:00:00 · 박희정
     * 함수: watch
     * 목적: 달리는 중 뒷자리 벨트가 풀리는 순간 말과 경고 토스트로 알린다. 다시
     *       맸다가 풀어야 또 알린다.
     * 인자: before — 바뀌기 전 차 상태
     *       now — 지금 차 상태
     * 반환: 알릴 말. 없으면 null
     * 근거: 인용 — docs/SCREEN.md 4장 ②-5 "토스트 같은 문장. 주행 중이라 패널
     *       없음"
     */
    override fun watch(before: CarState, now: CarState): String? {
        if (now.stopped) return null
        val unbuckled = Seat.entries.any { it.isRear && before.buckled(it) && !now.buckled(it) } // 막 풀렸는지
        if (!unbuckled) return null
        asking.alert(ChildLockLines.BELT_OFF) // → 로직 이동: Asking.alert()
        return ChildLockLines.BELT_OFF
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: shouldUndo
     * 목적: 길안내 목적지(유치원·집)에 도착해 P로 서면 되돌린다. 차일드락을 켠
     *       곳이 그 목적지면 되돌리지 않는다. 되돌릴 때 그 목적지를 본 것으로
     *       남겨, 내리는 동안 "새 길안내"로 다시 권하지 않게 한다.
     * 인자: start — 차일드락을 켠 순간의 상태
     *       now — 지금 차 상태
     * 반환: 되돌릴 때면 true
     * 근거: 인용 — docs/SCREEN.md 4장 ②-6, ②-10 "도착했어요. 뒷창문 잠금
     *       풀었어요."
     * 미구현: 길안내 없이 가면 저절로 되돌리지 않는다. "차일드락 꺼"로 끈다
     */
    override fun shouldUndo(start: CarState, now: CarState): Boolean {
        val destination = route.destination ?: return false // 길안내 목적지
        val arrived = destination.kind != Place.NONE && now.place == destination.kind && now.place != start.place &&
            now.stopped && now.gear == Gear.PARK // 목적지에 도착해 P로 섰는지
        if (arrived) seenDestination = destination
        return arrived
    }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: rearWindow
     * 목적: 뒷창 잠금 단계 하나를 만든다. 두 창문이 같은 이름이라 카드에서 한
     *       칸("뒷창문")으로 보인다.
     * 인자: window — 뒷창문(왼쪽·오른쪽)
     * 반환: 제어 단계
     * 근거: 측정 — 뒷창 잠금은 SDK 다시 읽기로 확인하고 콘솔에도
     *       반영된다(docs/api-spike.md 차일드락 창문 잠금 행)
     */
    private fun rearWindow(window: Window) = Step.Control(
        ChildLockLines.STEP_REAR_WINDOW, Command(Target.WindowLock(window), true), ChildLockLines.REAR_WINDOW_MANUAL,
        icon = "window",
    )
}
