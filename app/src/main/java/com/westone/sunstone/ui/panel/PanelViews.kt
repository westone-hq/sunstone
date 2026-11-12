package com.westone.sunstone.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westone.sunstone.R

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 변수: SHADE
 * 목적: 패널 그림자 색(검정 12%).
 * 근거: 인용 — 시안 template.html `--noti-shadow: 0 2px 8px rgba(0,0,0,.12)`
 */
private val SHADE = Color.Black.copy(alpha = .12f)

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: text
 * 목적: 시안 글자 크기(크기/줄높이, 굵기)를 글자 모양으로 만든다. 시안
 *       1단위 = 1sp(에뮬레이터 160dpi, 글자 배율 1.0).
 * 인자: size — 글자 크기
 *       line — 줄 높이
 *       weight — 굵기
 *       color — 색
 * 반환: 글자 모양
 */
private fun text(size: Int, line: Int, weight: FontWeight, color: Color) =
    TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = color)

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: AlertPanel
 * 목적: 알림 패널. 내비 경로 패널과 같은 각진 흑백 뼈대에 제목줄, 설명줄,
 *       항목 줄, 버튼 줄을 차례로 둔다.
 *       - 제목줄 112: 앱 아이콘 72, 제목 36/44 ExtraBold, 닫기 칸 112(왼쪽 1px
 *         구분선)
 *       - 설명줄 30/38, 진행 중이면 아래 진행선 6
 *       - 항목 줄 104: 아이콘 56, 글자 30/38
 *       - 버튼 100, 32/40 Bold. 두 개면 반반, 사이 1px
 * 인자: panel — 그릴 패널
 *       onPress — 버튼·닫기를 누르면 부른다
 * 근거: 인용 — docs/SCREEN.md 1장 뼈대와 크기, 시안 template.html `.noti`
 */
@Composable
fun AlertPanel(panel: PanelUi, onPress: (PanelAction) -> Unit) {
    val c = panelColors() // 색 토큰
    Column(
        // 패널 면: 모서리 0, 그림자 0 2 8 검정 12%
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape, ambientColor = SHADE, spotColor = SHADE)
            .background(c.surface),
    ) {
        Row(Modifier.fillMaxWidth().height(112.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                // 앱 아이콘과 제목
                Modifier.weight(1f).padding(start = 20.dp, end = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AppIcon()
                Text(
                    panel.title,
                    style = text(36, 44, FontWeight.ExtraBold, c.t1).copy(letterSpacing = (-0.3).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.div2))
            Box(
                // 닫기 칸
                Modifier.size(112.dp).clickable { onPress(panel.close) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_panel_close),
                    stringResource(R.string.panel_close),
                    Modifier.size(48.dp),
                    tint = c.t2,
                )
            }
        }
        Line(c.div2)
        if (panel.detail.isNotEmpty() || panel.progress != null) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    panel.detail,
                    Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 26.dp),
                    style = text(30, 38, FontWeight.Normal, c.t2),
                )
                panel.progress?.let { p ->
                    val ratio = if (p.total == 0) 0f else p.done.toFloat() / p.total // 진행선 길이(0~1)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(ratio).height(6.dp).background(c.t1))
                }
            }
            Line(c.div2)
        }
        panel.rows.forEachIndexed { i, row ->
            PanelRow(row)
            if (i < panel.rows.lastIndex) Line(c.div2)
        }
        if (panel.actions.isNotEmpty()) {
            Line(c.div2)
            Row(Modifier.fillMaxWidth().height(100.dp)) {
                panel.actions.forEachIndexed { i, a ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.div2))
                    Box(
                        // 버튼 하나: 칸을 꽉 채우는 각진 막대
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (a.filled) c.filled else c.surface)
                            .clickable { onPress(a.action) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(a.label, style = text(32, 40, FontWeight.Bold, if (a.filled) c.onFilled else c.t1))
                    }
                }
            }
        }
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: PanelRow
 * 목적: 항목 줄 하나. TODO는 회색 칸, NOW·DONE·MANUAL은 흰 칸. NOW는 왼쪽
 *       검은 막대 4와 도는 표시, DONE은 체크, MANUAL·안내 글자가 있으면
 *       오른쪽 글자.
 * 인자: row — 그릴 줄
 * 근거: 인용 — 시안 template.html `.li`
 */
