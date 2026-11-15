package com.westone.sunstone.ui.panel

import com.westone.sunstone.routines.dialog.Fact
import com.westone.sunstone.routines.dialog.Reply
import com.westone.sunstone.routines.engine.RoutineId

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
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: PanelPress
 * 목적: 카드 버튼·줄·닫기가 하는 일.
 *       - `Answer` — 질문에 그 대답을 한다(닫기는 `Reply.NO`)
 *       - `Finish` — 켜진 세차 모드를 지금 끝낸다
 *       - `Hide` — 진행 카드만 내린다(루틴은 그대로)
 *       - `Routine` — 부르는 카드의 루틴 줄(켜기·원래대로)
 *       - `AddRoutine` — [+ 루틴 추가]
 *       - `DemoPick`·`Scenario`·`SceneNext`·`SceneRestart` — 시연 줄
 *       - `Close` — 부르는 카드를 닫는다
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

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: Routine
     * 목적: 부르는 카드의 루틴 줄. 꺼져 있으면 켜고, 켜져 있으면 원래대로
     *       돌린다.
     * 인자: id — 루틴
     *       on — 지금 켜져 있으면 true
     */
    data class Routine(val id: RoutineId, val on: Boolean) : PanelPress

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: AddRoutine
     * 목적: [+ 루틴 추가]. 아직 만들기 화면이 없어 준비 중이라고만 알린다.
     */
    data object AddRoutine : PanelPress

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: DemoPick
     * 목적: "시연" 줄. 카드를 시나리오 고르기로 바꾼다.
     */
    data object DemoPick : PanelPress

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: Scenario
     * 목적: 시나리오 고르기 줄. 그 시나리오를 첫 장면부터 시작한다.
     * 인자: number — 시나리오 번호(1부터)
     */
    data class Scenario(val number: Int) : PanelPress

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: SceneNext
     * 목적: 시연 중 [다음 장면].
     */
    data object SceneNext : PanelPress

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: SceneRestart
     * 목적: 시연 중 [처음]. 시나리오 고르기 전으로 돌린다.
     */
    data object SceneRestart : PanelPress

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: Close
     * 목적: 부르는 카드(시나리오 고르기 포함)를 닫는다.
     */
    data object Close : PanelPress

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 클래스: Place
     * 목적: 목적지 후보 줄. 그 후보로 길안내를 시작한다.
     * 인자: index — 후보 번호(0부터)
     */
    data class Place(val index: Int) : PanelPress

    /**
     * 작성: 2026-11-14 17:00:00 · 박건준
     * 클래스: PlaceCancel
     * 목적: 목적지 고르기 카드의 닫기. 고르기를 그만둔다.
     */
    data object PlaceCancel : PanelPress
}

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: ActionUi
 * 목적: 카드 아래 버튼 하나.
 * 인자: label — 버튼 글자
 *       filled — 채운 버튼(검정)이면 true, 회색 버튼(basic)이면 false
 *       press — 누르면 하는 일
 *       waitMs — 0보다 크면 버튼 안에 이 시간(밀리초) 동안 흰색 18%가 왼쪽부터
 *       차오른다. 다 차면 routines가 그 버튼을 고른 것으로 본다
 *       picked — 고른 버튼이면 true(눌린 모습으로 1초 남는다)
 *       dim — 지금은 눌러도 하는 일이 없으면 true(글자를 흐리게. 장면 준비
 *       중·마지막 장면의 [다음 장면])
 */
data class ActionUi(
    val label: String,
    val filled: Boolean,
    val press: PanelPress,
    val waitMs: Long = 0,
    val picked: Boolean = false,
    val dim: Boolean = false,
)

