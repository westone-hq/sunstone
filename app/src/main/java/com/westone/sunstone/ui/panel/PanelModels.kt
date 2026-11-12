package com.westone.sunstone.ui.panel

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: RowState
 * 목적: 패널 항목 줄 하나의 상태. 칸 색과 오른쪽 표시가 이것으로 정해진다.
 *       - `TODO` — 아직 안 함(회색 칸)
 *       - `NOW` — 지금 하는 중(흰 칸, 왼쪽 검은 막대, 도는 표시)
 *       - `DONE` — 앱이 차를 바꿈(흰 칸, 체크)
 *       - `MANUAL` — 사람이 해야 함(흰 칸, "직접 ~ 주세요")
 * 근거: 인용 — docs/SCREEN.md 1장 "칸이 뜻하는 것"
 */
enum class RowState { TODO, NOW, DONE, MANUAL }

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: RowUi
 * 목적: 패널 항목 줄 하나.
 * 인자: icon — 아이콘 이름(`PanelIcons`가 그림으로 바꾼다). 없으면 null
 *       text — 줄 글자
 *       state — 줄 상태
 *       note — 오른쪽 작은 글자("세차 가능", "직접 접어 주세요"). 없으면 null
 */
data class RowUi(val icon: String?, val text: String, val state: RowState, val note: String? = null)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: PanelAction
 * 목적: 패널 버튼·닫기가 하는 일.
 *       - `YES` — 질문을 받아들인다
 *       - `NO` — 질문을 거절한다(닫기와 같다)
 *       - `FINISH` — 켜진 세차 모드를 지금 끝낸다
 *       - `HIDE` — 진행 패널만 내린다(루틴은 그대로)
 */
enum class PanelAction { YES, NO, FINISH, HIDE }

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: ActionUi
 * 목적: 패널 아래 버튼 하나.
 * 인자: label — 버튼 글자
 *       filled — 채운 버튼(검정)이면 true, 흰 버튼이면 false
 *       action — 누르면 하는 일
 */
data class ActionUi(val label: String, val filled: Boolean, val action: PanelAction)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: Progress
 * 목적: 진행선 값. 설명줄 아래 검은 선 길이가 done/total이다.
 * 인자: done — 맞춘 항목 수
 *       total — 앱이 맞출 항목 수(사람이 할 줄은 뺀다)
 */
data class Progress(val done: Int, val total: Int)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: PanelUi
 * 목적: 알림 패널 하나. 질문(제안)과 루틴 진행(맞추는 중·준비됨)이 같은
 *       뼈대를 쓴다.
 * 인자: title — 제목
 *       detail — 설명줄. 비어 있으면 그리지 않는다
 *       progress — 진행선. 진행 중이 아니면 null
 *       rows — 항목 줄들
 *       actions — 버튼들. 비어 있으면 버튼 줄을 그리지 않는다
 *       close — 닫기 칸을 눌렀을 때 하는 일
 * 근거: 인용 — docs/SCREEN.md 1장 뼈대와 크기
 */
data class PanelUi(
    val title: String,
    val detail: String,
    val progress: Progress?,
    val rows: List<RowUi>,
    val actions: List<ActionUi>,
    val close: PanelAction,
)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: ListenState
 * 목적: 듣는 중 막대의 상태.
 *       - `OFF` — 막대 없음(작은 마이크 칸만)
 *       - `LISTENING` — 대답을 기다림(초록 마이크 칸, 움직이는 파형)
 *       - `HEARD` — 들은 말을 보여 줌(회색 마이크 칸, 멈춘 파형)
 * 근거: 인용 — docs/SCREEN.md 1장 상태 흐름
 */
enum class ListenState { OFF, LISTENING, HEARD }

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: ListenUi
 * 목적: 듣는 중 막대 하나.
 * 인자: state — 막대 상태
 *       text — 큰 글자("듣고 있어요", 들은 말)
 *       hint — 작은 글자(말하는 법 안내). 없으면 null
 */
data class ListenUi(val state: ListenState, val text: String = "", val hint: String? = null)

/**
 * 작성: 2026-11-12 18:40:00 · 박건준
 * 클래스: ScreenUi
 * 목적: 왼쪽 구역에 그릴 것 전부. 패널, 듣는 중 막대, 토스트.
 * 인자: panel — 알림 패널. 없으면 null
 *       listen — 듣는 중 막대
 *       toast — 토스트 문장. 없으면 null
 */
data class ScreenUi(val panel: PanelUi?, val listen: ListenUi, val toast: String?)
