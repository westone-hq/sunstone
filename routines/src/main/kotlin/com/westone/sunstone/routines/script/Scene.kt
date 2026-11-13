package com.westone.sunstone.routines.script

import com.westone.sunstone.routines.dialog.QuestionKind
import com.westone.sunstone.routines.model.Gear
import com.westone.sunstone.routines.model.Place
import com.westone.sunstone.routines.port.Target
import com.westone.sunstone.routines.understand.VoiceAction

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-13 03:25:00 · 박건준
 * 클래스: Scene
 * 목적: 대사집 장면 하나(Tab 1 4.1 장면 형식). 자동 테스트, adb `scene`, 화면
 *       [다음 장면] 버튼이 모두 이 값을 쓴다. 대사를 고칠 때는 이것만 고친다.
 *       준비도 운전자 말도 없는 장면은 앞 장면에 이어지는 장면이다(주유 질문은
 *       길안내 말 뒤에 이어 나오고, 세차 모드는 세차 입구에서 5초 뒤 켜진다).
 * 인자: id — 장면 ID("S1")
 *       title — 장면 이름(화면에 보인다)
 *       prep — 이 장면 전에 차례로 넣을 상황. 한 단계씩 넣어야 권하기가
 *       "바뀐 순간"을 알아챈다(달리다가 → 주유소에 서기)
 *       driver — 운전자 말. 말 없이 상황만 바뀌는 장면(도착, 출발)이면 null
 *       expectSaid — 앱이 해야 하는 말, 차례대로
 *       expectCar — 장면 뒤 차에서 읽혀야 하는 값
 *       expectDestination — 장면 뒤 내비 목적지 이름. 보지 않으면 null
 *       expectWaypoints — 장면 뒤 내비 경유지 이름들. 보지 않으면 null
 *       expectNotices — 앱이 내야 하는 토스트 문장, 차례대로
 *       expectQuestion — 장면 뒤 대답을 기다리는 질문 종류. 없어야 하면 null
 *       afterMs — 앞 장면에서 이 시간(밀리초)이 지나면 일어나는 장면. 자동
 *       테스트는 시간을 그만큼 앞당긴다. 바로 일어나면 0
 * 근거: 인용 — Tab 1 4.1 장면 하나의 형식(장면 ID, 준비, 운전자 말, 기대 말,
 *       기대 변화, 고정 응답)
 */
data class Scene(
    val id: String,
    val title: String,
    val prep: List<Prep> = emptyList(),
    val driver: Utterance? = null,
    val expectSaid: List<String>,
    val expectCar: Map<Target, Any> = emptyMap(),
    val expectDestination: String? = null,
    val expectWaypoints: List<String>? = null,
    val expectNotices: List<String> = emptyList(),
    val expectQuestion: QuestionKind? = null,
    val afterMs: Long = 0,
) {
    /**
     * 작성: 2026-11-12 18:30:00 · 박건준
     * 함수: follows
     * 목적: 준비도 운전자 말도 없어 앞 장면에 이어지는 장면이면 true.
     */
    val follows: Boolean get() = prep.isEmpty() && driver == null
}

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-13 10:00:00 · 박희정
 * 클래스: Prep
 * 목적: 장면 준비 한 단계. 값이 null인 칸은 바꾸지 않는다. 가짜 차는 앱이,
 *       Pleos 차는 Virtual Console·데모 입력이 넣는다. 뒷좌석은 아이가 타는
 *       왼쪽 한 자리만 다룬다.
 * 인자: gear — 기어
 *       speedKmh — 속도(km/h)
 *       place — 지금 장소(가짜 차·데모 입력. Pleos 차는 근접 알림으로도 바뀐다)
 *       fuelPercent — 연료(%)
 *       washedDaysAgo — 마지막 세차가 며칠 전인지
 *       resetTrip — true면 길안내·대화 기록·권하기 대기를 지우고 새로 시작한다
 *       rearDoorOpen — 뒷좌석 왼쪽 문이 열렸는지
 *       rearOccupied — 뒷좌석 왼쪽에 사람이 탔는지
 *       rearBelt — 뒷좌석 왼쪽 벨트를 맸는지
 *       childLockDaysAgo — 마지막으로 차일드락을 쓴 날이 며칠 전인지. 같은 날
 *       시연을 다시 해도 처음 탈 때 묻게 1(어제)로 넣는다
 */
data class Prep(
    val gear: Gear? = null,
    val speedKmh: Float? = null,
    val place: Place? = null,
    val fuelPercent: Int? = null,
    val washedDaysAgo: Long? = null,
    val resetTrip: Boolean = false,
    val rearDoorOpen: Boolean? = null,
    val rearOccupied: Boolean? = null,
    val rearBelt: Boolean? = null,
    val childLockDaysAgo: Long? = null,
)

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 클래스: Utterance
 * 목적: 운전자 말 하나. 대표 문장과 같은 뜻 변형(음성 인식 오류 대비), 그리고
 *       알아들어야 하는 요청. 말 알아듣기가 규칙보다 먼저 이 값으로 맞춘다.
 * 인자: text — 대표 문장(화면 대사 버튼에 보인다)
 *       variants — 같은 뜻 변형 2~3개
 *       intent — 알아들어야 하는 요청
 * 근거: 인용 — Tab 1 4.2 "말 알아듣기에서는 대사집이 1순위다"
 */
data class Utterance(val text: String, val variants: List<String>, val intent: VoiceAction)
