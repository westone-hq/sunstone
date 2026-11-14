package com.westone.sunstone.ui

import kotlinx.coroutines.flow.StateFlow

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-13 13:00:00 · 박희정
 * 클래스: SceneControls
 * 목적: 시연 모드(Tab 1 4.2 실행 방식 1) 창구. 화면은 이 값만 보고 시나리오
 *       고르기, [다음 장면]·[처음] 버튼을 그린다. 구현은 debug 빌드의 `ScenePlayer`이고,
 *       출시 빌드에서는 없다(`AppGraph.scenes`가 null).
 */
interface SceneControls {
    /** 지금 장면. 아직 시작하지 않았으면 null */
    val current: StateFlow<SceneView?>

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-13 03:50:00 · 박건준
     * 함수: next
     * 목적: 다음 장면으로 간다. 준비 상황을 넣고, 운전자 대사가 있으면 낸다.
     */
    fun next()

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 수정: 2026-11-12 15:06:00 · 박건준
     * 함수: restart
     * 목적: 처음(S1 전)으로 돌린다. 나오던 말을 끊고, 켜진 루틴을 되돌리고
     *       길안내·대화 기록을 지운다.
     */
    fun restart()

    /**
     * 작성: 2026-11-13 13:00:00 · 박희정
     * 함수: start
     * 목적: 고른 시나리오를 처음부터 시작한다. 그 시나리오 첫 장면을 준비하고,
     *       운전자 대사가 있으면 낸다.
     * 인자: scenario — 시나리오 번호(1부터, 화면 버튼 순서)
     */
    fun start(scenario: Int)
}

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 수정: 2026-11-13 13:00:00 · 박희정
 * 클래스: SceneView
 * 목적: 화면에 보일 장면 한 줄. 운전자 대사는 장면에 들어가면 저절로 나와서
 *       따로 두지 않는다.
 * 인자: id — 장면 ID("S3")
 *       title — 장면 이름
 *       busy — 준비 상황·대사를 넣는 중이면 true(버튼을 막는다)
 *       last — 그 시나리오의 마지막 장면이면 true([다음 장면]을 막는다)
 */
data class SceneView(
    val id: String, val title: String, val busy: Boolean, val last: Boolean, val canAdvance: Boolean = true,
)
