package com.westone.sunstone.ui.panel

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.westone.sunstone.R

/**
 * 작성: 2026-11-12 18:48:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 클래스: PanelColors
 * 목적: 카드 색 토큰. 검정·흰색·회색만 쓰고, 초록은 마이크 칸, 주황은 앱
 *       아이콘 안에서만 쓴다.
 * 인자: surface — 카드 면
 *       t1, t2, t3 — 글자 진하기 1~3단계
 *       div1 — 아직 안 한 칸·근거 칸 바탕
 *       basic, basicPress — 채우지 않은 버튼 바탕과 눌린 바탕
 *       filled, onFilled — 채운 버튼 바탕과 글자
 *       green — 마이크 켜짐 칸
 *       sun — 앱 아이콘 로고 칸
 * 근거: 인용 — 시안 template2.html `:root`(낮)과 `[data-theme="dark"]`(밤)
 *       토큰, docs/SCREEN.md 1장 색
 */
@Immutable
data class PanelColors(
    val surface: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val div1: Color,
    val basic: Color,
    val basicPress: Color,
    val filled: Color,
    val onFilled: Color,
    val green: Color,
    val sun: Color,
)

/**
 * 작성: 2026-11-12 18:48:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 변수: LIGHT, DARK
 * 목적: 낮·밤 색 토큰.
 * 근거: 인용 — 시안 template2.html 토큰 값 그대로
 */
private val LIGHT = PanelColors(
    surface = Color(0xFFFFFFFF),
    t1 = Color(0xFF131417),
    t2 = Color(0xD61B1C1E),
    t3 = Color(0xA31B1C1E),
    div1 = Color(0x0D131417),
    basic = Color(0x0D131417),
    basicPress = Color(0x1A131417),
    filled = Color(0xFF131417),
    onFilled = Color(0xFFFFFFFF),
    green = Color(0xFF02C265),
    sun = Color(0xFFF09A1C),
)
private val DARK = PanelColors(
    surface = Color(0xFF26282D),
    t1 = Color(0xFFF2F3F5),
    t2 = Color(0xD6F2F3F5),
    t3 = Color(0xA3F2F3F5),
    div1 = Color(0x0FFFFFFF),
    basic = Color(0x14FFFFFF),
    basicPress = Color(0x24FFFFFF),
    filled = Color(0xFFF2F3F5),
    onFilled = Color(0xFF131417),
    green = Color(0xFF00E074),
    sun = Color(0xFFFFAE3D),
)

/**
 * 작성: 2026-11-12 18:48:00 · 박건준
 * 함수: panelColors
 * 목적: 시스템 낮·밤 설정에 맞는 색 토큰을 고른다.
 * 반환: 색 토큰
 */
@Composable
fun panelColors(): PanelColors = if (isSystemInDarkTheme()) DARK else LIGHT

/**
 * 작성: 2026-11-12 18:48:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 변수: BAR_BLACK, BAR_HEARD
 * 목적: 듣는 중 판·토스트 바탕과, 들은 뒤 마이크 칸·토스트 체크 칸 색. 낮·밤
 *       모두 같다.
 * 근거: 인용 — 시안 template2.html `.voice`·`.toast` #131417, `.voice.heard
 *       .mc`·`.toast .tc` #2A2C31
 */
val BAR_BLACK = Color(0xFF131417)
val BAR_HEARD = Color(0xFF2A2C31)

/**
 * 작성: 2026-11-12 18:48:00 · 박건준
 * 변수: AstaSans
 * 목적: 패널 글꼴. 가변 글꼴 하나에서 쓰는 굵기(400·700·800)를 꺼낸다.
 * 근거: 인용 — docs/SCREEN.md 1장 뼈대와 크기(Title M ExtraBold, Body L).
 *       Asta Sans는 SIL OFL 1.1(42dot)이라 앱에 넣어 배포할 수 있다
 *       (assets/licenses/asta-sans-OFL.txt)
 */
@OptIn(ExperimentalTextApi::class)
val AstaSans = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.asta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/**
 * 작성: 2026-11-12 18:48:00 · 박건준
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 함수: iconRes
 * 목적: 아이콘 이름을 그림 리소스로 바꾼다.
 * 인자: name — 아이콘 이름(`Fact.icon`, `QuestionRow.icon`, `Step.icon`)
 * 반환: 그림 리소스. 모르는 이름이면 null(아이콘 칸을 비운다)
 */
fun iconRes(name: String?): Int? = when (name) {
    "fuel" -> R.drawable.ic_panel_fuel
    "calendar" -> R.drawable.ic_panel_calendar
    "sun" -> R.drawable.ic_panel_sun
    "window" -> R.drawable.ic_panel_window
    "wiper" -> R.drawable.ic_panel_wiper
    "air" -> R.drawable.ic_panel_air
    "lock" -> R.drawable.ic_panel_lock
    "mirror" -> R.drawable.ic_panel_mirror
    "child" -> R.drawable.ic_panel_child
    "blind" -> R.drawable.ic_panel_blind
    else -> null
}
