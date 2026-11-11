package com.westone.sunstone.demo

import android.util.Log
import com.westone.sunstone.AppGraph
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.script.Prep
import com.westone.sunstone.routines.script.Scene
import com.westone.sunstone.routines.script.ScriptBook
import com.westone.sunstone.ui.SceneControls
import com.westone.sunstone.ui.SceneView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 함수: installDemo
 * 목적: debug 빌드에서 시연 모드를 붙인다. 앱이 켜질 때 한 번 부른다.
 * 인자: graph — 앱 조립부
 */
fun installDemo(graph: AppGraph) {
    graph.scenes = ScenePlayer(graph, ScriptBook.scenarioOne)
}

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 클래스: ScenePlayer
 * 목적: 대사집 장면을 실제 앱에 하나씩 넣는다(Tab 1 4.2 실행 방식 1·2).
 *       - `next()` — 화면 [다음 장면]. 다음 장면의 준비 상황을 넣고 대사를 띄운다
 *       - `goTo()` — adb `scene S3`. 처음부터 그 장면 앞까지 대사를 차례로 넣어
 *         건너뛰고, 그 장면을 준비한다. 앞 장면 ID를 주면 되돌린다
 *       - `restart()` — 처음(S1 전)으로
 *       Pleos 차면 기어·속도는 Virtual Console로, 장소·연료·세차일은 데모 입력으로
 *       넣는다. 가짜 차면 가짜 차 값을 바꾼다. Pleos 차일 때는 콘솔 연결 신호를
 *       1초마다 보낸다.
 * 인자: g — 앱 조립부
 *       scenes — 대사집 장면들
 * 미구현: 프로토타입이라 위치(GPS)는 옮기지 않고 장소만 바꾼다. 내비 화면의
 *       위치는 그대로다. 실제 이동이 필요하면 tools/scene-move.ps1을 쓴다
 */
internal class ScenePlayer(private val g: AppGraph, private val scenes: List<Scene>) : SceneControls {

    private companion object {
        /**
         * 작성: 2026-11-11 16:42:25 · 박희정
         * 변수: STEP_MS
         * 목적: 준비 단계 사이 기다리는 시간(밀리초). 앱이 바뀐 값을 받은 뒤 다음
         *       단계를 넣어야 권하기가 "바뀐 순간"을 알아챈다.
         * 근거: 측정 — 콘솔 속도는 앱에 2초 안에 들어온다(docs/api-spike.md 속도 행)
         */
        const val STEP_MS = 2_500L

        /**
         * 작성: 2026-11-11 16:42:25 · 박희정
         * 변수: REPLY_WAIT_MS
         * 목적: 건너뛸 때 대사 하나에 앱이 답하기를 기다리는 최대 시간(밀리초).
         * 근거: 측정 — 에뮬레이터를 켠 직후 첫 경로는 시작까지 24.5초 걸렸고,
         *       `PleosNavi`는 45초까지 기다린다. 그보다 조금 길게 잡았다
         */
        const val REPLY_WAIT_MS = 50_000L

        /**
         * 작성: 2026-11-11 16:42:25 · 박희정
         * 변수: TAG
         * 목적: 데모 logcat 태그.
         */
        const val TAG = "SunstoneDemo"
    }

    /** 지금 장면 화면 값. 시작 전이면 null */
    private val view = MutableStateFlow<SceneView?>(null)

    override val current: StateFlow<SceneView?> = view

    /** 지금 장면 번호(0부터). 시작 전이면 -1 */
    private var index = -1

    /** 준비·건너뛰기 작업. 도는 중이면 새 요청을 받지 않는다 */
    private var job: Job? = null

    init {
        if (g.pleos != null) {
            g.scope.launch {
                while (isActive) {
                    VirtualConsole.keepAlive() // → 로직 이동: VirtualConsole.keepAlive()
                    delay(1_000)
                }
            }
        }
    }

    override fun next() {
        if (job?.isActive == true || index + 1 >= scenes.size) return
        job = g.scope.launch { enter(index + 1) }
    }

