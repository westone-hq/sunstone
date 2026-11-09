package com.westone.sunstone.connect

import android.content.Context
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.SeatProfile
import com.westone.sunstone.routines.port.SeatProfiles
import org.json.JSONObject

/**
 * 작성: 2026-11-08 18:03:56 · 박건준
 * 수정: 2026-11-09 18:49:00 · 박건준
 * 클래스: PrefsSeatProfiles
 * 목적: 사람별 시트 자세를 앱 설정 저장소(SharedPreferences)에 JSON으로
 *       저장한다. 사람 이름이 키다.
 * 인자: context — 앱 Context
 */
class PrefsSeatProfiles(context: Context) : SeatProfiles {

    /** 자세 저장소. 키는 사람 이름, 값은 `encode()`가 만든 JSON 글자 */
    private val prefs = context.getSharedPreferences("seat_profiles", Context.MODE_PRIVATE)

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: get
     * 목적: 이름으로 저장한 자세를 꺼낸다.
     * 인자: name — 사람 이름
     * 반환: 자세. 없거나 저장 값이 깨졌으면 null
     */
    override fun get(name: String): SeatProfile? = prefs.getString(name, null)?.let { decode(name, it) }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: save
     * 목적: 자세를 저장한다. 같은 이름이 있으면 덮어쓴다.
     * 인자: profile — 저장할 자세
     */
    override fun save(profile: SeatProfile) {
        prefs.edit().putString(profile.name, encode(profile)).apply()
    }

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: names
     * 목적: 저장한 사람 이름을 가나다순으로 돌려준다.
     * 반환: 이름 목록
     */
    override fun names(): List<String> = prefs.all.keys.sorted()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: encode
     * 목적: 자세를 `{"positions":{축:위치}, "memorySlot":번호}` JSON으로 쓴다.
     * 인자: p — 저장할 자세
     * 반환: JSON 글자. 메모리 번호가 없으면 그 칸을 뺀다
     */
    private fun encode(p: SeatProfile): String = JSONObject().apply {
        put("positions", JSONObject().apply { p.positions.forEach { (axis, v) -> put(axis.name, v) } })
        p.memorySlot?.let { put("memorySlot", it) }
    }.toString()

    /**
     * 작성: 2026-11-08 18:03:56 · 박건준
     * 수정: 2026-11-09 18:49:00 · 박건준
     * 함수: decode
     * 목적: `encode()`가 쓴 JSON을 자세로 읽는다. 모르는 축은 버린다.
     * 인자: name — 사람 이름
     *       raw — 저장한 JSON 글자
     * 반환: 자세. JSON이 깨졌으면 null
     */
    private fun decode(name: String, raw: String): SeatProfile? = runCatching {
        val json = JSONObject(raw) // 저장한 자세
        val positions = json.optJSONObject("positions") // 축별 위치, 없으면 null
        val map = SeatAxis.entries.mapNotNull { axis -> // 저장된 축만 모은 위치
            if (positions != null && positions.has(axis.name)) axis to positions.getInt(axis.name) else null
        }.toMap()
        val slot = if (json.has("memorySlot")) json.getInt("memorySlot") else null // 시트 메모리 번호, 없으면 null
        SeatProfile(name, map, slot)
    }.getOrNull()
}
