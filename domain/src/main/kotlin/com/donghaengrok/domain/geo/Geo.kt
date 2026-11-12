package com.donghaengrok.domain.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

private const val EARTH_RADIUS_M = 6_371_000.0

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
}

/**
 * 여정 거리. 위치 궤적, 주행거리계 증가분, 속도 적분 세 가지 중 가장 큰 값을 쓴다.
 * 신호마다 빠지는 경우가 있어서(에뮬레이터에서는 위치가 고정되거나 주행거리계가 멈춰 있음) 과소 집계를 막는다.
 */
@Serializable
class DistanceTracker(
    var trackKm: Double = 0.0,
    var lastLat: Double? = null,
    var lastLon: Double? = null,
    var lastFixAt: Long = 0,
    var odoStart: Float? = null,
    var odoLast: Float? = null,
    var speedKm: Double = 0.0,
    var lastSpeedKmh: Float? = null,
    var lastSpeedAt: Long = 0,
) {
    fun onLocation(lat: Double, lon: Double, at: Long) {
        val pLat = lastLat
        val pLon = lastLon
        if (pLat == null || pLon == null) {
            lastLat = lat; lastLon = lon; lastFixAt = at
            return
        }
        val d = haversineMeters(pLat, pLon, lat, lon)
        val hours = (at - lastFixAt) / 3_600_000.0
        if (hours > 0 && d / 1000.0 / hours > MAX_KMH) return // 순간 이동(잘못된 위치)은 버린다
        if (d < MIN_STEP_M) return // 작은 흔들림은 쌓아 두었다가 5m를 넘을 때 반영
        trackKm += d / 1000.0
        lastLat = lat; lastLon = lon; lastFixAt = at
    }

    fun onOdometer(km: Float) {
        if (odoStart == null) odoStart = km
        odoLast = km
    }

    fun onSpeed(kmh: Float, at: Long) {
        val prev = lastSpeedKmh
        if (prev != null && at > lastSpeedAt) {
            val dtMs = min(at - lastSpeedAt, MAX_SPEED_GAP_MS)
            speedKm += (prev + kmh) / 2.0 * dtMs / 3_600_000.0
        }
        lastSpeedKmh = kmh
        lastSpeedAt = at
    }

    val odoKm: Double get() = max(0.0, ((odoLast ?: 0f) - (odoStart ?: odoLast ?: 0f)).toDouble())

    fun km(): Double = max(trackKm, max(odoKm, speedKm))

    private companion object {
        const val MAX_KMH = 250.0
        const val MIN_STEP_M = 5.0
        const val MAX_SPEED_GAP_MS = 5_000L
    }
}
