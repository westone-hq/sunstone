package com.westone.sunstone.routines.fake

import com.westone.sunstone.routines.model.GasStation
import com.westone.sunstone.routines.model.GeoPoint
import com.westone.sunstone.routines.model.RainOutlook
import com.westone.sunstone.routines.port.InfoPort

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-14 16:00:00 · 박건준
 * 클래스: FakeInfo
 * 목적: 메모리 속 가짜 바깥 정보. 단위 테스트에 쓴다.
 * 인자: stations — 근처 주유소로 돌려줄 목록
 *       rain — 비 소식(있음 true, 없음 false, 모름 null)
 *       rainDay — 비 소식이 있을 때 비가 오는 날(오늘 0, 내일 1 …)
 */
class FakeInfo(
    var stations: List<GasStation>,
    var rain: Boolean? = false,
    var rainDay: Int = 1,
) : InfoPort {

    /** 날씨를 마지막으로 물은 곳. 지금 위치로 물었거나 묻지 않았으면 null */
    var asked: GeoPoint? = null

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: gasStations
     * 목적: 넣어 둔 주유소 목록을 돌려준다.
     */
    override suspend fun gasStations() = stations

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-14 16:00:00 · 박건준
     * 함수: rain
     * 목적: 넣어 둔 비 소식을 돌려준다. `rainDay`가 볼 기간 밖이면 비가 없다고
     *       한다. 물은 곳을 `asked`에 남긴다.
     */
    override suspend fun rain(point: GeoPoint?, days: Int): RainOutlook? {
        asked = point
        return when (rain) {
            null -> null
            false -> RainOutlook(null)
            true -> RainOutlook(rainDay.takeIf { it <= days })
        }
    }
}
