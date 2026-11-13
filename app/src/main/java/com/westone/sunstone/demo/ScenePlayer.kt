package com.westone.sunstone.demo

import android.util.Log
import com.westone.sunstone.AppGraph
import com.westone.sunstone.R
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Seat
import com.westone.sunstone.routines.port.Command
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.script.Prep
import com.westone.sunstone.routines.script.Scene
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
 * 수정: 2026-11-13 13:00:00 · 박희정
 * 수정: 2026-11-13 21:00:00 · 박건준
 * 클래스: ScenePlayer
 * 목적: 대사집 장면을 실제 앱에 하나씩 넣는다(Tab 1 4.2 실행 방식 1·2).
 *       - `next()` — 부르는 카드 [다음 장면], 마이크 칸 길게 누르기, adb
 *         `scene next`(debug).
 *         다음 장면의 준비 상황을 넣고, 운전자 대사가 있으면 녹음본(앱과 다른
 *         목소리)을 낸 뒤 들은 말로 넣고 앱이 답할 때까지 기다린다
 *       - `goTo()` — adb `scene S3`. 그 시나리오 첫 장면부터 그 장면 앞까지
 *         대사를 차례로 넣어 건너뛰고, 그 장면을 준비한다. 앞 장면 ID를 주면
 *         되돌린다
 *       - `restart()` — 처음(시나리오 고르기 전)으로
 *       - `start()` — 화면에서 고른 시나리오를 첫 장면부터
 *       Pleos 차면 기어·속도·뒷문·뒷자리 탑승은 Virtual Console로, 장소·연료·
 *       세차일·벨트는 데모 입력으로 넣는다. 가짜 차면 가짜 차 값을 바꾼다. Pleos 차일 때는 콘솔 연결 신호를
 *       1초마다 보낸다.
 * 인자: g — 앱 조립부
 *       scenes — 대사집 장면들
 * 미구현: 프로토타입이라 차 상황(주유소 도착, 아이 탑승)을 스스로 만들 수
 *       없어 시연 모드를 제출(release) 빌드에도 둔다. 심사에서 설치한 APK만으로
 *       시나리오를 돌려 볼 수 있어야 한다. 위치(GPS)는 옮기지 않고 장소만
 *       바꾼다. 내비 화면의 위치는 그대로다. 실제 이동이 필요하면
 *       tools/scene-move.ps1을 쓴다
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
         * 작성: 2026-11-12 15:06:00 · 박건준
         * 변수: KEEP_ALIVE_MS
         * 목적: 콘솔 세션 신호를 보내는 간격(밀리초).
         * 근거: 측정 — 신호가 3초 넘게 끊기면 콘솔이 차량 쪽 연결을 끊는다
         *       (docs/api-spike.md Virtual Console 설명)
         */
        const val KEEP_ALIVE_MS = 1_000L

        /**
         * 작성: 2026-11-11 16:42:25 · 박희정
         * 변수: TAG
         * 목적: 데모 logcat 태그.
         */
        const val TAG = "SunstoneDemo"
    }

    /** 지금 장면 화면 값. 시작 전이면 null */
    private val view = MutableStateFlow<SceneView?>(null)

    /** 화면이 보는 지금 장면(값이 바뀌면 알려주는 상태) */
    override val current: StateFlow<SceneView?> = view

    /** 지금 장면 번호(0부터). 시작 전이면 -1 */
    private var index = -1

    /** 준비·건너뛰기 작업. 도는 중이면 새 요청을 받지 않는다 */
    private var job: Job? = null

    /** 운전자 대사 녹음본 재생기 */
    private val driver = DriverVoice(g.app)

    init {
        if (g.pleos != null) {
            g.scope.launch {
                while (isActive) {
                    VirtualConsole.keepAlive() // → 로직 이동: VirtualConsole.keepAlive()
                    delay(KEEP_ALIVE_MS)
                }
            }
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 03:50:00 · 박건준
     * 함수: next
     * 목적: 다음 장면으로 간다. 준비하고, 운전자 대사가 있으면 녹음본을 낸 뒤
     *       들은 말로 넣는다. 준비 중(대사·앱 답을 기다리는 중 포함)이면 "준비
     *       중"을 남기고 하지 않는다. 마지막 장면이면 하지 않는다.
     */
    override fun next() {
        if (preparing("next")) return
        if (index + 1 >= scenes.size) return
        job = g.scope.launch {
            enter(index + 1) // → 로직 이동: ScenePlayer.enter()
            speak(index) // → 로직 이동: ScenePlayer.speak()
            ready()
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-12 20:20:00 · 박건준
     * 함수: restart
     * 목적: 처음(S1 전)으로 돌린다. 켜진 루틴·길안내·대화 기록을 지운다.
     *       준비 중이면 "준비 중"을 남기고 하지 않는다.
     */
    override fun restart() {
        if (preparing("restart")) return
        job = g.scope.launch {
            index = -1
            view.value = null
            g.restartTrip() // → 로직 이동: AppGraph.restartTrip()
        }
    }

    /**
     * 작성: 2026-11-13 13:00:00 · 박희정
     * 함수: start
     * 목적: 고른 시나리오를 처음부터 시작한다. 그 시나리오 첫 장면(준비에
     *       `resetTrip`이 있는 장면 중 그 번째)에 들어가 준비하고, 운전자 대사가
     *       있으면 낸다. 준비 중이면 "준비 중"을 남기고 하지 않는다.
     * 인자: scenario — 시나리오 번호(1부터)
     */
    override fun start(scenario: Int) {
        if (preparing("start $scenario")) return
        val first = scenes.indices.filter { k -> scenes[k].prep.any { it.resetTrip } } // 시나리오마다 첫 장면
            .getOrNull(scenario - 1) ?: return
        job = g.scope.launch {
            enter(first) // → 로직 이동: ScenePlayer.enter()
            speak(first) // → 로직 이동: ScenePlayer.speak()
            ready()
        }
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 10:00:00 · 박희정
     * 함수: goTo
     * 목적: 그 장면으로 건너뛴다. 처음부터 다시 시작해 그 시나리오 첫 장면(준비에
     *       `resetTrip`이 있는 장면)부터 앞 장면들의 준비와 대사를 차례로 넣고
     *       (앱이 답할 때까지 기다린다) 그 장면을 준비한다. ②로 건너뛸 때 ①을
     *       다시 돌리지 않는다. 대사 뒤에
     *       이어지는 장면(주유 제안·세차 추천)의 말까지 기다린다. 건너뛴 장면의
     *       말은 그 장면에 들어가기 전에 끊어, 앞 장면 말이 뒤늦게 이어 나오지
     *       않게 한다. 그 장면에 운전자 대사가 있으면 녹음본과 함께 넣는다.
     *       건너뛰는 동안 화면 버튼은 "준비 중"이다. 준비 중이면 "준비 중"을
     *       남기고 하지 않는다.
     * 인자: id — 장면 ID("S3")
     * 반환: 그런 장면이 있으면 true(준비 중이라 하지 않았어도 true)
     */
    fun goTo(id: String): Boolean {
        val target = scenes.indexOfFirst { it.id.equals(id, ignoreCase = true) } // 갈 장면 번호
        if (target < 0) return false
        if (preparing("scene $id")) return true
        val first = (target downTo 0).firstOrNull { k -> scenes[k].prep.any { it.resetTrip } } ?: 0 // 시나리오 첫 장면
        job = g.scope.launch {
            g.restartTrip() // → 로직 이동: AppGraph.restartTrip()
            for (k in first until target) {
                enter(k)
                view.value = view.value?.copy(busy = true) // 건너뛰는 동안은 계속 준비 중
                scenes[k].driver?.let { sayAndWait(it.text, replies(k)) }
            }
            g.stopVoice() // → 로직 이동: AppGraph.stopVoice()
            enter(target)
            speak(target)
            ready()
        }
        return true
    }

    /**
     * 작성: 2026-11-12 20:20:00 · 박건준
     * 함수: preparing
     * 목적: 장면 준비·건너뛰기가 도는 중인지 보고, 그렇다면 받은 명령을
     *       "준비 중"으로 logcat에 남긴다. 화면 버튼은 그동안 "준비 중"으로
     *       막혀 있고, adb `scene`은 여기서 걸러진다.
     * 인자: what — 받은 명령(로그에 쓴다)
     * 반환: 준비 중이면 true(명령을 하지 않는다)
     */
    private fun preparing(what: String): Boolean {
        if (job?.isActive != true) return false
        Log.i(TAG, "준비 중이라 하지 않음: $what")
        return true
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: enter
     * 목적: 장면 하나에 들어간다. 앞 장면 토스트를 지우고, 화면에 장면을 띄우고
     *       준비를 한 단계씩 넣는다. 다음 장면이 새 시나리오(`resetTrip`)면 이
     *       장면을 그 시나리오의 마지막으로 보인다. 화면 버튼은 부르는 쪽이
     *       `ready()`로 풀 때까지 "준비 중"이다. 시나리오 이름과 그 안에서 몇
     *       번째 장면인지도 함께 보인다.
     * 인자: i — 장면 번호
     */
    private suspend fun enter(i: Int) {
        index = i
        val scene = scenes[i] // 들어갈 장면
        Log.i(TAG, "장면,${scene.id},${scene.title}")
        g.panels.clearToast() // → 로직 이동: PanelPresenter.clearToast()
        val starts = scenes.indices.filter { k -> scenes[k].prep.any { it.resetTrip } } // 시나리오마다 첫 장면
        val no = starts.count { it <= i } // 시나리오 번호(1부터)
        val first = starts.getOrElse(no - 1) { 0 } // 이 시나리오 첫 장면 번호
        val end = starts.getOrElse(no) { scenes.size } // 다음 시나리오 첫 장면 번호
        val names = g.app.resources.getStringArray(R.array.scene_scenarios) // 시나리오 이름들
        view.value = SceneView(
            scene.id, scene.title, busy = true, last = i == end - 1,
            scenario = names.getOrElse(no - 1) { "" }, step = i - first + 1, steps = end - first,
        )
        scene.prep.forEach { prep ->
            prepare(prep)
            delay(STEP_MS)
        }
    }

    /**
     * 작성: 2026-11-13 03:50:00 · 박건준
     * 함수: speak
     * 목적: 장면의 운전자 대사를 낸다. 듣는 중 판을 띄우고, 녹음본(앱과 다른
     *       목소리)을 끝까지 낸 뒤 그 말을 들은 말로 넣고(logcat "대사,…") 앱이
     *       답할 때까지 기다린다. 대사가 없는 장면이면 하지 않는다.
     * 인자: i — 장면 번호
     * 근거: 인용 — 2026-11-13 박건준 2차 업그레이드 지시: 운전자 대사 녹음본을
     *       먼저 재생한 뒤 들음으로
     */
    private suspend fun speak(i: Int) {
        val line = scenes[i].driver ?: return // 운전자 대사
        g.panels.tapMic() // → 로직 이동: PanelPresenter.tapMic()
        driver.play(line.text) // → 로직 이동: DriverVoice.play()
        Log.i(TAG, "대사,${line.text}")
        sayAndWait(line.text, replies(i))
    }

    /**
     * 작성: 2026-11-13 03:50:00 · 박건준
     * 함수: ready
     * 목적: 장면 준비가 끝났다고 화면 버튼을 푼다.
     */
    private fun ready() {
        view.value = view.value?.copy(busy = false)
    }

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 15:00:00 · 박희정
     * 함수: prepare
     * 목적: 준비 한 단계를 넣는다. 처음으로 돌리기 → 연료·세차일 → 장소 → 기어 →
     *       속도 → 뒷문 → 뒷자리 탑승 → 벨트 → 운전석 시작 자세 순서. 뒷자리는
     *       왼쪽 자리다. 세차일·차일드락 날·미리 저장할 자세는 기억에 넣는다.
     * 인자: prep — 준비 한 단계
     * 근거: 측정 — 콘솔 `PUT /vehicle/body?door_rl=`이 앱 `doorOpen`에 바로
     *       반영된다(docs/api-spike.md 문 열기 행). `PUT /vehicle/seat?kind=
     *       occupied&area=rear_left&value=`는 콘솔 `seat.occupied.rear_left`를
     *       바꾼다(뒷좌석 탑승 행)
     * 미구현: 프로토타입이라 벨트는 데모 입력으로 덮어쓴다. 에뮬레이터 SDK 벨트
     *       값이 늘 false다(`AppGraph.overrideBelt()` 참고)
     */
    private suspend fun prepare(prep: Prep) {
        if (prep.resetTrip) g.restartTrip() // → 로직 이동: AppGraph.restartTrip()
        prep.fuelPercent?.let(g::overrideFuel) // → 로직 이동: AppGraph.overrideFuel()
        prep.washedDaysAgo?.let { g.memory.recordWash(LocalDate.now().minusDays(it)) }
        prep.childLockDaysAgo?.let { g.memory.recordChildLock(LocalDate.now().minusDays(it)) }
        prep.seatProfile?.let(g.profiles::save)
        prep.place?.let(g::setPlace) // → 로직 이동: AppGraph.setPlace()
        prep.gear?.let { gear ->
            if (g.pleos != null) VirtualConsole.put("/vehicle/gear?gear=${gear.consoleName()}")
            else g.editFakeCar { it.copy(gear = gear) }
        }
        prep.speedKmh?.let { kmh ->
            if (g.pleos != null) VirtualConsole.put("/vehicle/controls?speed=${kmh.toInt()}")
            else g.overrideSpeed(kmh)
        }
        prep.rearDoorOpen?.let { open ->
            if (g.pleos != null) VirtualConsole.put("/vehicle/body?door_rl=$open")
            else g.editFakeCar { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to open)) }
        }
        prep.rearOccupied?.let { on ->
            if (g.pleos != null) VirtualConsole.put("/vehicle/seat?kind=occupied&area=rear_left&value=$on")
            else g.editFakeCar { it.copy(seatOccupied = it.seatOccupied + (Seat.REAR_LEFT to on)) }
        }
        prep.rearBelt?.let { g.overrideBelt(Seat.REAR_LEFT, it) } // → 로직 이동: AppGraph.overrideBelt()
        // 시트는 P단에서만 움직이므로 기어를 넣은 뒤 마지막에 옮긴다
        prep.driverSeat?.forEach { (axis, at) ->
            g.car.apply(Command(Target.SeatPosition(Seat.DRIVER, axis), at)) // → 로직 이동: CarPort.apply()
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
     * 작성: 2026-11-12 19:14:00 · 박건준
     * 함수: replies
     * 목적: 장면 대사 하나에 앱이 할 말 수. 뒤에 이어지는 장면(`Scene.follows`)의
     *       말도 같은 대사가 낳으므로 더한다.
     * 인자: k — 대사가 있는 장면 번호
     * 반환: 기다릴 앱 말 수
     */
    private fun replies(k: Int): Int {
        val follow = scenes.drop(k + 1).takeWhile { it.follows } // 이어지는 장면들
        return scenes[k].expectSaid.size + follow.sumOf { it.expectSaid.size }
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
