package com.westone.sunstone.routines.fake

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.port.InfoPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 클래스: FakeInfo
 * 목적: 메모리 속 가짜 바깥 정보. 단위 테스트에 쓴다.
 * 인자: stations — 근처 주유소로 돌려줄 목록
 *       rain — 비 소식(있음 true, 없음 false, 모름 null)
 */
class FakeInfo(
    var stations: List<GasStation>,
    var rain: Boolean? = false,
) : InfoPort {
    override suspend fun gasStations() = stations
    override suspend fun rainWithin(days: Int) = rain
}
