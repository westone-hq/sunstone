package com.westone.sunstone.info

import java.io.File
import java.util.Properties

/**
 * 작성: 2026-11-14 13:41:10 · 박희정
 * 수정: 2026-11-14 17:00:00 · 박건준
 * 클래스: ApiKeys
 * 목적: 바깥 API 키를 APK·소스에 넣지 않고 기기 안 설정 파일에서 읽는다.
 *       파일은 `tools/configure-api.ps1`이 `local.properties` 값으로 넣는다.
 *       키: `sunstone.kakao.restKey`, `sunstone.opinet.key`,
 *       `sunstone.fuel.code`(휘발유 B027, 경유 D047)
 * 인자: files — 차례로 찾아볼 설정 파일들(앱 비공개 폴더, 앱 바깥 저장소 폴더)
 * 미구현: 프로토타입이라 제출 APK에는 키가 없다. 키를 넣지 않으면 실제
 *       검색·가격은 쓰지 못하고 시연 모드의 준비 값만 쓴다
 */
class ApiKeys(private vararg val files: File?) {
    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 수정: 2026-11-14 17:00:00 · 박건준
     * 함수: read
     * 목적: 설정 파일들에서 값을 찾는다. 매번 다시 읽어, 키를 바꿔 넣으면 앱을
     *       다시 켜지 않아도 쓰인다. 값과 파일 내용은 로그에 남기지 않는다.
     * 인자: name — 키 이름
     * 반환: 값. 어느 파일에도 없거나 비어 있으면 null
     */
    fun read(name: String): String? = files.firstNotNullOfOrNull { it?.let { file -> read(file, name) } }

    /**
     * 작성: 2026-11-14 13:41:10 · 박희정
     * 함수: read
     * 목적: 설정 파일 하나에서 값을 읽는다.
     * 인자: file — 설정 파일
     *       name — 키 이름
     * 반환: 값. 파일이 없거나 값이 비어 있으면 null
     */
    private fun read(file: File, name: String): String? {
        if (!file.isFile) return null
        return try {
            val properties = Properties() // 키 값은 메모리에서만 사용
            file.reader(Charsets.UTF_8).use { properties.load(it) }
            properties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() && !it.contains('\n') }
        } catch (_: java.io.IOException) { null }
        catch (_: IllegalArgumentException) { null }
    }
}
