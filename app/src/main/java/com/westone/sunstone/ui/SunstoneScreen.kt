package com.westone.sunstone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westone.sunstone.AppGraph
import com.westone.sunstone.ChatLine
import com.westone.sunstone.R
import com.westone.sunstone.car.FuelState
import com.westone.sunstone.routines.understand.VoiceAction
import com.westone.sunstone.routines.engine.Phase
import com.westone.sunstone.routines.engine.RoutineId
import com.westone.sunstone.routines.engine.RunRequest
import com.westone.sunstone.routines.engine.RunView
import com.westone.sunstone.routines.engine.Source
import com.westone.sunstone.routines.engine.StepStatus
import com.westone.sunstone.routines.model.CarState
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.ui.panel.LeftZone
import com.westone.sunstone.ui.panel.PanelToast
import com.westone.sunstone.ui.panel.zoneFull
import kotlinx.coroutines.delay

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-12 19:10:00 · 박건준
 * 함수: SunstoneScreen
 * 목적: 앱 화면(오른쪽 앱 영역) 전체. 시연을 돌리고 지켜보는 화면이다. 운전자가
 *       보는 패널은 왼쪽 구역(`LeftZoneOverlay`)에 뜬다. 문구는
 *       res/values/strings.xml에 있다.
 *       - 늘 보임 — 상태 줄, 시연 모드 줄, 대화 기록, 말 입력칸, 권하기 카드
 *       - 정차 중에만 보임 — 루틴 카드, 저장한 자세, 실행 기록
 *       - 왼쪽 구역 창을 띄우지 못하면 같은 패널을 이 화면 왼쪽 위에 겹쳐 그린다
 * 인자: graph — 앱 조립부
 * 근거: 인용 — Pleos 주행 중 화면 제한. 주행 중에는 켜진 루틴 이름만 둔다
 * 미구현: 프로토타입이라 음성 인식 대신 쓰는 말 입력칸을 주행 중에도 보인다
 *       (시나리오가 주행 중 장면을 오가며 시험하려고). 음성 인식이 붙으면
 *       주행 중에는 숨긴다
 */
@Composable
fun SunstoneScreen(graph: AppGraph) {
    val car by graph.car.state.collectAsStateWithLifecycle() // 지금 차 상태
    val ui by graph.runner.ui.collectAsStateWithLifecycle() // 루틴 실행기 화면 상태
    val driving = !car.stopped || car.gear == Gear.DRIVE || car.gear == Gear.REVERSE // 주행 중 화면 제한 여부
    val fuel = graph.pleos?.fuel?.collectAsStateWithLifecycle()?.value // 연료 값, 가짜 차면 null
    val chat by graph.chat.collectAsStateWithLifecycle() // 대화 기록
    val overlay by graph.overlayShown.collectAsStateWithLifecycle() // 왼쪽 구역 창이 떠 있는지

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                // 화면 전체: 상태 줄, 권하기 카드, 루틴 카드, 입력칸 순서. 길면 세로로 넘긴다
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101114))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusLine(car, fuel, graph.usingFakeCar || graph.pleos == null)
                graph.scenes?.let { SceneBar(graph, it) }
                ChatLog(chat)
                SayBox(graph)

                ui.suggestion?.let { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            // 권하기 문장과 네/아니요
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.message, Modifier.weight(1f), fontSize = 20.sp)
                            // → 로직 이동: RoutineRunner.acceptSuggestion()
                            Button(onClick = { graph.runner.acceptSuggestion() }) {
                                Text(stringResource(R.string.suggestion_yes))
                            }
                            Spacer(Modifier.width(8.dp))
                            // → 로직 이동: RoutineRunner.dismissSuggestion()
                            OutlinedButton(onClick = { graph.runner.dismissSuggestion() }) {
                                Text(stringResource(R.string.suggestion_no))
                            }
                        }
                    }
                }

                if (driving) {
                    ui.runs.values.filter { it.phase == Phase.ACTIVE }.forEach {
                        Text(stringResource(R.string.routine_active_while_driving, it.title), fontSize = 18.sp)
                    }
                    return@Column
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    graph.runner.available.forEach { routine ->
                        RoutineCard(
                            modifier = Modifier.weight(1f),
                            title = routine.title,
                            run = ui.runs[routine.id],
                            // → 로직 이동: AppGraph.handle()
                            onRun = { graph.handle(VoiceAction.Run(RunRequest(routine.id, source = Source.SCREEN))) },
                            // → 로직 이동: RoutineRunner.undo()
                            onUndo = { graph.runner.undo(routine.id) },
                        )
                    }
                }

                ProfileRow(graph)
                ui.runs.values.maxByOrNull { it.startedAtMs }?.let { RunDetail(it) }
            }
            if (!overlay) PanelsInApp(graph)
        }
    }
}

