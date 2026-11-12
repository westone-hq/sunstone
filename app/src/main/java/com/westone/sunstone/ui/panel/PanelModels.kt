package com.westone.sunstone.ui.panel

import com.westone.sunstone.routines.dialog.Fact
import com.westone.sunstone.routines.dialog.Reply

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 클래스: CellState
 * 목적: 카드 할 일 칸 하나의 상태. 칸 색과 표시가 이것으로 정해진다.
 *       - `TODO` — 아직 안 함(회색 칸)
 *       - `NOW` — 지금 하는 중(흰 칸, 위 검은 막대 4)
 *       - `DONE` — 앱이 차를 바꿈(흰 칸, 오른쪽 위 체크 30)
 *       - `MANUAL` — 사람이 해야 함(흰 칸, 아래 "직접")
 * 근거: 인용 — docs/SCREEN.md 6장 할 일 칸, 시안 template2.html `.cell`
 */
enum class CellState { TODO, NOW, DONE, MANUAL }

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 클래스: CellUi
 * 목적: 카드 할 일 칸 하나.
 * 인자: icon — 아이콘 이름(`iconRes()`가 그림으로 바꾼다). 없으면 null
 *       text — 칸 글자("창문")
 *       state — 칸 상태
 *       note — 칸 아래 작은 글자("직접"). 없으면 null
 */
data class CellUi(val icon: String?, val text: String, val state: CellState, val note: String? = null)

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 클래스: PanelPress
 * 목적: 카드 버튼·닫기가 하는 일.
 *       - `Answer` — 질문에 그 대답을 한다(닫기는 `Reply.NO`)
 *       - `Finish` — 켜진 세차 모드를 지금 끝낸다
 *       - `Hide` — 진행 카드만 내린다(루틴은 그대로)
 */
sealed interface PanelPress {
    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 클래스: Answer
     * 목적: 질문에 대답한다.
     * 인자: reply — 대답 값
     */
    data class Answer(val reply: Reply) : PanelPress

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 클래스: Finish
     * 목적: 켜진 세차 모드를 지금 끝낸다.
     */
    data object Finish : PanelPress

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 클래스: Hide
     * 목적: 진행 카드만 내린다. 루틴은 그대로 둔다.
     */
    data object Hide : PanelPress
}

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 클래스: ActionUi
 * 목적: 카드 아래 버튼 하나.
 * 인자: label — 버튼 글자
 *       filled — 채운 버튼(검정)이면 true, 회색 버튼(basic)이면 false
 *       press — 누르면 하는 일
 *       waitMs — 0보다 크면 버튼 안에 이 시간(밀리초) 동안 흰색 18%가 왼쪽부터
 *       차오른다. 다 차면 routines가 그 버튼을 고른 것으로 본다
 *       picked — 고른 버튼이면 true(눌린 모습으로 1초 남는다)
 */
data class ActionUi(
    val label: String,
    val filled: Boolean,
    val press: PanelPress,
    val waitMs: Long = 0,
    val picked: Boolean = false,
)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 클래스: CardUi
 * 목적: 왼쪽 주행 화면 위에 뜨는 카드 하나. 질문(제안)과 루틴 진행(맞추는
 *       중·준비됨), 켜짐 표시가 같은 카드 자리를 쓴다.
 * 인자: title — 제목
 *       detail — 설명(최대 2줄). 비어 있으면 그리지 않는다
 *       emphasis — 설명 안에서 굵게 보일 낱말. 없으면 null
 *       facts — 근거 칸들
 *       cells — 할 일 칸들
 *       actions — 버튼들. 비어 있으면 버튼 줄을 그리지 않는다
 *       close — 닫기 칸을 눌렀을 때 하는 일
 *       chip — 켜짐 표시(높이 88 한 줄)면 true
 *       key — 같은 카드인지 가르는 값. 바뀌면 기다리는 버튼을 처음부터 채운다
 * 근거: 인용 — docs/SCREEN.md 6장 카드 짜임, 시안 template2.html `.noti`
 */
data class CardUi(
    val title: String,
    val detail: String,
    val emphasis: String? = null,
    val facts: List<Fact> = emptyList(),
    val cells: List<CellUi> = emptyList(),
    val actions: List<ActionUi> = emptyList(),
    val close: PanelPress? = null,
    val chip: Boolean = false,
    val key: Any? = null,
)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: ListenState
 * 목적: 듣는 중 판의 상태.
 *       - `OFF` — 판 없음
 *       - `LISTENING` — 대답을 기다림(초록 마이크 칸, 움직이는 파형)
 *       - `HEARD` — 들은 말을 보여 줌(회색 마이크 칸, 멈춘 파형)
 * 근거: 인용 — docs/SCREEN.md 1장 상태 흐름
 */
enum class ListenState { OFF, LISTENING, HEARD }

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-12 22:45:00 · 박건준
 * 클래스: ListenUi
 * 목적: 듣는 중 판 하나.
 * 인자: state — 판 상태
 *       text — 큰 글자("듣고 있어요", 들은 말)
 *       next — 들은 말 아래 작은 글자(앱이 이어서 할 일). 없으면 null
 */
data class ListenUi(val state: ListenState, val text: String = "", val next: String? = null)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 03:40:00 · 박건준
 * 클래스: ScreenUi
 * 목적: 왼쪽 구역에 그릴 것 전부. 카드, 듣는 중 판, 토스트, 마이크 칸.
 *       판과 토스트는 같은 자리를 번갈아 쓴다(`plate`).
 * 인자: card — 카드. 없으면 null
 *       listen — 듣는 중 판
 *       toast — 토스트 문장. 없으면 null
 */
data class ScreenUi(val card: CardUi?, val listen: ListenUi, val toast: String?) {

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 함수: plate
     * 목적: 아래 판 자리에 무엇을 보일지 고른다. 들은 말 → 토스트 → 듣는 중
     *       순서다. 들은 말을 1초 보인 뒤 결과 토스트가 이어 나온다.
     * 반환: 판에 보일 것. 없으면 null
     * 근거: 인용 — 시안 template2.html `toast()`는 판을 내리고 같은 자리에 뜬다
     */
    val plate: Plate?
        get() = when {
            listen.state == ListenState.HEARD -> Plate.Listen(listen)
            toast != null -> Plate.Toast(toast)
            listen.state == ListenState.LISTENING -> Plate.Listen(listen)
            else -> null
        }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 함수: mic
     * 목적: 마이크 칸을 보일 때인지 본다. 카드나 듣는 중 판이 떠 있으면 숨긴다.
     * 반환: 보이면 true
     * 근거: 인용 — 시안 template2.html `.drive.busy .mic`
     */
    val mic: Boolean get() = card == null && listen.state == ListenState.OFF
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 클래스: Plate
 * 목적: 아래 판 자리에 보일 것 하나. 듣는 중 판이거나 토스트다.
 */
sealed interface Plate {
    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 클래스: Listen
     * 목적: 듣는 중 판.
     * 인자: listen — 판 값
     */
    data class Listen(val listen: ListenUi) : Plate

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 클래스: Toast
     * 목적: 토스트.
     * 인자: text — 토스트 문장
     */
    data class Toast(val text: String) : Plate
}
