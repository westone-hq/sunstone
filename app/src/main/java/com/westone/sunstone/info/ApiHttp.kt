package com.westone.sunstone.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.net.HttpURLConnection

/** 클래스: ApiHttp
 * 목적: 인증 요청의 URL·본문·키를 로그나 예외 메시지로 노출하지 않는다.
 */
class ApiHttp {
    /** 함수: get
     * 목적: 인증 HTTP를 UI 밖에서 실행하고 응답을 제한·검사한다.
     */
    suspend fun get(
        base: String,
        parameters: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        } // 키가 포함된 전체 URL은 출력하지 않는다
        val connection = URI("$base?$query").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 4_000
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val status = connection.responseCode // 숫자만 실패 사유로 전달
            check(status == 200) { "API HTTP $status" }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096) // 서버 오류가 무한히 큰 응답을 만들지 못하게 한다
                val result = StringBuilder()
                while (true) {
                    val size = reader.read(buffer)
                    if (size < 0) break
                    check(result.length + size <= 1_000_000) { "API 응답 크기 오류" }
                    result.append(buffer, 0, size)
                }
                result.toString()
            }
            JSONObject(body)
        } catch (e: Exception) {
            // 원래 예외에는 오피넷 certkey가 포함될 수 있어 cause도 외부에 전달하지 않는다.
            throw IllegalStateException(if (e.message?.startsWith("API HTTP ") == true) e.message else "API 요청 실패")
        } finally {
            connection.disconnect()
        }
    }

    /** 함수: encode
     * 목적: 검색어·키·좌표를 URL 쿼리 값으로 인코딩한다.
     */
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
