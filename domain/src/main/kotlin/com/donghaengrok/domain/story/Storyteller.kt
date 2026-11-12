package com.donghaengrok.domain.story

import com.donghaengrok.domain.geo.SunCalculator
import com.donghaengrok.domain.journey.JourneySummary
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** 여정 제목과 내러티브를 템플릿으로 만든다(LLM 없이 기기 안에서, 결정적으로). */
object Storyteller {

    fun title(s: JourneySummary): String {
        val parts = mutableListOf<String>()
        weatherPhrase(s)?.let(parts::add)
        timePhrase(s)?.let(parts::add)
        val head = parts.joinToString(" ")
        val who = companionPhrase(s)
        val where = destinationPhrase(s)
        return listOf(head, listOfNotNull(who, where).joinToString(" ")).filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "오늘의 드라이브" }
    }

    fun narrative(s: JourneySummary): String {
        val start = at(s.startedAt)
        val lines = mutableListOf<String>()
        val who = when (s.companions.count) {
            1 -> "혼자"
            2 -> "둘이서"
            else -> "${s.companions.count}명이 함께"
        }
        lines += "${dateText(start)} ${clock(start)}, $who 출발했어요."
        if (s.rain.present) {
            val first = s.rain.firstMinute?.takeIf { it > 0 }?.let { "출발하고 ${it}분쯤 " } ?: ""
            val strength = if (s.rain.peakLevel >= 3) "세차게 " else ""
            lines += "${first}비가 ${strength}내려 ${s.rain.minutes.coerceAtLeast(1)}분 동안 와이퍼가 바빴어요."
        }
        if (s.wind.present) lines += "창문을 열고 ${km(s.wind.km)} 동안 바깥바람을 맞았어요."
        if (s.light.sunsetDrive) lines += "해 질 녘 노을 속을 달렸어요."
        if (s.light.starlight) lines += "상향등을 켜고 ${km(s.light.highBeamKm)}의 어두운 별빛 길을 지났어요."
        if (s.places.tunnels > 0) lines += "터널을 ${s.places.tunnels}번 지났어요."
        if (s.places.serviceAreas + s.places.restAreas > 0) lines += "휴게소에 ${s.places.serviceAreas + s.places.restAreas}번 들렀어요."
        s.moments.firstOrNull()?.let { lines += "그리고 이 순간을 기억했어요. \"${it.text}\"" }
        val dest = s.destinationName ?: s.end.district
        val end = at(s.endedAt)
        lines += "${s.drivingMinutes.coerceAtLeast(1)}분 동안 ${km(s.distanceKm)}를 달려 ${clock(end)}에" +
            (dest?.let { " ${it}에" } ?: "") + " 도착했어요."
        return lines.joinToString(" ")
    }

    private fun weatherPhrase(s: JourneySummary): String? = when {
        s.rain.present && s.rain.peakLevel >= 3 -> "소나기 속"
        s.rain.present -> "비 내리던"
        s.light.starlight -> "별빛 아래"
        s.wind.present -> "바람 좋은"
        else -> null
    }

    private fun timePhrase(s: JourneySummary): String? {
        if (s.light.sunsetDrive) return "노을 무렵"
        if (s.drivingMinutes > 0 && s.light.nightMinutes * 2 >= s.drivingMinutes) return "밤길"
        val h = at(s.startedAt).hour
        return when (h) {
            in 5..9 -> "아침"
            in 10..15 -> null
            in 16..18 -> "저녁 무렵"
            else -> null
        }
    }

    private fun companionPhrase(s: JourneySummary): String? = when (s.companions.count) {
        2 -> "둘이서"
        3 -> "셋이서"
        4 -> "넷이서"
        in 5..9 -> "다 함께"
        else -> null
    }

    private fun destinationPhrase(s: JourneySummary): String? {
        val dest = s.destinationName?.takeIf { it.isNotBlank() } ?: s.end.district ?: s.end.province
        return dest?.let { "$it 가는 길" }
    }

    private fun at(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(SunCalculator.KST)

    private fun clock(t: ZonedDateTime): String {
        val ampm = if (t.hour < 12) "오전" else "오후"
        val h = (t.hour % 12).let { if (it == 0) 12 else it }
        return if (t.minute == 0) "$ampm ${h}시" else "$ampm ${h}시 ${t.minute}분"
    }

    private fun dateText(t: ZonedDateTime): String {
        val dow = listOf("월", "화", "수", "목", "금", "토", "일")[t.dayOfWeek.value - 1]
        return "${t.monthValue}월 ${t.dayOfMonth}일 ${dow}요일"
    }

    private fun km(v: Double): String = if (v < 10) "${(v * 10).roundToInt() / 10.0}km" else "${v.roundToInt()}km"
}
