package com.westone.sunstone.demo

import ai.pleos.playground.vehicle.Vehicle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.westone.sunstone.graph
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-11 12:30:00 · 박건준
 * 클래스: DemoReceiver
 * 목적: 시연용 장면 넣기. 에뮬레이터가 못 만드는 상황(장소, 속도, 마이크
 *       입력)을 adb 한 줄로 넣는다. debug 빌드에만 있다.
 *       `adb shell am broadcast -a com.westone.sunstone.DEMO
 *       -p com.westone.sunstone --es cmd "place carwash"`
 *       - `place carwash|gas|kids|none` — 장소(예비. 평소엔 근접 알림이 바꾼다)
 *       - `speed 30` | `speed off` — 속도 덮어쓰기
 *       - `fuel 15` | `fuel off` — 연료(%) 덮어쓰기(Pleos 차만)
 *       - `say 세차할게` — 말 한마디(음성 인식 대신)
 *       - `accept` | `dismiss` — 권하기에 답하기
 *       - `fake on|off` — 다음 실행부터 가짜 차 사용
 *       - 가짜 차일 때만(Pleos는 Virtual Console 사용): `gear p|n|r|d`,
 *         `rear_door open|close`, `rear_seat on|off`, `rear_belt on|off`,
 *         `driver_seat on|off`
 *       - Pleos 차일 때만: `probe`(제어 API 실측, logcat `probe,` 줄),
 *         `areas`(지원 area 목록, logcat `areas,` 줄),
 *         `seatread`(AAOS 속성으로 시트 위치 읽기, logcat `seatread,` 줄),
 *         `seattime [passenger]`(시트 축 이동 시간, logcat `seattime` 줄),
 *         `seatfit [passenger]`(시간 기반 시트 이동 오차, logcat `seatfit,` 줄)
 *       - `navi` | `navi start` | `navi cancel` — 내비 실측(logcat `navi,` 줄),
 *         시연 안내 시작, 안내 취소
 *       - `location` — 위치 실측(FusedLocation 위치·근접 알림, logcat
 *         `location,` 줄)
 *       - `belt 좌석 on|off` | `belt clear` — 벨트 덮어쓰기(Pleos·가짜 차)
 *       - `beltprobe` — 벨트 실측(좌석별 착용·미착용 경고·경고등, logcat
 *         `belt,` 줄)
 *       - `childlock` — 뒷문 차일드락 실측(AAOS 속성, logcat `childlock,` 줄)
 * 미구현: 프로토타입이라 에뮬레이터가 못 만드는 상황(연료, 콘솔이 없을 때 속도,
 *       마이크 입력, 좌석별 벨트, 예비 장소)을 이 명령으로 넣는다. debug
 *       빌드에만 있어 실제 차 빌드에는 들어가지 않는다
 */
class DemoReceiver : BroadcastReceiver() {