/**
 * 작성: 2026-11-12 19:10:00 · 박건준
 * 수정: 2026-11-12 19:56:00 · 박건준
 * 함수: PanelsInApp
 * 목적: 왼쪽 구역 창을 띄우지 못했을 때 같은 구역(패널·막대·마이크 칸)과
 *       토스트를 앱 화면 왼쪽(853×1210)에 겹쳐 그린다.
 * 인자: graph — 앱 조립부
 * 미구현: 프로토타입이라 다른 앱 위에 그리기 허가가 없을 때의 대안이다. 이때
 *       패널은 왼쪽 구역이 아니라 앱 영역 안에 보인다
 */
@Composable
private fun PanelsInApp(graph: AppGraph) {
    val screen by graph.panels.ui.collectAsStateWithLifecycle() // 그릴 값
    Box(Modifier.width(853.dp).height(1210.dp)) {
        Box(Modifier.align(if (zoneFull(screen)) Alignment.TopStart else Alignment.BottomStart).padding(
            if (zoneFull(screen)) 0.dp else 24.dp,
        )) {
            LeftZone(screen, graph.panels::press, graph.panels::tapMic)
        }
        screen.toast?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 184.dp)) { PanelToast(it) }
        }
    }
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-11 16:02:00 · 박희정
 * 함수: StatusLine
 * 목적: 차 상태 한 줄(기어·속도·장소·열린 문·탑승, 받은 경우 연료). 시연 때
 *       차가 무엇을 알아챘는지 보여준다. 기어·장소는 영어 이름 대신 화면
 *       문구로 바꾼다. 시나리오가 주유(내연기관) 기준이라 에뮬레이터 전기차의
 *       차 종류·배터리·주행 가능 거리는 보이지 않는다.
 * 인자: car — 지금 차 상태
 *       fuel — 연료 값. 가짜 차면 null
 *       fake — 가짜 차를 쓰면 true. 줄 끝에 표시한다
 */
@Composable
private fun StatusLine(car: CarState, fuel: FuelState?, fake: Boolean) {
    val doors = car.doorOpen.filterValues { it }.keys.joinToString { it.label } // 열린 문
        .ifEmpty { stringResource(R.string.status_doors_closed) }
    val seats = car.seatOccupied.filterValues { it }.keys.joinToString { it.label } // 탑승한 좌석
        .ifEmpty { stringResource(R.string.status_seats_empty) }
    val gear = stringResource( // 기어 글자
        when (car.gear) {
            Gear.PARK -> R.string.gear_park
            Gear.REVERSE -> R.string.gear_reverse
            Gear.NEUTRAL -> R.string.gear_neutral
            Gear.DRIVE -> R.string.gear_drive
            Gear.UNKNOWN -> R.string.gear_unknown
        },
    )
    val place = stringResource( // 장소 이름
        when (car.place) {
            Place.CAR_WASH -> R.string.place_car_wash
            Place.GAS_STATION -> R.string.place_gas_station
            Place.KIDS -> R.string.place_kids
            Place.NONE -> R.string.place_none
        },
    )
    val energy = fuel?.percent?.let { stringResource(R.string.status_fuel, it) }.orEmpty() // 연료, 모르면 빈 글자
    Text(
        stringResource(R.string.status_line, gear, car.speedKmh, place, doors, seats) + energy +
            if (fake) stringResource(R.string.status_fake_car) else "",
        color = Color(0xFF9AA0A6),
        fontSize = 14.sp,
    )
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 19:11:42 · 박건준
 * 함수: RoutineCard
 * 목적: 루틴 하나의 카드. 이름, 지금 단계, 실행 또는 원래대로 버튼을 둔다.
 *       실행 중에는 실행 버튼을 막는다.
 * 인자: modifier — 카드 배치
 *       title — 루틴 이름
 *       run — 이 루틴의 실행 기록. 한 번도 안 돌았으면 null
 *       onRun — 실행 버튼
 *       onUndo — 원래대로 버튼
 */
@Composable
private fun RoutineCard(modifier: Modifier, title: String, run: RunView?, onRun: () -> Unit, onUndo: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    when (run?.phase) {
                        Phase.RUNNING -> R.string.routine_running
                        Phase.ACTIVE -> R.string.routine_active
                        Phase.UNDOING -> R.string.routine_undoing
                        else -> R.string.routine_idle
                    },
                ),
                color = Color(0xFF9AA0A6),
            )
            if (run?.phase == Phase.ACTIVE) {
                OutlinedButton(onClick = onUndo, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.routine_undo))
                }
            } else {
                Button(onClick = onRun, Modifier.fillMaxWidth(), enabled = run?.phase != Phase.RUNNING) {
                    Text(stringResource(R.string.routine_run))
                }
            }
        }
    }
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 19:11:42 · 박건준
 * 함수: ProfileRow
 * 목적: 저장한 시트 자세 버튼 줄. 누르면 그 자세로 시트 루틴을 돌린다.
 *       안내 문장이 바뀔 때(자세를 저장했을 때 등) 이름 목록을 다시 읽는다.
 *       저장한 자세가 없으면 줄을 그리지 않는다.
 * 인자: graph — 앱 조립부
 */
