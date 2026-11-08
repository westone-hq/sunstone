package com.westone.sunstone.connect

import android.content.Context
import com.westone.sunstone.routines.model.SeatAxis
import com.westone.sunstone.routines.port.SeatProfile
import com.westone.sunstone.routines.port.SeatProfiles
import org.json.JSONObject

/** 시트 자세를 SharedPreferences에 JSON으로 저장한다. 사람 이름이 키. */
class PrefsSeatProfiles(context: Context) : SeatProfiles {
    private val prefs = context.getSharedPreferences("seat_profiles", Context.MODE_PRIVATE)

    override fun get(name: String): SeatProfile? = prefs.getString(name, null)?.let { decode(name, it) }

    override fun save(profile: SeatProfile) {
        prefs.edit().putString(profile.name, encode(profile)).apply()
    }

    override fun names(): List<String> = prefs.all.keys.sorted()

    private fun encode(p: SeatProfile): String = JSONObject().apply {
        put("positions", JSONObject().apply { p.positions.forEach { (axis, v) -> put(axis.name, v) } })
        p.memorySlot?.let { put("memorySlot", it) }
    }.toString()

    private fun decode(name: String, raw: String): SeatProfile? = runCatching {
        val json = JSONObject(raw)
        val positions = json.optJSONObject("positions")
        val map = SeatAxis.entries.mapNotNull { axis ->
            if (positions != null && positions.has(axis.name)) axis to positions.getInt(axis.name) else null
        }.toMap()
        val slot = if (json.has("memorySlot")) json.getInt("memorySlot") else null
        SeatProfile(name, map, slot)
    }.getOrNull()
}
