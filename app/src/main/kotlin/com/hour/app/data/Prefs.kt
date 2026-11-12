package com.hour.app.data

import android.content.Context
import com.hour.domain.unlock.AssetSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 가벼운 사용자 설정: 장착한 애셋(슬롯당 1개), 낭독 켜기·끄기. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hour_prefs", Context.MODE_PRIVATE)

    private val _equipped = MutableStateFlow(
        AssetSlot.entries.mapNotNull { slot -> sp.getString(key(slot), null)?.let { slot to it } }.toMap(),
    )
    val equipped: StateFlow<Map<AssetSlot, String>> = _equipped.asStateFlow()

    private val _narration = MutableStateFlow(sp.getBoolean(KEY_NARRATION, true))
    val narration: StateFlow<Boolean> = _narration.asStateFlow()

    fun equip(slot: AssetSlot, ruleId: String?) {
        sp.edit().apply { if (ruleId == null) remove(key(slot)) else putString(key(slot), ruleId) }.apply()
        _equipped.value = _equipped.value.toMutableMap().apply { if (ruleId == null) remove(slot) else put(slot, ruleId) }
    }

    fun setNarration(on: Boolean) {
        sp.edit().putBoolean(KEY_NARRATION, on).apply()
        _narration.value = on
    }

    fun clearEquipped() {
        sp.edit().apply { AssetSlot.entries.forEach { remove(key(it)) } }.apply()
        _equipped.value = emptyMap()
    }

    private fun key(slot: AssetSlot) = "equip_${slot.name}"

    private companion object {
        const val KEY_NARRATION = "narration"
    }
}
