package com.westone.sunstone.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.graph
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.model.Door
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.model.Seat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 시연용 장면 주입. 에뮬레이터가 못 만드는 상황(장소, 속도, 마이크 입력)을 adb 한 줄로 넣는다.
 *
 * adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "place carwash"
 *
 * place carwash|gas|kids|none   장소
 * speed 30 | speed off          속도 덮어쓰기
 * say 세차할게                    말 한마디(음성 인식 대신)
 * accept | dismiss              권하기에 답하기
 * fake on|off                   다음 실행부터 가짜 차 사용
 * --- 가짜 차일 때만(Pleos는 Virtual Console 사용) ---
 * gear p|n|d
 * rear_door open|close          뒷좌석 왼쪽 문
 * rear_seat on|off              뒷좌석 왼쪽 탑승
 * rear_belt on|off              뒷좌석 왼쪽 벨트
 * driver_seat on|off            운전석 탑승
 * --- Pleos 차일 때만 ---
 * probe                         제어 API 실측, 결과는 logcat SunstoneCar `probe,` 줄
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")?.trim().orEmpty()
        val word = cmd.substringBefore(' ')
        val arg = cmd.substringAfter(' ', "").trim()
        val g = context.graph
        Log.i("SunstoneDemo", "cmd=$cmd")
        when (word) {
            "place" -> g.setPlace(
                when (arg) {
                    "carwash" -> Place.CAR_WASH
                    "gas" -> Place.GAS_STATION
                    "kids" -> Place.KIDS
                    else -> Place.NONE
                },
            )
            "speed" -> g.overrideSpeed(arg.toFloatOrNull())
            "say" -> PhraseMatcher.match(arg, Source.DEMO)?.let(g::handle) ?: g.speaker.say("잘 못 알아들었어요.")
            "accept" -> g.runner.acceptSuggestion()
            "dismiss" -> g.runner.dismissSuggestion()
            "fake" -> g.setUseFakeCar(arg == "on")
            "gear" -> g.editFakeCar {
                it.copy(gear = when (arg) { "p" -> Gear.PARK; "n" -> Gear.NEUTRAL; "r" -> Gear.REVERSE; else -> Gear.DRIVE })
            }
            "rear_door" -> g.editFakeCar { it.copy(doorOpen = it.doorOpen + (Door.REAR_LEFT to (arg == "open"))) }
            "rear_seat" -> g.editFakeCar { it.copy(seatOccupied = it.seatOccupied + (Seat.REAR_LEFT to (arg == "on"))) }
            "rear_belt" -> g.editFakeCar { it.copy(beltBuckled = it.beltBuckled + (Seat.REAR_LEFT to (arg == "on"))) }
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
            else -> Log.w("SunstoneDemo", "모르는 명령: $cmd")
        }
    }

    private companion object {
        /** 돌고 있는 실측 작업. 수신기는 방송마다 새로 만들어져서 여기에 둔다. 처음엔 null */
        var probeJob: Job? = null
    }
}