@Composable
private fun ProfileRow(graph: AppGraph) {
    val names = remember(graph.runner.ui.collectAsStateWithLifecycle().value.lastMessage) { // 저장한 사람 이름
        graph.profiles.names()
    }
    if (names.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.profiles_label), color = Color(0xFF9AA0A6))
        names.forEach { name ->
            OutlinedButton(onClick = {
                // → 로직 이동: AppGraph.handle()
                graph.handle(VoiceAction.Run(RunRequest(RoutineId.SEAT_MEMORY, name, Source.SCREEN)))
            }) { Text(name) }
        }
    }
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 20:50:30 · 박건준
 * 함수: RunDetail
 * 목적: 가장 최근 실행의 단계별 결과와 시작 뒤 지난 시간(분:초). 지난
 *       시간을 1초마다 다시 그린다. 단계 결과는 "완료"와 "안내" 두 가지만
 *       붙이고(보냈지만 확인 못 한 `Sent`도 완료), 아직 안 했거나 건너뛴
 *       단계는 이름만 보인다. 안내로 바뀐 이유가 있으면 괄호로 붙인다.
 * 인자: run — 보여줄 실행 기록
 */
@Composable
private fun RunDetail(run: RunView) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) } // 지금 시각(밀리초), 1초마다 바뀐다
    LaunchedEffect(run.startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = ((now - run.startedAtMs).coerceAtLeast(0) / 1000).toInt() // 시작 뒤 지난 시간(초)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.run_title, run.title, elapsed / 60, elapsed % 60),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            run.steps.forEach { step ->
                val mark = when (step.status) { // 단계 결과 글자, 끝나지 않은 단계는 null
                    StepStatus.DONE -> stringResource(R.string.step_done)
                    StepStatus.GUIDED -> stringResource(R.string.step_guided)
                    StepStatus.RUNNING, StepStatus.WAITING, StepStatus.SKIPPED -> null
                }
                val note = step.note // 안내로 바뀐 이유, 없으면 null
                Text(
                    when {
                        mark == null -> step.title
                        note == null -> stringResource(R.string.step_line, mark, step.title)
                        else -> stringResource(R.string.step_line_with_note, mark, step.title, note)
                    },
                    fontSize = 16.sp,
                    color = if (mark == null) Color(0xFF9AA0A6) else Color.Unspecified,
                )
            }
        }
    }
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-10 12:56:20 · 박건준
 * 함수: SayBox
 * 목적: 음성 인식 대신 쓰는 말 입력칸과 자주 쓰는 말 버튼.
 * 인자: graph — 앱 조립부
 * 근거: 측정 — 에뮬레이터에 마이크 입력이 없어서 시연은 여기나 데모 `say`로
 *       말을 넣는다
 * 미구현: 프로토타입이라 음성 인식 대신 글자로 말을 넣는다. 실제 차에서는 음성
 *       인식 결과가 같은 `PhraseMatcher`로 들어간다(음성 인식은 아직 없음)
 */
