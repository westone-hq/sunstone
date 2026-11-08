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
            else -> Log.w("SunstoneDemo", "모르는 명령: $cmd")
        }
    }
}