    private companion object {
        /**
         * 작성: 2026-11-09 18:34:38 · 박건준
         * 수정: 2026-11-09 18:53:00 · 박건준
         * 변수: AREAS_CONNECT_MS
         * 목적: `areas`·`seattime`이 차 서비스에 연결되기를 기다리는 시간(밀리초).
         * 근거: 미정 — 에뮬레이터에서 넉넉히 잡은 값
         */
        const val AREAS_CONNECT_MS = 1_000L

        /** 돌고 있는 실측 작업. 수신기는 방송마다 새로 만들어져서 여기에 둔다. 처음엔 null */
        var probeJob: Job? = null
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: onReceive
     * 목적: 방송의 `cmd` 엑스트라를 명령어와 인자로 나눠 처리한다. 모르는
     *       명령은 logcat에 남긴다.
     * 인자: context — 방송을 받은 Context
     *       intent — `cmd` 엑스트라를 가진 방송
     */
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")?.trim().orEmpty() // 명령 한 줄
        val word = cmd.substringBefore(' ') // 명령어
        val arg = cmd.substringAfter(' ', "").trim() // 인자, 없으면 빈 글자
        val g = context.graph // 앱 조립부
        Log.i("SunstoneDemo", "cmd=$cmd")
        when (word) {
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: place
             * 목적: 지금 장소를 넣는다. 모르는 인자는 장소 없음으로 본다.
             */
            // → 로직 이동: AppGraph.setPlace()
            "place" -> g.setPlace(
                when (arg) {
                    "carwash" -> Place.CAR_WASH
                    "gas" -> Place.GAS_STATION
                    "kids" -> Place.KIDS
                    else -> Place.NONE
                },
            )
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: speed
             * 목적: 속도를 덮어쓴다(km/h). 숫자가 아니면 센서 값으로 돌아간다.
             */
            "speed" -> g.overrideSpeed(arg.toFloatOrNull()) // → 로직 이동: AppGraph.overrideSpeed()
            /**
             * 작성: 2026-11-09 20:18:22 · 박건준
             * 함수: fuel
             * 목적: 연료(%)를 덮어쓴다. 0~100 밖은 끝으로 자르고, 숫자가 아니면
             *       (`fuel off`) 차 값으로 돌아간다. Pleos 차에만 들어간다.
             */
            "fuel" -> g.overrideFuel(arg.toIntOrNull()?.coerceIn(0, 100)) // → 로직 이동: AppGraph.overrideFuel()
            /**
             * 작성: 2026-11-11 16:42:25 · 박희정
             * 함수: scene
             * 목적: 대사집 장면으로 건너뛰거나 되돌린다(Tab 1 4.2 실행 방식 2).
             *       - `scene S3` — 처음부터 S3 앞까지 대사를 넣고 S3를 준비한다
             *       - `scene next` — 다음 장면(화면 [다음 장면]과 같다)
             *       - `scene restart` — 처음(S1 전)으로
             */
            "scene" -> {
                val player = g.scenes as? ScenePlayer // 장면 실행기
                when {
                    player == null -> Log.w("SunstoneDemo", "scene: 시연 모드 없음")
                    arg == "next" -> player.next() // → 로직 이동: ScenePlayer.next()
                    arg == "restart" -> player.restart() // → 로직 이동: ScenePlayer.restart()
                    // → 로직 이동: ScenePlayer.goTo()
                    !player.goTo(arg) -> Log.w("SunstoneDemo", "scene: 모르는 장면이거나 진행 중: $arg")
                }
            }
            /**
             * 작성: 2026-11-11 15:14:38 · 박희정
             * 함수: washed
             * 목적: 마지막 세차일을 오늘에서 N일 전으로 넣는다(`washed 36`).
             *       시나리오 ① 준비 "마지막 세차 36일 전"을 만든다. 숫자가
             *       아니면 하지 않는다.
             * 미구현: 프로토타입이라 실제 세차 기록 대신 데모로 넣는다
             */
            "washed" -> arg.toLongOrNull()?.let { days ->
                g.memory.recordWash(LocalDate.now().minusDays(days)) // → 로직 이동: MemoryPort.recordWash()
            }
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-11 16:02:00 · 박희정
             * 함수: say
             * 목적: 말 한마디를 음성 인식 대신 넣는다. 화면 대화 기록에도 남고,
             *       못 알아들으면 그렇다고 말한다.
             */
            "say" -> g.hear(arg, Source.DEMO) // → 로직 이동: AppGraph.hear()
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: accept
             * 목적: 지금 권하기에 "네"로 답한다.
             */
            "accept" -> g.runner.acceptSuggestion() // → 로직 이동: RoutineRunner.acceptSuggestion()
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: dismiss
             * 목적: 지금 권하기에 "아니요"로 답한다.
             */
            "dismiss" -> g.runner.dismissSuggestion() // → 로직 이동: RoutineRunner.dismissSuggestion()
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: fake
             * 목적: 다음 실행부터 가짜 차를 쓸지 정한다. `on`이 아니면 Pleos.
             */
            "fake" -> g.setUseFakeCar(arg == "on") // → 로직 이동: AppGraph.setUseFakeCar()
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: gear
             * 목적: 가짜 차의 기어를 바꾼다. 모르는 인자는 D로 본다.
             */
            // → 로직 이동: AppGraph.editFakeCar()
            "gear" -> g.editFakeCar {
                val gear = when (arg) { // 바꿀 기어
                    "p" -> Gear.PARK
                    "n" -> Gear.NEUTRAL
                    "r" -> Gear.REVERSE
                    else -> Gear.DRIVE
                }
                it.copy(gear = gear)
            }
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: rear_door
             * 목적: 가짜 차의 뒷좌석 왼쪽 문을 열거나 닫는다.
             */
            // → 로직 이동: AppGraph.editFakeCar()
            "rear_door" -> g.editFakeCar { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to (arg == "open"))) }
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: rear_seat
             * 목적: 가짜 차의 뒷좌석 왼쪽 탑승을 바꾼다.
             */
            // → 로직 이동: AppGraph.editFakeCar()
            "rear_seat" -> g.editFakeCar { it.copy(seatOccupied = it.seatOccupied + (Seat.REAR_LEFT to (arg == "on"))) }
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: rear_belt
             * 목적: 가짜 차의 뒷좌석 왼쪽 벨트를 바꾼다.
             */
            // → 로직 이동: AppGraph.editFakeCar()
            "rear_belt" -> g.editFakeCar { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to (arg == "on"))) }
            /**
             * 작성: 2026-11-08 18:03:56 · 박건준
             * 수정: 2026-11-09 18:49:00 · 박건준
             * 함수: driver_seat
             * 목적: 가짜 차의 운전석 탑승을 바꾼다.
             */
            // → 로직 이동: AppGraph.editFakeCar()
            "driver_seat" -> g.editFakeCar { it.copy(seatOccupied = it.seatOccupied + (Seat.DRIVER to (arg == "on"))) }
            /**
             * 작성: 2026-11-09 16:52:02 · 박건준
             * 함수: probe
             * 목적: 제어 API 실측(`CarProbe`)을 앱 범위에서 돌린다. 몇 분 걸려서
             *       수신기 안에서 기다리지 않는다. 이미 돌고 있거나 Pleos 차가
             *       아니면(가짜 차) 시작하지 않는다.
             */
            "probe" -> {
                val pleos = g.pleos // 실측할 차, 가짜 차를 쓰는 중이면 null
                when {
                    pleos == null -> Log.w("SunstoneDemo", "probe: Pleos 차가 아니라서 실측하지 않음")
                    probeJob?.isActive == true -> Log.w("SunstoneDemo", "probe: 이미 실측 중")
                    // → 로직 이동: CarProbe.run()
                    else -> probeJob = g.scope.launch { CarProbe(pleos).run() }
                }
            }
            /**
             * 작성: 2026-11-09 18:34:38 · 박건준
             * 함수: areas
             * 목적: 차가 받는 area 목록(`CarAreas`)을 읽는다. 루틴이 쓰는 차
             *       창구와 따로 `Vehicle`을 만들고, 끝나면 놓는다.
             * 근거: 미확인 — `Vehicle.initialize()`가 서비스 연결을 기다리지
             *       않고 돌아와서, 연결될 시간을 1초 둔다
             */
            "areas" -> g.scope.launch {
                val vehicle = Vehicle(context.applicationContext).also { it.initialize() } // 확인용 차 연결
                delay(AREAS_CONNECT_MS)
                try {
                    CarAreas(vehicle).run() // → 로직 이동: CarAreas.run()
                } finally {
                    vehicle.release()
                }
            }
            /**
             * 작성: 2026-11-09 18:51:00 · 박건준
             * 함수: seatread
             * 목적: 시트 위치를 `CarPropertyManager`로 읽어 본다(`CarSeatRead`).
             */
            "seatread" -> CarSeatRead(context).run() // → 로직 이동: CarSeatRead.run()
            /**
             * 작성: 2026-11-09 18:53:00 · 박건준
             * 수정: 2026-11-11 11:50:00 · 박건준
             * 함수: seattime
             * 목적: 운전석 시트 축마다 0→100, 100→0 시간을 잰다(`SeatTimer`).
             *       몇 분 걸려서 앱 범위에서 돌리고, 따로 만든 `Vehicle`은
             *       끝나면 놓는다. `seattime passenger`면 조수석을 잰다.
             */
            "seattime" -> g.scope.launch {
                val vehicle = Vehicle(context.applicationContext).also { it.initialize() } // 실측용 차 연결
                delay(AREAS_CONNECT_MS)
                try {
                    SeatTimer(vehicle, arg == "passenger").run() // → 로직 이동: SeatTimer.run()
                } finally {
                    vehicle.release()
                }
            }
            /**
             * 작성: 2026-11-09 20:40:00 · 박건준
             * 함수: seatcheck
             * 목적: SDK 시트 위치 읽기를 지원 목록·지원 area·리스너로 다시
             *       확인한다(`SeatReadCheck`). 따로 만든 `Vehicle`은 끝나면 놓는다.
             */
            "seatcheck" -> g.scope.launch {
                val vehicle = Vehicle(context.applicationContext).also { it.initialize() } // 실측용 차 연결
                delay(AREAS_CONNECT_MS)
                try {
                    SeatReadCheck(vehicle).run() // → 로직 이동: SeatReadCheck.run()
                } finally {
                    vehicle.release()
                }
            }
            /**
             * 작성: 2026-11-09 19:14:00 · 박건준
             * 수정: 2026-11-11 11:58:00 · 박건준
             * 함수: seatfit
             * 목적: 시간 기반 시트 이동이 목표 30·70에 얼마나 가깝게 가는지
             *       잰다(`SeatFit`). Pleos 차가 아니면 하지 않는다.
             *       `seatfit passenger`면 조수석을 잰다.
             */
            "seatfit" -> {
                val pleos = g.pleos // 실측할 차, 가짜 차를 쓰는 중이면 null
                if (pleos == null) Log.w("SunstoneDemo", "seatfit: Pleos 차가 아니라서 실측하지 않음")
                else g.scope.launch { SeatFit(pleos, arg == "passenger").run() } // → 로직 이동: SeatFit.run()
            }
            /**
             * 작성: 2026-11-10 14:16:00 · 박건준
             * 수정: 2026-11-11 12:30:00 · 박건준
             * 함수: navi
             * 목적: 내비 창구(`PleosNavi`)를 부른다. Pleos 차가 아니면 하지 않는다.
             *       - `navi` — 단계별 실측(`NaviProbe`), 1분 30초쯤. 이미 돌고 있으면
             *         시작하지 않는다
             *       - `navi start` — 해운대 안내 시작 + 시연 주유소 경유지
             *       - `navi cancel` — 안내 취소
             */
            "navi" -> {
                val navi = g.navi // 내비 창구, 가짜 차면 null
                val places = g.places // 시연 장소 목록, 가짜 차면 null
                when {
                    navi == null || places == null -> Log.w("SunstoneDemo", "navi: Pleos 차가 아니라서 하지 않음")
                    // → 로직 이동: NaviProbe.startDemo()
                    arg == "start" -> g.scope.launch { NaviProbe.startDemo(navi, places) }
                    arg == "cancel" -> g.scope.launch { navi.cancelRoute() } // → 로직 이동: PleosNavi.cancelRoute()
                    probeJob?.isActive == true -> Log.w("SunstoneDemo", "navi: 이미 실측 중")
                    // → 로직 이동: NaviProbe.run()
                    else -> probeJob = g.scope.launch { NaviProbe(navi, places).run() }
                }
            }
            /**
             * 작성: 2026-11-10 14:35:00 · 박건준
             * 함수: location
             * 목적: 위치 실측(`LocationProbe`)을 앱 범위에서 돌린다. 1분쯤 걸린다.
             */
            "location" -> g.scope.launch { LocationProbe(context).run() } // → 로직 이동: LocationProbe.run()
            /**
             * 작성: 2026-11-11 12:30:00 · 박건준
             * 함수: belt
             * 목적: 좌석 하나의 벨트를 덮어쓴다. `belt 좌석 on|off`, 좌석은
             *       `driver`·`passenger`·`rear_left`·`rear_right`. `belt clear`면 모두
             *       차 값으로 돌아간다. Pleos 차와 가짜 차 모두 들어간다.
             * 미구현: 프로토타입이라 에뮬레이터에 좌석별 벨트 값이 없어 데모로 넣는다
             */
            "belt" -> {
                val (who, how) = arg.substringBefore(' ') to arg.substringAfter(' ', "") // 좌석, on|off
                val seat = when (who) { // 덮어쓸 좌석, 모르면 null
                    "driver" -> Seat.DRIVER
                    "passenger" -> Seat.PASSENGER
                    "rear_left" -> Seat.REAR_LEFT
                    "rear_right" -> Seat.REAR_RIGHT
                    else -> null
                }
                when {
                    // → 로직 이동: AppGraph.overrideBelt()
                    who == "clear" -> Seat.entries.forEach { g.overrideBelt(it, null) }
                    seat == null || how !in setOf("on", "off") -> Log.w("SunstoneDemo", "모르는 명령: $cmd")
                    else -> g.overrideBelt(seat, how == "on")
                }
            }
            /**
             * 작성: 2026-11-10 14:40:00 · 박건준
             * 수정: 2026-11-11 12:30:00 · 박건준
             * 함수: beltprobe
             * 목적: 벨트 실측(`BeltProbe`)을 앱 범위에서 돌린다. 따로 만든
             *       `Vehicle`은 끝나면 놓는다. 이전 이름은 `belt`.
             */
            "beltprobe" -> g.scope.launch {
                val vehicle = Vehicle(context.applicationContext).also { it.initialize() } // 실측용 차 연결
                delay(AREAS_CONNECT_MS)
                try {
                    BeltProbe(vehicle).run() // → 로직 이동: BeltProbe.run()
                } finally {
                    vehicle.release()
                }
            }
            /**
             * 작성: 2026-11-11 11:52:00 · 박건준
             * 함수: childlock
             * 목적: 뒷문 차일드락을 AAOS 속성으로 읽고 써 본다(`ChildLockProbe`).
             */
            "childlock" -> ChildLockProbe(context).run() // → 로직 이동: ChildLockProbe.run()
            /**
             * 작성: 2026-11-12 18:05:00 · 박건준
             * 함수: overlay
             * 목적: 왼쪽 구역 오버레이 창과 키 수신을 실측한다(`OverlayProbe`).
             *       `overlay show` | `overlay hide`.
             */
            // → 로직 이동: OverlayProbe.show(), OverlayProbe.hide()
            "overlay" -> OverlayProbe(context).run { if (arg == "hide") hide() else show() }
            else -> Log.w("SunstoneDemo", "모르는 명령: $cmd")
        }
    }
}