@Composable
private fun SayBox(graph: AppGraph) {
    var text by remember { mutableStateOf("") } // 입력칸 글자
    val quick = stringArrayResource(R.array.say_quick) // 빠른 말 버튼
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text,
                { text = it },
                Modifier.weight(1f),
                label = { Text(stringResource(R.string.say_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { say(graph, text); text = "" }) { Text(stringResource(R.string.say_send)) }
        }
        Row(
            // 빠른 말 버튼, 넘치면 가로로 넘긴다
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quick.forEach { q -> OutlinedButton(onClick = { say(graph, q) }) { Text(q, fontSize = 13.sp) } }
        }
    }
}

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-11 16:02:00 · 박희정
 * 함수: say
 * 목적: 입력한 말을 음성 인식 결과처럼 넘긴다. 대화 기록에 남고, 못 알아들으면
 *       그렇다고 말한다.
 * 인자: graph — 앱 조립부
 *       text — 입력한 말
 */
private fun say(graph: AppGraph, text: String) = graph.hear(text, Source.VOICE) // → 로직 이동: AppGraph.hear()

/**
 * 작성: 2026-11-11 16:02:00 · 박희정
 * 함수: ChatLog
 * 목적: 대화 기록을 말풍선처럼 보여준다. 운전자 말은 오른쪽 회색, Sunstone
 *       말은 왼쪽 파란색. 최근 `CHAT_SHOWN`줄만 보인다.
 * 인자: lines — 대화 기록, 오래된 것부터
 */
@Composable
private fun ChatLog(lines: List<ChatLine>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.takeLast(CHAT_SHOWN).forEach { line ->
            val side = if (line.fromDriver) Arrangement.End else Arrangement.Start // 운전자는 오른쪽
            val bubble = if (line.fromDriver) Color(0xFF2A2D33) else Color(0xFF1C2541) // 말풍선 색
            Row(Modifier.fillMaxWidth(), horizontalArrangement = side) {
                Text(
                    line.text,
                    Modifier
                        .background(bubble, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (line.fromDriver) Color(0xFFE0E0E0) else Color(0xFFB8C4FF),
                    fontSize = 18.sp,
                )
            }
        }
    }
}

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-12 11:45:00 · 박희정
 * 함수: SceneBar
 * 목적: 시연 모드 줄(Tab 1 4.2 실행 방식 1). 지금 장면, 그 장면 대사 버튼,
 *       [다음 장면]·[처음] 버튼을 둔다. 대사 버튼을 누르면 그 말을 한 것으로
 *       넣는다(음성 인식이 안 될 때 쓴다). 준비 상황을 넣는 동안은 버튼을 막는다.
 *       대사 버튼은 장면마다 한 번만 보인다. 같은 대답을 두 번 넣으면 물은 게
 *       없어 "잘 못 알아들었어요"가 나오기 때문이다
 * 인자: graph — 앱 조립부
 *       scenes — 장면 실행기
 */
@Composable
private fun SceneBar(graph: AppGraph, scenes: SceneControls) {
    val scene by scenes.current.collectAsStateWithLifecycle() // 지금 장면, 시작 전이면 null
    Row(
        // 시연 모드: 장면 이름, 대사 버튼, 다음·처음 버튼
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1F2B), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val s = scene // 화면에 그릴 장면
        Text(
            if (s == null) stringResource(R.string.scene_ready)
            else stringResource(R.string.scene_title, s.id, s.title),
            Modifier.weight(1f),
            color = Color(0xFF9AA0A6),
            fontSize = 16.sp,
        )
        var saidIn by remember { mutableStateOf<String?>(null) } // 대사 버튼을 누른 장면 ID
        if (s == null && saidIn != null) saidIn = null // [처음]이면 다시 보이게
        s?.line?.takeIf { saidIn != s.id }?.let { line ->
            Button(
                onClick = {
                    saidIn = s.id
                    graph.hear(line, Source.DEMO) // → 로직 이동: AppGraph.hear()
                },
                enabled = !s.busy,
            ) { Text(line) }
        }
        // → 로직 이동: SceneControls.next()
        Button(onClick = { scenes.next() }, enabled = s == null || (!s.busy && !s.last)) {
            Text(stringResource(if (s == null) R.string.scene_start else R.string.scene_next))
        }
        // → 로직 이동: SceneControls.restart()
        OutlinedButton(onClick = { scenes.restart() }, enabled = s?.busy != true) {
            Text(stringResource(R.string.scene_restart))
        }
    }
}

/**
 * 작성: 2026-11-11 16:02:00 · 박희정
 * 변수: CHAT_SHOWN
 * 목적: 화면에 보일 대화 줄 수.
 * 근거: 미정 — 시나리오 ① 한 바퀴(준비 뒤 약 10줄)가 한 화면에 들어가게 정했다
 */
private const val CHAT_SHOWN = 12
