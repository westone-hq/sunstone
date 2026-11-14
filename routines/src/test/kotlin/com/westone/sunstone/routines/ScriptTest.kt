package com.westone.sunstone.routines

import com.westone.sunstone.routines.dialog.Asking
import com.westone.sunstone.routines.dialog.CurrentRoute
import com.westone.sunstone.routines.dialog.SeatDialog
import com.westone.sunstone.routines.dialog.TripDialog
import com.westone.sunstone.routines.dialog.WashBooking
import com.westone.sunstone.routines.engine.RoutineRunner
import com.westone.sunstone.routines.fake.FakeCar
import com.westone.sunstone.routines.fake.FakeInfo
import com.westone.sunstone.routines.fake.FakeNavi
import com.westone.sunstone.routines.library.ChildLockRoutine
import com.westone.sunstone.routines.library.SeatMemoryRoutine
import com.westone.sunstone.routines.library.WashRoutine
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.PlaceHit
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.model.Window
import com.westone.sunstone.routines.port.InMemoryMemory
import com.westone.sunstone.routines.port.InMemorySeatProfiles
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.script.Prep
import com.westone.sunstone.routines.script.Scene
import com.westone.sunstone.routines.script.ScriptBook
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: ScriptTest
 * 목적: 대사집(`ScriptBook`) 전체를 가짜 차 + 고정 응답으로 차례로 돌려 기대 말·
 *       토스트·질문·기대 변화와 비교한다(대사집 자동 테스트).
 *       장면 묶음(준비나 말이 있는 장면 + 뒤에 이어지는 장면)마다
 *       1. 준비를 한 단계씩 넣는다
 *       2. 대표 문장과 변형이 모두 같은 요청으로 알아들어지는지 본다
 *       3. 대표 문장을 앱처럼 처리하고, 이어지는 장면의 기다리는 시간
 *          (`Scene.afterMs`, 세차 모드 5초)을 앞당긴 뒤 한 말·토스트·질문과
 *          차·내비 값을 대사집과 비교한다
 *       대사집을 고치면 이 테스트가 그대로 따라간다.
 * 근거: 인용 — 시연 계획 "가짜 차 + 고정 응답으로 대사집 전체를 돌려 기대 말·
 *       기대 변화와 비교한다. 코드를 고칠 때마다 돌린다"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptTest {

    private companion object {
        /**
         * 작성: 2026-11-11 16:42:25 · 박희정
         * 변수: TODAY
         * 목적: 테스트의 오늘 날짜.
         */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 11)
    }

    /** 앱이 한 말. 장면 묶음마다 비운다 */
    private val said = mutableListOf<String>()

    /** 앱이 낸 토스트. 장면 묶음마다 비운다 */
    private val noticed = mutableListOf<String>()

    /** 가짜 차. Pleos 에뮬레이터처럼 선루프와 뒷유리 블라인드가 없다 */
    private val car = FakeCar(CarState()).apply {
        unsupported =
            setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2), Target.RearBlind)
    }

    /** 가짜 내비. 앱의 시연 장소 목록(places.json)과 같은 목적지 */
    private val navi = FakeNavi(
        listOf(
            PlaceHit("파라다이스 호텔 부산", GeoPoint(35.1602, 129.1652)),
            PlaceHit("OO유치원", GeoPoint(37.5058, 127.0446), Place.KIDS),
            PlaceHit("집", GeoPoint(37.5007, 127.0365), Place.HOME),
        ),
    )

    /** 고정 응답. 앱의 `FixedInfo`와 같은 주유소 세 곳, 일주일 비 소식 없음 */
    private val info = FakeInfo(
        listOf(
            GasStation(PlaceHit("말죽거리주유소", GeoPoint(37.4852, 127.0407)), 1650, carWash = true),
            GasStation(PlaceHit("서울석유양재주유소", GeoPoint(37.4781, 127.0396)), 1610, carWash = false),
            GasStation(PlaceHit("S-OIL 중앙주유소", GeoPoint(37.4816, 127.0422)), 1690, carWash = true),
        ),
        rain = false,
    )

    /** 마지막 세차일. 장면 준비가 넣는다 */
    private val memory = InMemoryMemory()

    /** 세차 예약 */
    private val booking = WashBooking()

    /** 질문·토스트 */
    private val asking = Asking()

    /** 지금 길안내 목적지. 길안내 대화와 차일드락이 함께 쓴다 */
    private val route = CurrentRoute()

    /** 길안내 대화 */
    private val trip = TripDialog(car, navi, info, memory, booking, asking, { said += it }, { TODAY }, route)

    /** 사람별 시트 자세. 시트 대화와 시트 메모리가 함께 쓴다 */
    private val profiles = InMemorySeatProfiles()

    /** 시트 맞추기 대화. `runner()`가 실행기와 함께 만든다 */
    private lateinit var seat: SeatDialog

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지와 토스트(경고
     *       토스트 포함) 받기를 시작한다. 시트 대화도 그 실행기로 만든다.
     * 반환: 실행기
     */
    private fun TestScope.runner(): RoutineRunner {
        backgroundScope.launch { asking.notices.collect { noticed += it } }
        backgroundScope.launch { asking.alerts.collect { noticed += it } } // 경고 토스트도 토스트로 본다
        return RoutineRunner(
            car = car,
            routines = listOf(
                WashRoutine(booking, memory, asking, { TODAY }),
                ChildLockRoutine(route, memory, asking, { TODAY }),
                SeatMemoryRoutine(profiles, asking),
            ),
            speaker = { said += it },
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            asking = asking,
        ).also { runner ->
            runner.start()
            seat = SeatDialog(car, profiles, asking, { said += it }) { runner.request(it) }
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: prepare
     * 목적: 장면 준비 한 단계를 가짜 차와 기억에 넣는다. 뒷문·뒷자리·벨트는
     *       뒷자리 왼쪽에 넣는다. 처음으로 돌리면 앱(`AppGraph.restartTrip()`)처럼
     *       질문과 길안내 목적지를 지운다. 세차일·차일드락 날은 기억에 넣는다.
     * 인자: prep — 준비 한 단계
     */
    private fun prepare(prep: Prep) {
        if (prep.resetTrip) {
            asking.newTrip()
            route.clear()
            seat.reset()
        }
        prep.washedDaysAgo?.let { memory.recordWash(TODAY.minusDays(it)) }
        prep.childLockDaysAgo?.let { memory.recordChildLock(TODAY.minusDays(it)) }
        prep.driverSeat?.forEach { (axis, at) -> car.set(Target.SeatPosition(Seat.DRIVER, axis), at) }
        prep.seatProfile?.let(profiles::save)
        car.update {
            it.copy(
                gear = prep.gear ?: it.gear,
                speedKmh = prep.speedKmh ?: it.speedKmh,
                place = prep.place ?: it.place,
                fuelPercent = prep.fuelPercent ?: it.fuelPercent,
                doorOpen = prep.rearDoorOpen?.let { open -> it.doorOpen + (Door.REAR_LEFT to open) } ?: it.doorOpen,
                seatOccupied = prep.rearOccupied?.let { on -> it.seatOccupied + (Seat.REAR_LEFT to on) }
                    ?: it.seatOccupied,
                beltBuckled = prep.rearBelt?.let { on -> it.beltBuckled + (Seat.REAR_LEFT to on) } ?: it.beltBuckled,
            )
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: handle
     * 목적: 알아들은 요청을 앱(`AppGraph.handle()`)과 같은 순서로 처리한다.
     *       대답은 루틴 권하기 → 길안내 대화 → 시트 대화 순서로 본다.
     *       길안내를 시작하면 앱처럼 권하기를 다시 본다(`RoutineRunner.recheck()`).
     * 인자: runner — 실행기
     *       action — 알아들은 요청
     */
    private suspend fun handle(runner: RoutineRunner, action: VoiceAction) {
        when (action) {
            is VoiceAction.Navigate -> {
                trip.navigate(action.destination)
                runner.recheck()
            }
            is VoiceAction.Answer ->
                if (!runner.answer(action.reply) && !trip.answer(action.reply)) seat.answer(action.reply)
            is VoiceAction.SeatStart -> seat.start(action.seat)
            is VoiceAction.SeatAdjust -> seat.adjust(action.axis, action.percent)
            is VoiceAction.SeatRecall -> seat.recall(action.name, action.seat)
            is VoiceAction.Name -> seat.name(action.name)
            is VoiceAction.Run -> runner.request(action.request)
            else -> error("대사집 테스트가 다루지 않는 요청: $action")
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-12 18:34:00 · 박건준
     * 함수: check
     * 목적: 장면 묶음의 기대 말·토스트·질문·기대 변화를 확인한다. 말과 토스트는
     *       묶음 안 장면 차례로 이은 것, 질문은 마지막 장면 것과 비교한다.
     *       실패하면 장면 ID를 붙여 알린다.
     * 인자: group — 장면 묶음
     */
    private fun check(group: List<Scene>) {
        val ids = group.joinToString("+") { it.id } // 알림에 붙일 장면 ID
        assertEquals("$ids 기대 말", group.flatMap { it.expectSaid }, said)
        assertEquals("$ids 토스트", group.flatMap { it.expectNotices }, noticed)
        assertEquals("$ids 질문", group.last().expectQuestion, asking.question.value?.kind)
        group.forEach { scene ->
            scene.expectCar.forEach { (target, value) ->
                assertEquals("${scene.id} 기대 변화 $target", value, car.value(target))
            }
            scene.expectDestination?.let { assertEquals("${scene.id} 목적지", it, navi.destination?.name) }
            scene.expectWaypoints?.let { assertEquals("${scene.id} 경유지", it, navi.waypoints.map { w -> w.name }) }
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 시나리오 1 대사집 S1부터 S9까지
     * 목적: 시나리오 ① 장면을 묶음별로 차례로 돌린다. 마지막에 오늘이 마지막
     *       세차일로 남았는지 본다.
     */
    @Test
    fun `시나리오 1 대사집 S1부터 S9까지`() = runTest {
        play(ScriptBook.scenarioOne)
        assertEquals(TODAY, memory.lastWashDate())
    }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: 시나리오 2 대사집 S10부터 S18까지
     * 목적: 시나리오 ② 장면을 묶음별로 차례로 돌린다. 마지막에 오늘이 차일드락을
     *       쓴 날로 남았는지 본다.
     */
    @Test
    fun `시나리오 2 대사집 S10부터 S18까지`() = runTest {
        play(ScriptBook.scenarioTwo)
        assertEquals(TODAY, memory.lastChildLockDate())
    }

    /**
     * 작성: 2026-11-13 15:00:00 · 박희정
     * 함수: 시나리오 3 대사집 S19부터 S25까지
     * 목적: 시나리오 ③ 장면을 묶음별로 차례로 돌린다. 마지막에 건준 님 자세가
     *       움직인 뒤 위치로 저장됐는지 본다.
     */
    @Test
    fun `시나리오 3 대사집 S19부터 S25까지`() = runTest {
        play(ScriptBook.scenarioThree)
        assertEquals(50, profiles.get("건준")?.positions?.get(SeatAxis.FORE_AFT))
        assertEquals(60, profiles.get("건준")?.positions?.get(SeatAxis.BACKREST_ANGLE))
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 두 시나리오를 이어서 돌려도 같다
     * 목적: 시연 모드에서 ① 다음 [다음 장면]으로 ②에 들어가도 ②를 처음부터 하는
     *       것과 같은 말을 하는지 본다.
     */
    @Test
    fun `두 시나리오를 이어서 돌려도 같다`() = runTest { play(ScriptBook.all) }

    /**
     * 작성: 2026-11-13 12:30:00 · 박희정
     * 함수: 차일드락 다음에 세차를 해도 같다
     * 목적: ②를 한 뒤 [처음]으로 ①을 해도 차일드락이 끼어들지 않고 ①과 같은
     *       말을 하는지 본다(에뮬레이터에서 ① S1에 차일드락을 물었던 일).
     */
    @Test
    fun `차일드락 다음에 세차를 해도 같다`() = runTest { play(ScriptBook.scenarioTwo + ScriptBook.scenarioOne) }

    /**
     * 작성: 2026-11-13 10:00:00 · 박희정
     * 함수: play
     * 목적: 장면들을 묶음별로 차례로 돌린다. 이어지는 장면은 앞 묶음에 붙인다.
     *       묶음마다 준비를 넣고, 말을 알아듣는지 본 뒤 처리하고, 기다리는
     *       시간을 앞당겨 기대 값과 비교한다.
     * 인자: scenes — 돌릴 장면들
     */
    private suspend fun TestScope.play(scenes: List<Scene>) {
        val runner = runner()
        runCurrent()
        val groups = mutableListOf<MutableList<Scene>>() // 장면 묶음, 이어지는 장면은 앞 묶음에 붙인다
        scenes.forEach { if (it.follows) groups.last() += it else groups += mutableListOf(it) }
        groups.forEach { group ->
            said.clear()
            noticed.clear()
            val scene = group.first() // 준비나 말이 있는 장면
            scene.prep.forEach { prepare(it); runCurrent() }
            scene.driver?.let { line ->
                (listOf(line.text) + line.variants).forEach { text ->
                    assertEquals("${scene.id} \"$text\" 알아듣기", line.intent, PhraseMatcher.match(text))
                }
                handle(runner, line.intent)
                runCurrent()
            }
            group.filter { it.afterMs > 0 }.forEach { advanceTimeBy(it.afterMs); runCurrent() }
            check(group)
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: 장면 ID는 겹치지 않는다
     * 목적: adb `scene S3`가 장면을 하나로 찾을 수 있는지, 시나리오마다 첫 장면이
     *       P단에서 처음부터(`resetTrip`) 시작하고 이어지는 장면이 아닌지 본다.
     */
    @Test
    fun `장면 ID는 겹치지 않는다`() {
        val ids = ScriptBook.all.map { it.id } // 장면 ID들
        assertEquals(ids.distinct(), ids)
        assertEquals(25, ids.size)
        listOf(ScriptBook.scenarioOne, ScriptBook.scenarioTwo, ScriptBook.scenarioThree).forEach { scenario ->
            val first = scenario.first() // 시나리오 첫 장면
            assertEquals(Gear.PARK, first.prep.first().gear) // P단에서 시작
            assertFalse(first.follows)
        }
        assertTrue(ScriptBook.scenarioTwo.first().prep.first().resetTrip)
    }
}
