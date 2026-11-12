package com.westone.sunstone.routines

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
import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.PlaceHit
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 클래스: ScriptTest
 * 목적: 대사집(`ScriptBook`) 전체를 가짜 차 + 고정 응답으로 차례로 돌려 기대 말·
 *       기대 변화와 비교한다(Tab 1 4.2 실행 방식 3 자동 테스트). 장면마다
 *       1. 준비를 한 단계씩 넣는다
 *       2. 대표 문장과 변형이 모두 같은 요청으로 알아들어지는지 본다
 *       3. 대표 문장을 앱처럼 처리하고, 한 말과 차·내비 값을 대사집과 비교한다
 *       대사집을 고치면 이 테스트가 그대로 따라간다.
 * 근거: 인용 — Tab 1 4.2 "가짜 차 + 고정 응답으로 대사집 전체를 돌려 기대 말·
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

    /** 앱이 한 말. 장면마다 비운다 */
    private val said = mutableListOf<String>()

    /** 가짜 차. Pleos 에뮬레이터처럼 선루프가 없다 */
    private val car = FakeCar(CarState()).apply {
        unsupported = setOf(Target.WindowPosition(Window.ROOF_1), Target.WindowPosition(Window.ROOF_2))
    }

    /** 가짜 내비. 앱의 시연 장소 목록(places.json)과 같은 목적지 */
    private val navi = FakeNavi(listOf(PlaceHit("파라다이스 호텔 부산", GeoPoint(35.1602, 129.1652))))

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

    /** 길안내 대화 */
    private val trip = TripDialog(car, navi, info, memory, booking, { said += it }, { TODAY })

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: runner
     * 목적: 앱과 같은 루틴 세 개로 실행기를 만들고 상황 감지를 시작한다.
     * 반환: 실행기
     */
    private fun TestScope.runner() = RoutineRunner(
        car = car,
        routines = listOf(
            WashRoutine(booking, memory, { TODAY }),
            ChildLockRoutine(),
            SeatMemoryRoutine(InMemorySeatProfiles()),
        ),
        speaker = { said += it },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.start() }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: prepare
     * 목적: 장면 준비 한 단계를 가짜 차와 기억에 넣는다.
     * 인자: prep — 준비 한 단계
     */
    private fun prepare(prep: Prep) {
        prep.washedDaysAgo?.let { memory.recordWash(TODAY.minusDays(it)) }
        car.update {
            it.copy(
                gear = prep.gear ?: it.gear,
                speedKmh = prep.speedKmh ?: it.speedKmh,
                place = prep.place ?: it.place,
                fuelPercent = prep.fuelPercent ?: it.fuelPercent,
            )
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: handle
     * 목적: 알아들은 요청을 앱(`AppGraph.handle()`)과 같은 순서로 처리한다.
     * 인자: runner — 실행기
     *       action — 알아들은 요청
     */
    private suspend fun handle(runner: RoutineRunner, action: VoiceAction) {
        when (action) {
            is VoiceAction.Navigate -> trip.navigate(action.destination)
            is VoiceAction.Answer -> if (!trip.answer(action.yes)) runner.answer(action.yes)
            is VoiceAction.Run -> runner.request(action.request)
            else -> error("대사집 테스트가 다루지 않는 요청: $action")
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: check
     * 목적: 장면의 기대 말·기대 변화를 확인한다. 실패하면 장면 ID를 붙여 알린다.
     * 인자: scene — 장면
     */
    private fun check(scene: Scene) {
        assertEquals("${scene.id} 기대 말", scene.expectSaid, said)
        scene.expectCar.forEach { (target, value) ->
            assertEquals("${scene.id} 기대 변화 $target", value, car.value(target))
        }
        scene.expectDestination?.let { assertEquals("${scene.id} 목적지", it, navi.destination?.name) }
        scene.expectWaypoints?.let { assertEquals("${scene.id} 경유지", it, navi.waypoints.map { w -> w.name }) }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: 시나리오 1 대사집 S1부터 S5까지
     * 목적: 시나리오 ① 장면을 차례로 돌린다.
     */
    @Test
    fun `시나리오 1 대사집 S1부터 S5까지`() = runTest {
        val runner = runner()
        runCurrent()
        ScriptBook.scenarioOne.forEach { scene ->
            said.clear()
            scene.prep.forEach { prepare(it); runCurrent() }
            scene.driver?.let { line ->
                (listOf(line.text) + line.variants).forEach { text ->
                    assertEquals("${scene.id} \"$text\" 알아듣기", line.intent, PhraseMatcher.match(text))
                }
                handle(runner, line.intent)
                runCurrent()
            }
            check(scene)
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: 장면 ID는 겹치지 않는다
     * 목적: adb `scene S3`가 장면을 하나로 찾을 수 있는지 본다.
     */
    @Test
    fun `장면 ID는 겹치지 않는다`() {
        val ids = ScriptBook.scenarioOne.map { it.id } // 장면 ID들
        assertEquals(ids.distinct(), ids)
        assertEquals(Gear.PARK, ScriptBook.scenarioOne.first().prep.first().gear) // 첫 장면은 P단에서 시작
    }
}
