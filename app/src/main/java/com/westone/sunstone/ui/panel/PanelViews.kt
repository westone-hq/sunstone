package com.westone.sunstone.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westone.sunstone.R
import com.westone.sunstone.routines.dialog.Fact

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 수정: 2026-11-13 04:20:00 · 박건준
 * 변수: CARD_SIDE, CARD_TOP, CARD_WIDTH, PLATE_BOTTOM, PLATE_HEIGHT
 * 목적: 왼쪽 구역 안 카드·판 자리(px = dp). 카드는 좌우 24, 상태줄 아래 86
 *       (화면 176)에서 시작하고, 판은 좌우 24, 아래 24에 높이 112로 뜬다.
 *       구역 폭은 853이라 카드·판 폭은 853 − 48 = 805.
 * 식: CARD_TOP = 운전대 표시 아이콘 아래 끝 160 + 16 − 상태줄 90 = 86
 * 근거: 인용 — 시안 template2.html `.noti`(left 24, right 24, top 176),
 *       `.voice`·`.toast`(bottom 24, height 112). 측정 — 왼쪽 구역 폭 853,
 *       상태줄 90, 운전대 표시 아이콘 y 106~160(docs/api-spike.md 왼쪽 구역
 *       그리기 행)
 */
internal const val CARD_SIDE = 24
internal const val CARD_TOP = 86
internal const val CARD_WIDTH = 805
internal const val PLATE_BOTTOM = 24
internal const val PLATE_HEIGHT = 112

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 변수: SHADOW
 * 목적: 카드·판 그림자가 그려질 바깥 여백(px = dp). 창은 이만큼 크게 잡고
 *       안쪽에 카드·판을 그린다.
 * 근거: 인용 — 시안 template2.html `--noti-shadow: 0 2px 8px`(아래로 10까지
 *       번진다)
 */
internal const val SHADOW = 12

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 변수: SHADE
 * 목적: 카드·판 그림자 색(검정 12%).
 * 근거: 인용 — 시안 template2.html `--noti-shadow: 0 2px 8px rgba(0,0,0,.12)`
 */
private val SHADE = Color.Black.copy(alpha = .12f)

/**
 * 작성: 2026-11-12 22:45:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 변수: GAP
 * 목적: 근거 칸끼리, 할 일 칸끼리, 칸과 버튼, 두 버튼 사이 띄우기. 사이로
 *       카드 흰 바탕이 보인다.
 * 근거: 인용 — 시안 template2.html `.facts`·`.cells`·`.na` gap 2
 */
private val GAP = 2.dp

/**
 * 작성: 2026-11-13 04:20:00 · 박건준
 * 변수: PICKED
 * 목적: 채운 버튼 위에 덮는 흰색 18%. 기다림이 차오르는 막대와 고른 버튼에
 *       같이 쓴다.
 * 근거: 인용 — 시안 template2.html `.btn .cd`, `.btn.filled.picked`
 */
private val PICKED = Color.White.copy(alpha = .18f)

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 변수: HOLD_MS
 * 목적: 길게 누르기로 보는 시간(밀리초). 시연 모드 다음 장면에 쓴다.
 * 근거: 인용 — 2026-11-13 박건준 2차 업그레이드 지시: 마이크 칸을 0.8초 길게
 *       누르면 다음 장면
 */
private const val HOLD_MS = 800L

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
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 수정: 2026-11-13 04:05:00 · 박건준
 * 함수: holdOrTap
 * 목적: 누르면 `onTap`, `HOLD_MS` 넘게 누르고 있으면 `onHold`를 부른다.
 *       길게 누른 뒤 손을 떼도 `onTap`은 부르지 않는다. 다시 그려져도 누르던
 *       것이 끊기지 않게 할 일은 최신 값만 바꿔 쓴다.
 * 인자: onTap — 짧게 눌렀을 때. 없으면 null
 *       onHold — 길게 눌렀을 때
 * 반환: 누르기를 받는 Modifier
 * 근거: 측정 — 할 일을 키로 두니 [지금 켜기]가 차오르며 다시 그려질 때마다
 *       누르기 받기가 처음부터 다시 시작됐다(2026-11-13 반복 실행)
 */
