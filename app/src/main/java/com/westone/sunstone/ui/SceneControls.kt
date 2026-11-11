package com.westone.sunstone.ui

import kotlinx.coroutines.flow.StateFlow

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 클래스: SceneControls
 * 목적: 시연 모드(Tab 1 4.2 실행 방식 1) 창구. 화면은 이 값만 보고 [다음 장면]
 *       버튼과 장면 대사 버튼을 그린다. 구현은 debug 빌드의 `ScenePlayer`이고,
 *       출시 빌드에서는 없다(`AppGraph.scenes`가 null).
 */
interface SceneControls {
    /** 지금 장면. 아직 시작하지 않았으면 null */
    val current: StateFlow<SceneView?>

    /** 다음 장면으로 간다. 준비 상황을 넣고 대사를 띄운다 */
    fun next()

    /** 처음(S1 전)으로 돌린다. 켜진 루틴을 되돌리고 길안내·대화 기록을 지운다 */
    fun restart()
}

/**
 * 작성: 2026-11-11 16:42:25 · 박희정
 * 클래스: SceneView
 * 목적: 화면에 보일 장면 한 줄.
 * 인자: id — 장면 ID("S3")
 *       title — 장면 이름
 *       line — 운전자 대사. 말 없는 장면이면 null
 *       busy — 준비 상황을 넣는 중이면 true(버튼을 막는다)
 *       last — 마지막 장면이면 true
 */
data class SceneView(val id: String, val title: String, val line: String?, val busy: Boolean, val last: Boolean)
