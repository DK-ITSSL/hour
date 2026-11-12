package com.hour.app.text

import com.hour.domain.geo.SunCalculator
import com.hour.domain.journey.TripCategory
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** 한국어 조사·날짜·거리 표기 도우미. 화면과 음성 문장에서 함께 쓴다. */
object Korean {
    /** 마지막 글자에 받침이 있는지. 한글이 아니면 받침 없음으로 본다. */
    fun hasBatchim(word: String): Boolean {
        val c = word.trimEnd().lastOrNull() ?: return false
        if (c !in '가'..'힣') return false
        return (c - '가') % 28 != 0
    }

    /** 받침이 ㄹ인지(로/으로 구분). */
    private fun endsWithRieul(word: String): Boolean {
        val c = word.trimEnd().lastOrNull() ?: return false
        return c in '가'..'힣' && (c - '가') % 28 == 8
    }

    fun eulReul(word: String) = word + if (hasBatchim(word)) "을" else "를"
    fun iGa(word: String) = word + if (hasBatchim(word)) "이" else "가"
    fun euroRo(word: String) = word + if (hasBatchim(word) && !endsWithRieul(word)) "으로" else "로"
    fun gwaWa(word: String) = word + if (hasBatchim(word)) "과" else "와"

    fun km(v: Double): String = when {
        v <= 0.0 -> "0km"
        v < 10 -> "${(v * 10).roundToInt() / 10.0}km"
        else -> "${v.roundToInt()}km"
    }

    fun minutes(min: Int): String {
        val m = min.coerceAtLeast(0)
        return if (m >= 60) "${m / 60}시간${if (m % 60 > 0) " ${m % 60}분" else ""}" else "${m}분"
    }

    fun at(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(SunCalculator.KST)

    private val days = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 11월 12일 (수) */
    fun date(ms: Long): String {
        val t = at(ms)
        return "${t.monthValue}월 ${t.dayOfMonth}일 (${days[t.dayOfWeek.value - 1]})"
    }

    /** 오후 3:20 */
    fun clock(ms: Long): String {
        val t = at(ms)
        val ampm = if (t.hour < 12) "오전" else "오후"
        val h = (t.hour % 12).let { if (it == 0) 12 else it }
        return "$ampm $h:${t.minute.toString().padStart(2, '0')}"
    }

    /** 운행타입 이름(화면·음성 공통). */
    fun category(c: TripCategory): String = c.korean
}