@Composable
private fun Modifier.holdOrTap(onTap: (() -> Unit)?, onHold: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap) // 지금 짧게 누르기 할 일
    val hold by rememberUpdatedState(onHold) // 지금 길게 누르기 할 일
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            var lifted = false // 시간 안에 손을 뗐는지
            val up = withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation().also { lifted = true } } // 뗀 자리
            when {
                !lifted -> {
                    hold()
                    waitForUpOrCancellation()
                }
                up != null -> tap?.invoke()
            }
        }
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: NotiCard
 * 목적: 왼쪽 주행 화면 위에 뜨는 카드. Pleos 시스템 알림과 같은 짜임을 각진
 *       흰 면에 그린다.
 *       - 머리: 앱 아이콘 72 | 제목 36/44 ExtraBold + 설명 26/34(최대 2줄,
 *         굵게 보일 낱말) | 닫기 40(구분선 없음). 안쪽 여백 28
 *       - 근거 칸(제안 카드): 제목 아래 회색 칸, 높이 48, 아이콘 30 + 글 24 Bold
 *       - 할 일 칸: 가로 한 줄, 높이 128
 *       - 버튼: 높이 88, 30/38 Bold, 둘이면 반반. 채운 버튼 검정, 다른 하나는
 *         회색(basic)
 *       켜짐 표시(`chip`)면 높이 88 한 줄로 그린다.
 * 인자: card — 그릴 카드
 *       onPress — 버튼·닫기를 누르면 부른다
 *       onHold — 앱 아이콘을 길게 누르면 부른다(시연 모드 다음 장면)
 * 근거: 인용 — docs/SCREEN.md 6장 카드, 시안 template2.html `.noti`
 */
