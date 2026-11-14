package com.westone.sunstone.info

import java.io.File
import java.util.Properties

/** 클래스: ApiKeys
 * 목적: APK·소스에 키를 넣지 않고 앱 비공개 로컬 설정에서 읽는다.
 * 인자: file — files/api.properties. 개발 중에는 설정 도구로 공급한다.
 */
class ApiKeys(private val file: File) {
    /** 함수: read
     * 목적: 설정을 매번 다시 읽어 재발급한 키를 앱 재빌드 없이 적용한다.
     * 반환: 없거나 비어 있으면 null. 값·파일 내용을 로그에 남기지 않는다.
     */
    fun read(name: String): String? {
        if (!file.isFile) return null
        return try {
            val properties = Properties() // 키 값은 메모리에서만 사용
            file.reader(Charsets.UTF_8).use { properties.load(it) }
            properties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() && !it.contains('\n') }
        } catch (_: java.io.IOException) { null }
        catch (_: IllegalArgumentException) { null }
    }
}
