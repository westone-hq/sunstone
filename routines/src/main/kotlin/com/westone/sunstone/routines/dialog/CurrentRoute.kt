package com.westone.sunstone.routines.dialog

import com.westone.sunstone.routines.model.PlaceHit

/**
 * 작성: 2026-11-12 16:10:00 · 박희정
 * 수정: 2026-11-14 03:00:00 · 박건준
 * 클래스: CurrentRoute
 * 목적: 지금 길안내 목적지를 기억한다. 길안내 대화가 경로를 시작하면 넣고,
 *       차일드락이 "집에 가는 길"인지 볼 때 읽는다. 둘이 같은 것을 쓴다.
 * 근거: 인용 — 시연 시나리오 ② #5 "집 판단: 내비 목적지"
 */
class CurrentRoute {

    /** 지금 길안내 목적지. 길안내 대화가 경로를 시작하면 넣고, 새 길안내·시연 처음이면 지운다. 없으면 null */
    @Volatile
    var destination: PlaceHit? = null
        private set

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: start
     * 목적: 경로를 시작한 목적지를 넣는다.
     * 인자: place — 목적지
     */
    fun start(place: PlaceHit) {
        destination = place
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: clear
     * 목적: 목적지를 지운다. 새 길안내를 시작하거나 시연을 처음부터 할 때 부른다.
     */
    fun clear() {
        destination = null
    }
}