/**
 * 작성: 2026-11-13 20:00:00 · 박건준
 * 클래스: RowUi
 * 목적: 부르는 카드의 줄 하나(높이 112, 회색). 루틴 줄과 시나리오 고르기 줄이
 *       같은 모양을 쓴다.
 * 인자: title — 이름(30/38 Bold)
 *       sub — 아래 작은 글자(켜는 말). 없으면 null
 *       icon — 왼쪽 칸 아이콘 이름. 번호 칸이면 null
 *       number — 왼쪽 칸 검은 번호(시나리오). 아이콘 칸이면 null
 *       on — 켜져 있으면 true(오른쪽에 "켜짐")
 *       press — 누르면 하는 일
 * 근거: 인용 — 앱 화면 시안 sunstone-app.html `.crow`, `.crow .no`, `i.on`
 */
data class RowUi(
    val title: String,
    val sub: String?,
    val icon: String?,
    val number: Int?,
    val on: Boolean,
    val press: PanelPress,
)

/**
 * 작성: 2026-11-13 20:00:00 · 박건준
 * 클래스: DemoUi
 * 목적: 부르는 카드 [+ 루틴 추가] 아래 시연 줄.
 *       - `Entry` — 시연 전: 작은 줄 "시연"(높이 72)
 *       - `Playing` — 시연 중: 장면 이름·위치 | [다음 장면] | [처음](높이 88)
 * 근거: 인용 — 앱 화면 시안 sunstone-app.html `.more`, `.dfoot`
 */
sealed interface DemoUi {
    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: Entry
     * 목적: 시연 전 "시연" 줄. 누르면 시나리오 고르기로 바뀐다.
     */
    data object Entry : DemoUi

    /**
     * 작성: 2026-11-13 20:00:00 · 박건준
     * 클래스: Playing
     * 목적: 시연 중 줄.
     * 인자: title — 지금 장면("S3 · 대답")
     *       where — 시나리오와 장면 위치("출장 가는 길 · 9장면 중 3")
     *       actions — [다음 장면](검정, 폭 200)과 [처음](회색, 폭 140)
     */
    data class Playing(val title: String, val where: String, val actions: List<ActionUi>) : DemoUi
}

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: CardUi
 * 목적: 왼쪽 주행 화면 위에 뜨는 카드 하나. 질문(제안), 루틴 진행(맞추는
 *       중·준비됨), 부르는 카드, 켜짐 표시가 같은 카드 자리를 쓴다. 켜짐
 *       표시가 아니면 미디어 줄 위까지 왼쪽 구역을 다 쓴다.
 * 인자: title — 제목
 *       detail — 설명(최대 2줄). 비어 있으면 그리지 않는다
 *       emphasis — 설명 안에서 굵게 보일 낱말. 없으면 null
 *       facts — 근거 칸들
 *       cells — 할 일 칸들
 *       rows — 줄들(부르는 카드의 루틴 줄, 시나리오 고르기 줄)
 *       actions — 버튼들. 비어 있으면 버튼 줄을 그리지 않는다
 *       addRoutine — [+ 루틴 추가]를 두면 true
 *       demo — 시연 줄. 없으면 null
 *       close — 닫기 칸을 눌렀을 때 하는 일
 *       chip — 켜짐 표시(높이 88 한 줄)면 true
 *       key — 같은 카드인지 가르는 값. 바뀌면 기다리는 버튼을 처음부터 채운다
 * 근거: 인용 — docs/SCREEN.md 6장 카드 짜임, 시안 template2.html `.noti`,
 *       앱 화면 시안 sunstone-app.html `.noti.show { bottom }`
 */