@Composable
fun NotiCard(card: CardUi, onPress: (PanelPress) -> Unit, onHold: () -> Unit) {
    val c = panelColors() // 색 토큰
    Column(
        // 카드 면: 모서리 0, 그림자 0 2 8 검정 12%
        Modifier
            .width(CARD_WIDTH.dp)
            .shadow(4.dp, RectangleShape, ambientColor = SHADE, spotColor = SHADE)
            .background(c.surface),
    ) {
        if (card.chip) {
            ChipRow(card, onPress, onHold)
            return@Column
        }
        CardHead(card, onPress, onHold)
        if (card.facts.isNotEmpty()) FactRow(card.facts)
        if (card.cells.isNotEmpty()) {
            Row(Modifier.padding(top = GAP), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                card.cells.forEach { Cell(it, Modifier.weight(1f)) }
            }
        }
        if (card.actions.isNotEmpty()) {
            Row(
                Modifier.padding(top = GAP).fillMaxWidth().height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {
                card.actions.forEachIndexed { i, a -> ActionButton(a, card.key to i, onPress, Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: CardHead
 * 목적: 카드 머리. 앱 아이콘, 제목·설명, 닫기 칸(폭 88, 아이콘 40, 위 34).
 *       근거 칸이 있으면 아래 여백을 8 줄여 근거 칸을 제목 쪽으로 붙인다.
 * 인자: card — 그릴 카드
 *       onPress — 닫기를 누르면 부른다
 *       onHold — 앱 아이콘을 길게 누르면 부른다
 * 근거: 인용 — 시안 template2.html `.nh`, `.facts { margin-top: -8px }`
 */
@Composable
private fun CardHead(card: CardUi, onPress: (PanelPress) -> Unit, onHold: () -> Unit) {
    val c = panelColors() // 색 토큰
    val bottom = if (card.facts.isEmpty()) 28.dp else 20.dp // 머리 아래 여백
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(
            Modifier.weight(1f).padding(start = 28.dp, top = 28.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AppIcon(72, onHold)
            Column {
                Text(card.title, style = text(36, 44, FontWeight.ExtraBold, c.t1).copy(letterSpacing = (-0.3).sp))
                if (card.detail.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        emphasized(card.detail, card.emphasis, c.t1),
                        style = text(26, 34, FontWeight.Normal, c.t2),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        card.close?.let { close ->
            Box(
                // 닫기 칸: 구분선 없이 오른쪽 88
                Modifier.width(88.dp).fillMaxHeight().holdOrTap({ onPress(close) }, {}),
                contentAlignment = Alignment.TopCenter,
            ) {
                Icon(
                    painterResource(R.drawable.ic_panel_close),
                    stringResource(R.string.panel_close),
                    Modifier.padding(top = 34.dp).size(40.dp),
                    tint = c.t3,
                )
            }
        }
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: emphasized
 * 목적: 설명 글자에서 굵게 보일 낱말(주유소 이름)만 굵고 진하게 바꾼다.
 * 인자: detail — 설명
 *       bold — 굵게 보일 낱말. 없거나 설명에 없으면 그대로
 *       color — 굵은 글자 색
 * 반환: 꾸민 글자
 * 근거: 인용 — 시안 template2.html `.nd b`(t1, 700)
 */
private fun emphasized(detail: String, bold: String?, color: Color) = buildAnnotatedString {
    val at = bold?.let(detail::indexOf) ?: -1 // 굵은 낱말 자리, 없으면 -1
    if (bold == null || at < 0) {
        append(detail)
        return@buildAnnotatedString
    }
    append(detail.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(bold) }
    append(detail.substring(at + bold.length))
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: FactRow
 * 목적: 근거 칸 줄. 제목 글자와 같은 왼쪽(124)에서 시작하고 칸 사이 2.
 * 인자: facts — 근거 칸들
 * 근거: 인용 — 시안 template2.html `.facts`(padding 0 28 24 124)
 */
@Composable
private fun FactRow(facts: List<Fact>) {
    val c = panelColors() // 색 토큰
    Row(
        Modifier.padding(start = 124.dp, end = 28.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        facts.forEach { f ->
            Row(
                // 근거 칸 하나: 높이 48, 회색
                Modifier.height(48.dp).background(c.div1).padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                iconRes(f.icon)?.let { Icon(painterResource(it), null, Modifier.size(30.dp), tint = c.t1) }
                Text(f.text, style = text(24, 30, FontWeight.Bold, c.t1), maxLines = 1)
            }
        }
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: Cell
 * 목적: 할 일 칸 하나(높이 128). 아이콘 44 + 글 24/30을 가운데에 둔다.
 *       - TODO — 회색, 글·아이콘 흐림
 *       - NOW — 흰 칸, 위 검은 막대 4, 굵게
 *       - DONE — 흰 칸, 오른쪽 위 체크 30, 굵게
 *       - MANUAL — 흰 칸, 아래 "직접" 22
 * 인자: cell — 그릴 칸
 *       modifier — 배치(폭)
 * 근거: 인용 — 시안 template2.html `.cell`
 */
@Composable
private fun Cell(cell: CellUi, modifier: Modifier) {
    val c = panelColors() // 색 토큰
    val white = cell.state != CellState.TODO // 흰 칸인지
    val strong = cell.state == CellState.NOW || cell.state == CellState.DONE // 굵게
    val ink = when (cell.state) { // 글·아이콘 색
        CellState.TODO -> c.t3
        CellState.MANUAL -> c.t2
        else -> c.t1
    }
    Box(modifier.height(128.dp).background(if (white) c.surface else c.div1)) {
        if (cell.state == CellState.NOW) Box(Modifier.fillMaxWidth().height(4.dp).background(c.t1))
        if (cell.state == CellState.DONE) {
            Icon(
                painterResource(R.drawable.ic_panel_check),
                null,
                Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp).size(30.dp),
                tint = c.t1,
            )
        }
        Column(
            Modifier.align(Alignment.Center).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            iconRes(cell.icon)?.let { Icon(painterResource(it), null, Modifier.size(44.dp), tint = ink) }
            Text(cell.text, style = text(24, 30, if (strong) FontWeight.Bold else FontWeight.Normal, ink), maxLines = 1)
            cell.note?.let { Text(it, style = text(22, 28, FontWeight.Normal, c.t3)) }
        }
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 수정: 2026-11-13 04:20:00 · 박건준
 * 함수: ActionButton
 * 목적: 카드 버튼 하나. 칸을 꽉 채우는 각진 막대.
 *       - 기다리는 버튼(`waitMs`)이면 흰색 18%가 그 시간 동안 왼쪽부터 차오른다
 *       - 고른 버튼(`picked`)이면 채운 버튼은 흰색 18%를 버튼 전체에 덮고(기다림이
 *         다 찬 모습과 같다), 회색 버튼은 눌린 회색으로 둔다
 * 인자: action — 버튼 값
 *       key — 같은 카드의 같은 버튼인지 가르는 값. 바뀌면 다시 채운다
 *       onPress — 누르면 부른다
 *       modifier — 배치(폭)
 * 근거: 인용 — 시안 template2.html `.btn`, `.btn .cd`(5초 linear),
 *       `.btn.filled.picked`(inset 흰색 18%), `.btn.basic.picked`. 다 차면
 *       고르는 일은 routines 실행기가 한다(`RoutineRunner` 기다렸다
 *       받아들이기). 화면은 차오르기만 그린다
 */
@Composable
private fun ActionButton(action: ActionUi, key: Any?, onPress: (PanelPress) -> Unit, modifier: Modifier) {
    val c = panelColors() // 색 토큰
    val back = when { // 버튼 바탕
        action.filled -> c.filled
        action.picked -> c.basicPress
        else -> c.basic
    }
    Box(
        modifier
            .fillMaxHeight()
            .background(back)
            .then(if (action.filled && action.picked) Modifier.background(PICKED) else Modifier)
            .holdOrTap({ onPress(action.press) }, {}),
        contentAlignment = Alignment.Center,
    ) {
        if (action.waitMs > 0) {
            val fill = remember(key) { Animatable(0f) } // 차오른 비율(0~1)
            LaunchedEffect(key) { fill.animateTo(1f, tween(action.waitMs.toInt(), easing = LinearEasing)) }
            // 차오르는 막대는 그리기 단계에서만 읽어 버튼을 다시 짜지 않는다
            Box(
                Modifier.matchParentSize().drawBehind {
                    drawRect(PICKED, size = Size(size.width * fill.value, size.height))
                },
            )
        }
        Text(action.label, style = text(30, 38, FontWeight.Bold, if (action.filled) c.onFilled else c.t1))
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: ChipRow
 * 목적: 켜짐 표시 한 줄(높이 88). 앱 아이콘 56 | 제목 30/38 Bold + 설명
 *       24/30 | 회색 버튼 폭 180.
 * 인자: card — 켜짐 표시 카드
 *       onPress — 버튼을 누르면 부른다
 *       onHold — 앱 아이콘을 길게 누르면 부른다
 * 근거: 인용 — docs/SCREEN.md 6장 8단계, 시안 template2.html `.chipc`
 */
@Composable
private fun ChipRow(card: CardUi, onPress: (PanelPress) -> Unit, onHold: () -> Unit) {
    val c = panelColors() // 색 토큰
    Row(
        Modifier.fillMaxWidth().height(88.dp).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AppIcon(56, onHold)
        Column(Modifier.weight(1f)) {
            Text(card.title, style = text(30, 38, FontWeight.Bold, c.t1), maxLines = 1)
            Text(card.detail, style = text(24, 30, FontWeight.Normal, c.t2), maxLines = 1)
        }
        card.actions.firstOrNull()?.let { ActionButton(it, card.key, onPress, Modifier.width(180.dp)) }
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 함수: AppIcon
 * 목적: 앱 아이콘. 검은 칸에 3×3 로고, 로고 칸 다섯은 주황. 주황은 여기서만
 *       쓴다. 72면 로고 칸 14·사이 4, 56이면 10·3. 길게 누르면 `onHold`.
 * 인자: size — 아이콘 크기(72, 56)
 *       onHold — 길게 누르면 부른다(시연 모드 다음 장면)
 * 근거: 인용 — 시안 template2.html `.appicon .logo`, `.chipc .appicon`.
 *       docs/SCREEN.md 5장 앱 아이콘 주황은 브랜드색으로 아이콘 안에서만
 */
@Composable
private fun AppIcon(size: Int, onHold: () -> Unit) {
    val c = panelColors() // 색 토큰
    val lit = setOf(1, 3, 4, 5, 7) // 주황 칸 번호(0~8, 십자 모양)
    val dot = if (size >= 72) 14 else 10 // 로고 칸 크기
    val gap = if (size >= 72) 4 else 3 // 로고 칸 사이
    Box(
        Modifier.size(size.dp).background(BAR_BLACK).holdOrTap(null, onHold),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
            repeat(3) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    repeat(3) { k ->
                        Box(Modifier.size(dot.dp).background(if (r * 3 + k in lit) c.sun else Color(0xFF45474D)))
                    }
                }
            }
        }
    }
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 함수: PlateView
 * 목적: 아래 판 하나(높이 112, 폭 805, 검정, 그림자). 듣는 중 판과 토스트가 같은
 *       자리를 번갈아 쓴다.
 *       - 듣는 중 — 마이크 칸 112(초록, 들은 뒤 #2A2C31, 아이콘 48), 파형,
 *         글자 30/38 Bold, 들은 뒤 아래 줄 24/30
 *       - 토스트 — 체크 칸 112(#2A2C31), 글자 30/38 Bold
 * 인자: plate — 그릴 것
 *       onHold — 마이크 칸을 길게 누르면 부른다(시연 모드 다음 장면)
 * 근거: 인용 — docs/SCREEN.md 6장 듣는 중 판과 토스트, 시안 template2.html
 *       `.voice`, `.toast`
 */
@Composable
fun PlateView(plate: Plate, onHold: () -> Unit) {
    Row(
        Modifier
            .width(CARD_WIDTH.dp)
            .height(PLATE_HEIGHT.dp)
            .shadow(4.dp, RectangleShape, ambientColor = SHADE, spotColor = SHADE)
            .background(BAR_BLACK),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (plate) {
            is Plate.Listen -> ListenRow(plate.listen, onHold)
            is Plate.Toast -> {
                Box(Modifier.size(PLATE_HEIGHT.dp).background(BAR_HEARD), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_panel_check), null, Modifier.size(48.dp), tint = Color.White)
                }
                Text(
                    plate.text,
                    Modifier.padding(horizontal = 32.dp),
                    style = text(30, 38, FontWeight.Bold, Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 수정: 2026-11-13 04:20:00 · 박건준
 * 함수: ListenRow
 * 목적: 듣는 중 판 안쪽. 듣는 중이면 마이크 칸 초록·파형 움직임·"듣고
 *       있어요" 한 줄, 들은 뒤면 #2A2C31·파형 멈춤·들은 말 아래 앱이 할 일.
 *       처리 중이면 들은 뒤와 같고 마이크 대신 도는 표시를 둔다.
 * 인자: listen — 판 값
 *       onHold — 마이크 칸을 길게 누르면 부른다
 * 근거: 인용 — 시안 template2.html `.voice`, `.wave`(padding 0 24 0 32)
 */
@Composable
private fun ListenRow(listen: ListenUi, onHold: () -> Unit) {
    val c = panelColors() // 색 토큰
    val heard = listen.state != ListenState.LISTENING // 들은 뒤인지(처리 중 포함)
    Box(
        Modifier.size(PLATE_HEIGHT.dp).background(if (heard) BAR_HEARD else c.green).holdOrTap(null, onHold),
        contentAlignment = Alignment.Center,
    ) {
        if (listen.state == ListenState.WORKING) {
            Spinner()
        } else {
            Icon(
                painterResource(R.drawable.ic_panel_mic),
                null,
                Modifier.size(48.dp),
                tint = if (heard) Color.White else BAR_BLACK,
            )
        }
    }
    Wave(moving = !heard, Modifier.padding(start = 32.dp, end = 24.dp))
    Column(Modifier.padding(end = 32.dp)) {
        Text(
            listen.text,
            style = text(30, 38, FontWeight.Bold, Color.White),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        listen.next?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = text(24, 30, FontWeight.Normal, Color.White.copy(alpha = .64f)), maxLines = 1)
        }
    }
}

/**
 * 작성: 2026-11-13 04:20:00 · 박건준
 * 함수: Spinner
 * 목적: 처리 중 표시. 지름 44(마이크 아이콘 48 칸 안), 테두리 5, 흰색 24% 원 위에
 *       흰 호가 0.9초에 한 바퀴 돈다.
 * 근거: 인용 — 1차 시안 template.html `.spin`(0.9초 한 바퀴, 테두리 5)을 판
 *       색(검정 바탕 흰색)으로 옮겼다. 2차 시안에는 처리 중 표시가 없다
 */
@Composable
private fun Spinner() {
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn",
    )
    Canvas(Modifier.size(44.dp).rotate(turn)) {
        val w = 5.dp.toPx() // 테두리 굵기
        val r = size.minDimension / 2 - w / 2 // 원 반지름
        drawCircle(Color.White.copy(alpha = .24f), radius = r, style = Stroke(w))
        drawArc(
            Color.White, -90f, 100f, false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            style = Stroke(w, cap = StrokeCap.Butt),
        )
    }
}

/**
 * 작성: 2026-11-12 18:50:00 · 박건준
 * 수정: 2026-11-12 22:45:00 · 박건준
 * 함수: Wave
 * 목적: 파형 막대 5개(폭 6, 사이 6, 칸 높이 40). 움직이면 높이 12↔36을
 *       1초에 오가고 막대마다 0.15초씩 늦게 시작한다. 멈추면 높이 6, 투명도
 *       40%.
 * 인자: moving — 움직이면 true
 *       modifier — 배치
 * 근거: 인용 — 시안 template2.html `.wave`, `@keyframes wv`
 */
@Composable
private fun Wave(moving: Boolean, modifier: Modifier) {
    val wave = rememberInfiniteTransition(label = "wave") // 파형 반복
    Row(
        modifier.height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { i ->
            val h by wave.animateFloat( // 막대 높이(dp)
                12f, 36f,
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
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 함수: MicCell
 * 목적: 말 걸기 칸 112. 카드·판이 없을 때 상태줄 바로 아래 왼쪽에 둔다. 누르면
 *       듣는 중 판을 띄우고, 0.8초 길게 누르면 `onHold`(시연 모드 다음 장면).
 *       초록은 마이크가 켜졌을 때만 쓰므로 여기는 검정이다.
 * 인자: onTap — 누르면 부른다
 *       onHold — 길게 누르면 부른다
 * 근거: 인용 — 시안 template2.html `.mic`(left 0, top 92, 112×112).
 *       측정 — 운전대 음성 버튼(KEYCODE_VOICE_ASSIST)은 시스템 음성
 *       비서(Gleo)가 가져가 앱이 받지 못한다(docs/api-spike.md 운전대 음성
 *       버튼 행). docs/SCREEN.md 5장 대화 시작 방법의 대안
 * 미구현: 프로토타입이라 운전대 버튼 대신 화면 칸으로 말을 건다
 */
@Composable
fun MicCell(onTap: () -> Unit, onHold: () -> Unit) {
    Box(
        Modifier.size(112.dp).background(BAR_BLACK).holdOrTap(onTap, onHold),
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