    override fun restart() {
        if (job?.isActive == true) return
        job = g.scope.launch {
            index = -1
            view.value = null
            g.restartTrip() // → 로직 이동: AppGraph.restartTrip()
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: goTo
     * 목적: 그 장면으로 건너뛴다. 처음부터 다시 시작해 앞 장면들의 준비와 대사를
     *       차례로 넣고(앱이 답할 때까지 기다린다) 그 장면을 준비한다.
     * 인자: id — 장면 ID("S3")
     * 반환: 그런 장면이 있으면 true
     */
    fun goTo(id: String): Boolean {
        val target = scenes.indexOfFirst { it.id.equals(id, ignoreCase = true) } // 갈 장면 번호
        if (target < 0 || job?.isActive == true) return false
        job = g.scope.launch {
            g.restartTrip() // → 로직 이동: AppGraph.restartTrip()
            for (k in 0 until target) {
                enter(k)
                scenes[k].driver?.let { sayAndWait(it.text, scenes[k].expectSaid.size) }
            }
            enter(target)
        }
        return true
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: enter
     * 목적: 장면 하나에 들어간다. 화면에 장면을 띄우고 준비를 한 단계씩 넣는다.
     * 인자: i — 장면 번호
     */
    private suspend fun enter(i: Int) {
        index = i
        val scene = scenes[i] // 들어갈 장면
        Log.i(TAG, "장면,${scene.id},${scene.title}")
        view.value = SceneView(scene.id, scene.title, scene.driver?.text, busy = true, last = i == scenes.lastIndex)
        scene.prep.forEach { prep ->
            prepare(prep)
            delay(STEP_MS)
        }
        view.value = view.value?.copy(busy = false)
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: prepare
     * 목적: 준비 한 단계를 넣는다. 처음으로 돌리기 → 연료·세차일 → 장소 → 기어 →
     *       속도 순서.
     * 인자: prep — 준비 한 단계
     */
    private suspend fun prepare(prep: Prep) {
        if (prep.resetTrip) g.restartTrip() // → 로직 이동: AppGraph.restartTrip()
        prep.fuelPercent?.let(g::overrideFuel) // → 로직 이동: AppGraph.overrideFuel()
        prep.washedDaysAgo?.let { g.memory.recordWash(LocalDate.now().minusDays(it)) }
        prep.place?.let(g::setPlace) // → 로직 이동: AppGraph.setPlace()
        prep.gear?.let { gear ->
            if (g.pleos != null) VirtualConsole.put("/vehicle/gear?gear=${gear.consoleName()}")
            else g.editFakeCar { it.copy(gear = gear) }
        }
        prep.speedKmh?.let { kmh ->
            if (g.pleos != null) VirtualConsole.put("/vehicle/controls?speed=${kmh.toInt()}")
            else g.overrideSpeed(kmh)
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: sayAndWait
     * 목적: 대사를 넣고, 앱이 [replies]마디를 말할 때까지 기다린다.
     * 인자: text — 운전자 대사
     *       replies — 기다릴 앱 말 수
     */
    private suspend fun sayAndWait(text: String, replies: Int) {
        val before = g.chat.value.count { !it.fromDriver } // 넣기 전 앱이 한 말 수
        g.hear(text, Source.DEMO) // → 로직 이동: AppGraph.hear()
        withTimeoutOrNull(REPLY_WAIT_MS) {
            while (g.chat.value.count { !it.fromDriver } < before + replies) delay(300)
        } ?: Log.w(TAG, "장면 대사 답 없음: $text")
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: consoleName
     * 목적: 기어를 Virtual Console 이름으로 바꾼다.
     */
    private fun Gear.consoleName() = when (this) {
        Gear.PARK -> "Park"
        Gear.REVERSE -> "Reverse"
        Gear.NEUTRAL -> "Neutral"
        Gear.DRIVE, Gear.UNKNOWN -> "Drive"
    }
}
