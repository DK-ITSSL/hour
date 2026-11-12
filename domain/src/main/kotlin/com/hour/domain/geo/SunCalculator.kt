package com.hour.domain.geo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class SunTimes(val sunrise: Long, val sunset: Long)

/** 일출·일몰 계산(NOAA 근사식, 오차 수 분). 노을 드라이브·밤길 판정에만 쓰므로 정밀도는 충분하다. */
object SunCalculator {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    fun sunTimes(epochMillis: Long, lat: Double, lon: Double, zone: ZoneId = KST): SunTimes? {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val rise = event(date, lat, lon, rising = true, zone = zone) ?: return null
        val set = event(date, lat, lon, rising = false, zone = zone) ?: return null
        return SunTimes(rise, set)
    }

    private fun event(date: LocalDate, lat: Double, lon: Double, rising: Boolean, zone: ZoneId): Long? {
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634
        l = norm(l, 360.0)
        var ra = norm(Math.toDegrees(atan(0.91764 * tanD(l))), 360.0)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(90.833) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15.0
        val localMean = h + ra - 0.06571 * t - 6.622
        val ut = norm(localMean - lngHour, 24.0)
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        var candidate = utcMidnight + (ut * 3_600_000).toLong()
        // 결과가 요청한 현지 날짜에 오도록 하루 단위로 보정한다(예: 한국 일출은 UTC 기준 전날).
        val day = 86_400_000L
        while (Instant.ofEpochMilli(candidate).atZone(zone).toLocalDate().isAfter(date)) candidate -= day
        while (Instant.ofEpochMilli(candidate).atZone(zone).toLocalDate().isBefore(date)) candidate += day
        return candidate
    }

    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun norm(v: Double, m: Double): Double = ((v % m) + m) % m
}
