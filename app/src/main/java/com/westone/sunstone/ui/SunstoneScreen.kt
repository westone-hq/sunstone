package com.westone.sunstone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westone.sunstone.AppGraph
import com.westone.sunstone.routines.understand.PhraseMatcher
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.RunView
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.engine.StepStatus
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import kotlinx.coroutines.delay

/**
 * 화면은 "보조"다. 주 입력은 말 한마디와 상황 감지이고, 화면은 지금 무엇을 했는지 보여준다.
 * 주행 중에는 상태 한 줄과 마지막 안내만 보인다(Pleos 주행 중 화면 제한).
 */
@Composable
fun SunstoneScreen(graph: AppGraph) {
    val car by graph.car.state.collectAsStateWithLifecycle()
    val ui by graph.runner.ui.collectAsStateWithLifecycle()
    val driving = !car.stopped || car.gear == Gear.DRIVE || car.gear == Gear.REVERSE

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF101114))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusLine(car, graph.usingFakeCar || graph.pleos == null)
            ui.lastMessage?.let { Text(it, color = Color(0xFFB8C4FF), fontSize = 18.sp) }

            ui.suggestion?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.message, Modifier.weight(1f), fontSize = 20.sp)
                        Button(onClick = { graph.runner.acceptSuggestion() }) { Text("네") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { graph.runner.dismissSuggestion() }) { Text("아니요") }
                    }
                }
            }

            if (driving) {
                ui.runs.values.filter { it.phase == Phase.ACTIVE }.forEach { Text("● ${it.title} 켜짐", fontSize = 18.sp) }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                graph.runner.available.forEach { routine ->
                    RoutineCard(
                        modifier = Modifier.weight(1f),
                        title = routine.title,
                        run = ui.runs[routine.id],
                        onRun = { graph.handle(VoiceAction.Run(RunRequest(routine.id, source = Source.SCREEN))) },
                        onUndo = { graph.runner.undo(routine.id) },
                    )
                }
            }

            ProfileRow(graph)
            ui.runs.values.maxByOrNull { it.startedAtMs }?.let { RunDetail(it) }
            SayBox(graph)
        }
    }
}

@Composable
private fun StatusLine(car: CarState, fake: Boolean) {
    val doors = car.doorOpen.filterValues { it }.keys.joinToString { it.label }.ifEmpty { "닫힘" }
    val seats = car.seatOccupied.filterValues { it }.keys.joinToString { it.label }.ifEmpty { "-" }
    Text(
        "기어 ${car.gear} · ${"%.0f".format(car.speedKmh)}km/h · 장소 ${car.place} · 문 $doors · 탑승 $seats" +
            if (fake) " · 가짜 차" else "",
        color = Color(0xFF9AA0A6),
        fontSize = 14.sp,
    )
}

@Composable
private fun RoutineCard(modifier: Modifier, title: String, run: RunView?, onRun: () -> Unit, onUndo: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (run?.phase) {
                    Phase.RUNNING -> "실행 중"
                    Phase.ACTIVE -> "켜짐"
                    Phase.UNDOING -> "되돌리는 중"
                    else -> "대기"
                },
                color = Color(0xFF9AA0A6),
            )
            if (run?.phase == Phase.ACTIVE) {
                OutlinedButton(onClick = onUndo, Modifier.fillMaxWidth()) { Text("원래대로") }
            } else {
                Button(onClick = onRun, Modifier.fillMaxWidth(), enabled = run?.phase != Phase.RUNNING) { Text("실행") }
            }
        }
    }
}

@Composable
private fun ProfileRow(graph: AppGraph) {
    val names = remember(graph.runner.ui.collectAsStateWithLifecycle().value.lastMessage) { graph.profiles.names() }
    if (names.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("자세:", color = Color(0xFF9AA0A6))
        names.forEach { name ->
            OutlinedButton(onClick = {
                graph.handle(VoiceAction.Run(RunRequest(RoutineId.SEAT_MEMORY, name, Source.SCREEN)))
            }) { Text(name) }
        }
    }
}

@Composable
private fun RunDetail(run: RunView) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = (now - run.startedAtMs).coerceAtLeast(0) / 1000
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${run.title} · %d:%02d".format(elapsed / 60, elapsed % 60), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            run.steps.forEach { step ->
                val mark = when (step.status) {
                    StepStatus.DONE -> "완료"
                    StepStatus.GUIDED -> "안내"
                    StepStatus.RUNNING -> "진행"
                    StepStatus.SKIPPED -> "건너뜀"
                    StepStatus.WAITING -> "대기"
                }
                Text("$mark ${step.title}" + (step.note?.let { "  ($it)" } ?: ""), fontSize = 16.sp)
            }
        }
    }
}

/** 음성 인식 대신 쓰는 입력칸. 에뮬레이터에 마이크가 없어서 시연은 여기나 adb로 말을 넣는다. */
@Composable
private fun SayBox(graph: AppGraph) {
    var text by remember { mutableStateOf("") }
    val quick = listOf("세차할게", "세차 끝났어", "애 태웠어", "지금 자세 엄마로 저장해줘", "엄마 자세로", "원래대로")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text("말 한마디") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { say(graph, text); text = "" }) { Text("말하기") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { q -> OutlinedButton(onClick = { say(graph, q) }) { Text(q, fontSize = 13.sp) } }
        }
    }
}

private fun say(graph: AppGraph, text: String) {
    val action = PhraseMatcher.match(text, Source.VOICE)
    if (action == null) graph.speaker.say("잘 못 알아들었어요.") else graph.handle(action)
}
