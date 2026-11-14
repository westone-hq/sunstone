package com.westone.sunstone.connect

import android.content.Context
import com.westone.sunstone.routines.port.MemoryPort
import java.time.LocalDate

/**
 * 작성: 2026-11-11 15:14:38 · 박희정
 * 수정: 2026-11-12 16:10:00 · 박희정
 * 클래스: PrefsMemory
 * 목적: 기억 창구(`MemoryPort`)를 앱 설정 저장소에 둔다. 앱을 다시 켜도 마지막
 *       세차일과 마지막 차일드락 날이 남는다.
 * 인자: context — 앱 Context
 */
class PrefsMemory(context: Context) : MemoryPort {

    private companion object {
        /**
         * 작성: 2026-11-11 15:14:38 · 박희정
         * 변수: KEY_LAST_WASH
         * 목적: 마지막 세차일(ISO 날짜 글자, 예 "2026-10-06")을 둘 설정 키.
         */
        const val KEY_LAST_WASH = "last_wash"

        /**
         * 작성: 2026-11-12 16:10:00 · 박희정
         * 변수: KEY_LAST_CHILD_LOCK
         * 목적: 마지막으로 차일드락을 쓴 날(ISO 날짜 글자)을 둘 설정 키.
         */
        const val KEY_LAST_CHILD_LOCK = "last_child_lock"
    }

    /** 기억 저장소. 날짜만 둔다 */
    private val prefs = context.getSharedPreferences("memory", Context.MODE_PRIVATE)

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 수정: 2026-11-12 16:10:00 · 박희정
     * 함수: lastWashDate
     * 목적: 저장한 마지막 세차일을 읽는다. 없거나 읽을 수 없으면 null.
     */
    override fun lastWashDate(): LocalDate? = date(KEY_LAST_WASH)

    /**
     * 작성: 2026-11-11 15:14:38 · 박희정
     * 함수: recordWash
     * 목적: 세차한 날을 저장한다.
     */
    override fun recordWash(date: LocalDate) {
        prefs.edit().putString(KEY_LAST_WASH, date.toString()).apply()
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: lastChildLockDate
     * 목적: 저장한 마지막 차일드락 날을 읽는다. 없거나 읽을 수 없으면 null.
     */
    override fun lastChildLockDate(): LocalDate? = date(KEY_LAST_CHILD_LOCK)

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: recordChildLock
     * 목적: 차일드락을 쓴 날을 저장한다.
     */
    override fun recordChildLock(date: LocalDate) {
        prefs.edit().putString(KEY_LAST_CHILD_LOCK, date.toString()).apply()
    }

    /**
     * 작성: 2026-11-12 16:10:00 · 박희정
     * 함수: date
     * 목적: 설정 키에 둔 날짜 글자를 읽는다.
     * 인자: key — 설정 키
     * 반환: 날짜. 없거나 읽을 수 없으면 null
     */
    private fun date(key: String): LocalDate? =
        prefs.getString(key, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