data class CardUi(
    val title: String,
    val detail: String,
    val emphasis: String? = null,
    val facts: List<Fact> = emptyList(),
    val cells: List<CellUi> = emptyList(),
    val rows: List<RowUi> = emptyList(),
    val actions: List<ActionUi> = emptyList(),
    val addRoutine: Boolean = false,
    val demo: DemoUi? = null,
    val close: PanelPress? = null,
    val chip: Boolean = false,
    val key: Any? = null,
)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: ListenState
 * 목적: 듣는 중 줄의 상태.
 *       - `OFF` — 줄 없음
 *       - `LISTENING` — 대답을 기다림(초록 마이크 칸, 움직이는 파형)
 *       - `HEARD` — 들은 말을 보여 줌(회색 마이크 칸, 멈춘 파형)
 *       - `WORKING` — 들은 말을 처리하는 중(회색 칸에 도는 표시, 글자는 그대로)
 * 근거: 인용 — docs/SCREEN.md 1장 상태 흐름. 2026-11-13 박건준 결정: 내비가
 *       경유지를 넣는 동안 들은 말을 내리지 않고 마이크 칸 자리에 도는 표시
 */
enum class ListenState { OFF, LISTENING, HEARD, WORKING }

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: ListenUi
 * 목적: 듣는 중 줄 하나.
 * 인자: state — 줄 상태
 *       text — 큰 글자("듣고 있어요", 들은 말)
 *       next — 들은 말 아래 작은 글자(앱이 이어서 할 일). 없으면 null
 */
data class ListenUi(val state: ListenState, val text: String = "", val next: String? = null)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 수정: 2026-11-13 11:00:00 · 박희정
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: ScreenUi
 * 목적: 왼쪽 구역에 그릴 것 전부. 카드, 듣는 중 줄, 토스트, 마이크 칸.
 *       듣는 중 줄과 토스트는 같은 줄을 번갈아 쓴다(`plate`). 그 줄은 카드
 *       맨 아래(켜짐 표시면 그 아래)에 붙고, 카드가 없으면 카드 자리에 혼자
 *       뜬다.
 * 인자: card — 카드. 없으면 null
 *       listen — 듣는 중 줄
 *       toast — 토스트. 없으면 null
 */
data class ScreenUi(val card: CardUi?, val listen: ListenUi, val toast: Plate.Toast?) {

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: plate
     * 목적: 듣는 중 줄에 무엇을 보일지 고른다. 들은 말 → 토스트 → 처리
     *       중·듣는 중 순서다. 들은 말을 1초 보인 뒤 처리 중(도는 표시)이었다가
     *       결과 토스트로 바뀐다.
     * 반환: 줄에 보일 것. 없으면 null
     * 근거: 인용 — 시안 template2.html `toast()`는 듣는 중을 내리고 같은
     *       자리에 뜬다
     */
    val plate: Plate?
        get() = when {
            listen.state == ListenState.HEARD -> Plate.Listen(listen)
            toast != null -> toast
            listen.state != ListenState.OFF -> Plate.Listen(listen)
            else -> null
        }

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 함수: mic
     * 목적: 마이크 칸을 보일 때인지 본다. 카드나 듣는 중 줄(토스트 포함)이 떠
     *       있으면 숨긴다. 줄만 뜰 때도 카드 자리(위 176)가 마이크 칸과 겹친다.
     * 반환: 보이면 true
     * 근거: 인용 — 시안 template2.html `.drive.busy .mic`
     */
    val mic: Boolean get() = card == null && plate == null
}

/**
 * 작성: 2026-11-13 03:40:00 · 박건준
 * 수정: 2026-11-13 20:00:00 · 박건준
 * 클래스: Plate
 * 목적: 듣는 중 줄에 보일 것 하나. 듣는 중이거나 토스트다.
 */
sealed interface Plate {
    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 20:00:00 · 박건준
     * 클래스: Listen
     * 목적: 듣는 중(들은 말, 처리 중 포함).
     * 인자: listen — 줄 값
     */
    data class Listen(val listen: ListenUi) : Plate

    /**
     * 작성: 2026-11-13 03:40:00 · 박건준
     * 수정: 2026-11-13 11:00:00 · 박희정
     * 클래스: Toast
     * 목적: 토스트.
     * 인자: text — 토스트 문장
     *       alert — 경고(`Asking.alerts`)면 true. 체크 대신 경고 아이콘을 그린다
     */
    data class Toast(val text: String, val alert: Boolean = false) : Plate
}
