package com.westone.sunstone.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 작성: 2026-11-09 18:53:00 · 박건준
 * 클래스: VirtualConsole
 * 목적: 에뮬레이터 Virtual Console(차 값을 바꾸고 보는 웹 도구)의 HTTP
 *       API를 앱 안에서 부른다. 콘솔은 에뮬레이터 안 8088 포트에서 돌아서
 *       앱이 `127.0.0.1`로 바로 부를 수 있다. 실측(`CarProbe`, `SeatTimer`)
 *       전용이라 debug 빌드에만 있다.
 * 근거: 측정 — 에뮬레이터 안 `netstat`에서 0.0.0.0:8088 LISTEN 확인
 */
internal object VirtualConsole {
    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 변수: BASE
     * 목적: 에뮬레이터 안에서 본 콘솔 주소.
     */
    private const val BASE = "http://127.0.0.1:8088"

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 변수: TIMEOUT_MS
     * 목적: 콘솔 연결·읽기 제한 시간(밀리초). 콘솔이 꺼져 있어도 실측이
     *       멈추지 않게 짧게 둔다.
     */
    private const val TIMEOUT_MS = 1_000

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: read
     * 목적: `/vehicle/state` JSON에서 점 경로 하나의 값을 읽는다.
     * 인자: path — `seat.positions.slide.driver` 같은 점 경로. null이면 콘솔에
     *       없는 대상
     * 반환: 값의 글자 표현. 콘솔에 없으면 `없음`, 못 읽으면 `읽기 실패: 이유`
     */
    suspend fun read(path: String?): String {
        if (path == null) return "없음"
        return runCatching {
            val text = request("GET", "/vehicle/state") // 콘솔 상태 JSON 원문
            path.split('.').fold<String, Any?>(JSONObject(text)) { node, key -> (node as? JSONObject)?.opt(key) }
                ?.toString() ?: "없음"
        }.getOrElse { "읽기 실패: ${it.javaClass.simpleName}" }
    }

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: setSeat
     * 목적: 콘솔에서 시트 축 하나의 위치를 바로 바꾼다. 실측 전 시작 위치를
     *       맞추고, 끝난 뒤 원래대로 돌리는 데 쓴다.
     * 인자: control — 콘솔 축 이름(slide·height·recline·tilt·lumbar)
     *       seat — 콘솔 좌석 이름(driver 등)
     *       value — 위치(0~100)
     * 반환: 성공하면 true
     */
    suspend fun setSeat(control: String, seat: String, value: Int): Boolean = runCatching {
        request("PUT", "/vehicle/seat?kind=position&control=$control&area=$seat&value=$value")
    }.isSuccess

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: keepAlive
     * 목적: 콘솔에 브라우저 세션 신호를 보낸다. 3초 넘게 끊기면 차량 쪽 연결이
     *       끊겨 내기 순환·문 잠금 제어가 실패하므로 시연 중 1초마다 보낸다.
     * 반환: 성공하면 true
     * 근거: 측정 — 신호가 끊긴 뒤 내기 순환 Failed, 문 잠금 3초 응답 없음.
     *       신호를 보내는 동안에는 둘 다 Done(2026-11-11 routine/wash)
     */
    suspend fun keepAlive(): Boolean = runCatching { request("POST", "/browser/session?client=sunstone") }.isSuccess

    /**
     * 작성: 2026-11-11 16:42:25 · 박희정
     * 함수: put
     * 목적: 콘솔 값을 바꾼다(기어, 속도). 세션 신호를 먼저 보낸다.
     * 인자: path — 콘솔 경로("/vehicle/gear?gear=Park")
     * 반환: 성공하면 true
     */
    suspend fun put(path: String): Boolean = runCatching {
        request("POST", "/browser/session?client=sunstone")
        request("PUT", path)
    }.isSuccess

    /**
     * 작성: 2026-11-09 18:53:00 · 박건준
     * 함수: request
     * 목적: 콘솔에 HTTP 요청 하나를 보내고 응답 본문을 받는다.
     * 인자: method — GET, PUT, 또는 POST(세션 신호)
     *       path — `/vehicle/...` 경로와 질의
     * 반환: 응답 본문. 연결 실패나 2xx가 아닌 응답이면 예외
     */
    private suspend fun request(method: String, path: String): String = withContext(Dispatchers.IO) {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection // 콘솔 요청
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