@Composable
private fun PanelRow(row: RowUi) {
    val c = panelColors() // 색 토큰
    val white = row.state != RowState.TODO // 흰 칸인지
    val strong = row.state == RowState.DONE || row.state == RowState.NOW // 글자를 굵게
    Row(
        // 줄: 높이 104, 왼쪽 막대 4 자리 + 여백 28, 오른쪽 여백 32
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(c.surface)
            .background(if (white) Color.Transparent else c.div1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (row.state == RowState.NOW) c.t1 else Color.Transparent))
        Spacer(Modifier.width(28.dp))
        iconRes(row.icon)?.let {
            Icon(painterResource(it), null, Modifier.size(56.dp), tint = if (white) c.t1 else c.t3)
            Spacer(Modifier.width(24.dp))
        }
        Text(
            row.text,
            Modifier.weight(1f),
            style = text(30, 38, if (strong) FontWeight.Bold else FontWeight.Normal, if (white) c.t1 else c.t2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            row.state == RowState.NOW -> Spinner()
            row.state == RowState.DONE ->
                Icon(painterResource(R.drawable.ic_panel_check), null, Modifier.size(48.dp), tint = c.t1)
            row.note != null -> Text(row.note, style = text(26, 34, FontWeight.Normal, c.t3))
        }
        Spacer(Modifier.width(32.dp))
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: Spinner
 * 목적: 지금 하는 중 표시. 지름 40, 테두리 5, 0.9초에 한 바퀴.
 * 근거: 인용 — 시안 template.html `.spin`
 */
@Composable
private fun Spinner() {
    val c = panelColors() // 색 토큰
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn",
    )
    Canvas(Modifier.size(40.dp).rotate(turn)) {
        val w = 5.dp.toPx() // 테두리 굵기
        val inset = Stroke(w) // 선 모양
        drawCircle(c.div2, radius = size.minDimension / 2 - w / 2, style = inset)
        drawArc(
            c.t1, -45f, 90f, false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            style = Stroke(w, cap = StrokeCap.Butt),
        )
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: AppIcon
 * 목적: 앱 아이콘 72. 검은 칸에 3×3 로고, 로고 칸 다섯은 주황. 주황은 여기서만
 *       쓴다.
 * 근거: 인용 — 시안 template.html `.appicon .logo`, docs/SCREEN.md 5장 앱
 *       아이콘 주황은 브랜드색으로 아이콘 안에서만
 */
@Composable
private fun AppIcon() {
    val c = panelColors() // 색 토큰
    val lit = setOf(1, 3, 4, 5, 7) // 주황 칸 번호(0~8, 십자 모양)
    Box(Modifier.size(72.dp).background(BAR_BLACK), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { k ->
                        Box(Modifier.size(14.dp).background(if (r * 3 + k in lit) c.sun else Color(0xFF45474D)))
                    }
                }
            }
        }
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: Line
 * 목적: 1px 구분선.
 * 인자: color — 선 색
 */
@Composable
private fun Line(color: Color) = Box(Modifier.fillMaxWidth().height(1.dp).background(color))

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: ListenBar
 * 목적: 듣는 중 막대. 높이 160, 검은 바탕에 마이크 칸 160, 파형 막대 5개,
 *       글자. 듣는 중이면 마이크 칸 초록·파형 움직임, 들은 뒤면 #2A2C31·파형
 *       멈춤.
 * 인자: listen — 막대 값. OFF면 부르지 않는다
 * 근거: 인용 — docs/SCREEN.md 1장, 시안 template.html `.voice`
 */
@Composable
fun ListenBar(listen: ListenUi) {
    val c = panelColors() // 색 토큰
    val heard = listen.state == ListenState.HEARD // 들은 뒤인지
    Row(Modifier.fillMaxWidth().height(160.dp).background(BAR_BLACK), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(160.dp).background(if (heard) BAR_HEARD else c.green),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_panel_mic),
                null,
                Modifier.size(64.dp),
                tint = if (heard) Color.White else BAR_BLACK,
            )
        }
        Wave(moving = !heard, Modifier.padding(start = 36.dp, end = 28.dp))
        Column(Modifier.weight(1f).padding(end = 32.dp)) {
            Text(listen.text, style = text(32, 40, FontWeight.Bold, Color.White), maxLines = 2)
            listen.hint?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = text(26, 34, FontWeight.Normal, Color.White.copy(alpha = .64f)))
            }
        }
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: Wave
 * 목적: 파형 막대 5개(폭 6, 사이 6). 움직이면 높이 12↔48을 1초에 오가고
 *       막대마다 0.15초씩 늦게 시작한다. 멈추면 높이 6, 투명도 40%.
 * 인자: moving — 움직이면 true
 *       modifier — 배치
 * 근거: 인용 — 시안 template.html `.wave`, `@keyframes wv`
 */
