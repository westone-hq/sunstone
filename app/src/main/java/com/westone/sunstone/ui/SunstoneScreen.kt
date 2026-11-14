package com.westone.sunstone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westone.sunstone.AppGraph
import com.westone.sunstone.ui.panel.CARD_SIDE
import com.westone.sunstone.ui.panel.CARD_TOP
import com.westone.sunstone.ui.panel.CardOrBar
import com.westone.sunstone.ui.panel.MicCell
import com.westone.sunstone.ui.panel.panelColors

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 함수: SunstoneScreen
 * 목적: 왼쪽 구역 창을 띄우지 못했을 때만 여는 앱 화면. `page` 색 빈 바탕
 *       왼쪽(853×1210)에 왼쪽 구역과 같은 마이크 칸·카드를 같은 자리로
 *       그린다. 창이 뜨면 앱 화면은 그리지 않는다(`MainActivity`).
 * 인자: graph — 앱 조립부
 * 근거: 인용 — 2026-11-13 박건준 지시: 앱 화면(상태 줄, 시연 모드 줄,
 *       말풍선, 입력칸, 루틴 카드, 실행 기록)을 없애고 왼쪽 구역 카드만 쓴다
 * 미구현: 프로토타입이라 다른 앱 위에 그리기 허가가 없을 때의 대안이다. 이때
 *       카드는 왼쪽 구역이 아니라 앱 영역 안에 보인다
 */
@Composable
fun SunstoneScreen(graph: AppGraph) {
    val ui by graph.panels.ui.collectAsStateWithLifecycle() // 그릴 값
    val panels = graph.panels // 카드 값과 누르기 처리
    Box(Modifier.fillMaxSize().background(panelColors().page)) {
        Box(Modifier.width(853.dp).height(1210.dp)) {
            if (ui.mic) MicCell(panels::tapMic, panels::hold)
            Box(Modifier.padding(start = CARD_SIDE.dp, top = CARD_TOP.dp)) { CardOrBar(ui, panels) }
        }
    }
}
