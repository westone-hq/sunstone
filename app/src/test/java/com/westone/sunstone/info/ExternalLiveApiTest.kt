package com.westone.sunstone.info

import com.westone.sunstone.routines.model.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** 클래스: ExternalLiveApiTest
 * 목적: 재발급 키가 로컬에 공급됐을 때만 실제 인증 API를 검증한다.
 * 미구현: 키가 없으면 이 테스트는 건너뛴다. 통과로 보고하지 않는다.
 */
class ExternalLiveApiTest {
    /** 함수: credentials
     * 목적: 키를 명령줄·테스트 보고서에 쓰지 않고 로컬 파일에서 읽는다.
     */
    private fun credentials(): ApiKeys {
        assumeTrue("실제 인증 API 테스트는 명시적으로 활성화해야 함", System.getenv("SUNSTONE_LIVE_APIS") == "1")
        val file = File(requireNotNull(System.getProperty("sunstone.localProperties")))
        val keys = ApiKeys(file)
        assumeTrue("재발급 카카오 키 미설정", keys.read("sunstone.kakao.restKey") != null)
        return keys
    }

    @Test fun `실제 카카오 목적지 검색`() = runBlocking {
        val keys = credentials()
        val api = KakaoPlaces({ keys.read("sunstone.kakao.restKey") })
        for (query in listOf("강남역", "파라다이스 호텔 부산", "서울역")) {
            val rows = api.search(query)
            assertTrue("실제 검색 후보 없음: $query", rows.isNotEmpty())
            assertTrue(rows.all { it.address.isNotBlank() })
        }
    }

    @Test fun `실제 카카오 주변 주유소 검색`() = runBlocking {
        val keys = credentials()
        val api = KakaoPlaces({ keys.read("sunstone.kakao.restKey") })
        val rows = api.nearbyStations(GeoPoint(37.4852, 127.0407))
        assertTrue("주변 주유소 후보 없음", rows.isNotEmpty())
        assertTrue(rows.all { it.priceWon == null && !it.facilityKnown })
        assertTrue(rows.all { it.distanceM != null && it.distanceM!! in 0..5000 })
    }
}