@Composable
private fun Wave(moving: Boolean, modifier: Modifier) {
    val wave = rememberInfiniteTransition(label = "wave") // 파형 반복
    Row(
        modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { i ->
            val h by wave.animateFloat( // 막대 높이(dp)
                12f, 48f,
                infiniteRepeatable(
                    tween(500, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    StartOffset(i * 150),
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(6.dp)
                    .height(if (moving) h.dp else 6.dp)
                    .alpha(if (moving) 1f else .4f)
                    .background(Color.White),
            )
        }
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: PanelToast
 * 목적: 토스트. 반경 4, 체크 40 + 글자 26/34 Bold, 최대 폭 792.
 * 인자: message — 토스트 문장
 * 근거: 인용 — 시안 template.html `.toast`
 */
@Composable
fun PanelToast(message: String) {
    val c = panelColors() // 색 토큰
    Row(
        Modifier
            .widthIn(max = 792.dp)
            .background(c.toast, RoundedCornerShape(4.dp))
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_panel_check), null, Modifier.size(40.dp), tint = c.onToast)
        Text(message, style = text(26, 34, FontWeight.Bold, c.onToast), maxLines = 1)
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 함수: MicCell
 * 목적: 말 걸기 칸. 듣는 중 막대가 없을 때 왼쪽 구역 아래에 늘 둔다. 누르면
 *       듣는 중 막대를 띄운다. 초록은 마이크가 켜졌을 때만 쓰므로 여기는
 *       검정이다.
 * 인자: onTap — 누르면 부른다
 * 근거: 측정 — 운전대 음성 버튼(KEYCODE_VOICE_ASSIST)은 시스템 음성
 *       비서(Gleo)가 가져가 앱이 받지 못한다(docs/api-spike.md 운전대 음성
 *       버튼 행). docs/SCREEN.md 5장 대화 시작 방법의 대안
 * 미구현: 프로토타입이라 운전대 버튼 대신 화면 칸으로 말을 건다
 */
@Composable
fun MicCell(onTap: () -> Unit) {
    Box(
        Modifier.size(112.dp).background(BAR_BLACK).clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_panel_mic),
            stringResource(R.string.mic_cell),
            Modifier.size(56.dp),
            tint = Color.White,
        )
    }
}

/**
 * 작성: 2026-11-12 19:50:00 · 박건준
 * 함수: zoneFull
 * 목적: 왼쪽 구역 전체를 덮을 때인지 본다. 패널이나 듣는 중 막대가 떠 있으면
 *       덮는다.
 * 인자: ui — 왼쪽 구역 값
 * 반환: 덮으면 true. 아니면 마이크 칸만 보인다
 */
fun zoneFull(ui: ScreenUi): Boolean = ui.panel != null || ui.listen.state != ListenState.OFF

/**
 * 작성: 2026-11-12 19:50:00 · 박건준
 * 함수: LeftZone
 * 목적: 왼쪽 구역(상태줄 아래부터 하단 막대 위까지)을 그린다.
 *       - 덮을 때 — 불투명 배경(시안 `.drive` 세로 그라데이션) 위에 위쪽 패널,
 *         맨 아래 듣는 중 막대. 막대가 없으면 왼쪽 아래 마이크 칸
 *       - 덮지 않을 때 — 마이크 칸만
 *       배경으로 시스템 주행 화면(차 그림, 빠른 버튼, 미디어 줄)이 패널 사이로
 *       보이지 않게 한다. 토스트는 따로 그린다(`PanelToast`).
 * 인자: ui — 왼쪽 구역 값
 *       onPress — 패널 버튼·닫기
 *       onMic — 마이크 칸
 * 근거: 인용 — 시안 template.html `.drive`(낮 #FFFFFF 20% → #EEF0F2, 밤
 *       #1D1E21 → #17181B), `.drive.busy`는 패널이 뜨면 원래 내용을 숨긴다
 */
@Composable
fun LeftZone(ui: ScreenUi, onPress: (PanelAction) -> Unit, onMic: () -> Unit) {
    if (!zoneFull(ui)) {
        MicCell(onMic)
        return
    }
    val c = panelColors() // 색 토큰
    val drive = Brush.verticalGradient(0f to c.driveTop, .2f to c.driveTop, 1f to c.driveBottom) // 구역 배경
    Box(Modifier.fillMaxSize().background(drive)) {
        ui.panel?.let { Box(Modifier.padding(24.dp)) { AlertPanel(it, onPress) } }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            if (ui.listen.state != ListenState.OFF) {
                ListenBar(ui.listen)
            } else {
                Box(Modifier.padding(24.dp)) { MicCell(onMic) }
            }
        }
    }
}
