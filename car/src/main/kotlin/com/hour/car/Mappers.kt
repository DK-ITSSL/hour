package com.hour.car

import com.hour.domain.signal.WiperLevel

/** WiperSwitch 이름(SDK enum.name)을 강도로 변환한다. SDK 타입에 의존하지 않도록 이름으로 받는다. */
fun wiperLevelOf(switchName: String?): WiperLevel = when {
    switchName == null -> WiperLevel.UNKNOWN
    switchName == "OFF" -> WiperLevel.OFF
    switchName == "MIST" || switchName.startsWith("INTERMITTENT") -> WiperLevel.LIGHT
    switchName == "CONTINUOUS_LEVEL_1" || switchName == "CONTINUOUS_LEVEL_2" -> WiperLevel.MODERATE
    switchName.startsWith("CONTINUOUS_LEVEL") -> WiperLevel.HEAVY
    else -> WiperLevel.UNKNOWN // AUTO, OTHER
}

/** 창문 위치 원시값을 0(닫힘)~1(완전 열림) 비율로 바꾼다. 방향은 D1 검증 결과에 따라 openAtMax로 정한다. */
fun openRatio(raw: Int, min: Int, max: Int, openAtMax: Boolean = true): Float {
    if (max <= min) return 0f
    val r = ((raw - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    return if (openAtMax) r else 1f - r
}
