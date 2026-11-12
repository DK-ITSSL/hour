package com.hour.domain.sense

import com.hour.domain.geo.ProvinceLocator
import com.hour.domain.geo.RegionParser
import com.hour.domain.geo.SunCalculator
import com.hour.domain.geo.SunTimes
import com.hour.domain.geo.haversineMeters
import com.hour.domain.journey.CompanionSummary
import com.hour.domain.journey.GeoPoint
import com.hour.domain.journey.LightSummary
import com.hour.domain.journey.RainSummary
import com.hour.domain.journey.SafetySummary
import com.hour.domain.journey.StopRecord
import com.hour.domain.journey.WarmthSummary
import com.hour.domain.journey.WindSummary
import com.hour.domain.signal.DriveGear
import com.hour.domain.signal.PassPointKind
import com.hour.domain.signal.SeatPosition
import com.hour.domain.signal.WindowPosition
import com.hour.domain.signal.WiperLevel
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable

/** 켜짐/꺼짐 구간의 누적 시간과 거리. 아직 열려 있는 구간은 조회 시점까지 포함한다. */
@Serializable
class IntervalTracker(
    var openAt: Long? = null,
    var openKm: Double = 0.0,
    var totalMs: Long = 0,
    var totalKm: Double = 0.0,
    var firstAt: Long? = null,
) {
    val isOn: Boolean get() = openAt != null

    fun set(on: Boolean, at: Long, km: Double) {
        val start = openAt
        if (on && start == null) {
            openAt = at
            openKm = km
            if (firstAt == null) firstAt = at
        } else if (!on && start != null) {
            totalMs += max(0, at - start)
            totalKm += max(0.0, km - openKm)
            openAt = null
        }
    }

    fun ms(now: Long): Long = totalMs + (openAt?.let { max(0, now - it) } ?: 0)
    fun minutes(now: Long): Int = (ms(now) / 60_000).toInt()
    fun km(nowKm: Double): Double = totalKm + (openAt?.let { max(0.0, nowKm - openKm) } ?: 0.0)
}

/** 차량의 마지막으로 알려진 상태. 여정이 없을 때도 유지해서, 여정 시작 시점의 상태(이미 비가 오는 중 등)를 반영한다. */
@Serializable
class VehicleState(
    var gear: DriveGear = DriveGear.UNKNOWN,
    var speedKmh: Float = 0f,
    var wiperOn: Boolean = false,
    var wiperLevel: WiperLevel = WiperLevel.OFF,
    var highBeam: Boolean = false,
    var lowBeam: Boolean = false,
    var windows: MutableMap<WindowPosition, Float> = mutableMapOf(),
    var occupancy: MutableMap<SeatPosition, Boolean> = mutableMapOf(),
    var belts: MutableMap<SeatPosition, Boolean> = mutableMapOf(),
    var seatHeat: MutableMap<SeatPosition, Int> = mutableMapOf(),
    var defrost: Boolean = false,
    var outsideC: Float? = null,
    var lat: Double? = null,
    var lon: Double? = null,
    var altitude: Double? = null,
    var address: String? = null,
    var speedLimit: Int = 0,
) {
    val point: GeoPoint? get() = if (lat != null && lon != null) GeoPoint(lat!!, lon!!) else null
    val driving: Boolean get() = gear.isMoving
    fun seated(seat: SeatPosition): Boolean = occupancy[seat] == true || belts[seat] == true
}

// ---------------------------------------------------------------- 감각

@Serializable
class RainAccumulator(val wiper: IntervalTracker = IntervalTracker(), var peak: Int = 0, var startedAt: Long = 0) {
    fun sync(s: VehicleState, at: Long, km: Double) {
        wiper.set(s.wiperOn, at, km)
        if (s.wiperOn) peak = max(peak, s.wiperLevel.intensity)
    }
    fun summary(now: Long, km: Double) = RainSummary(
        minutes = wiper.minutes(now),
        km = wiper.km(km),
        peakLevel = peak,
        firstMinute = wiper.firstAt?.let { ((it - startedAt) / 60_000).toInt().coerceAtLeast(0) },
    )
}

@Serializable
class LightAccumulator(
    val highBeam: IntervalTracker = IntervalTracker(),
    val night: IntervalTracker = IntervalTracker(),
    var sunsetDrive: Boolean = false,
    var sunDay: Long = -1,
    var sunrise: Long = 0,
    var sunset: Long = 0,
) {
    fun sync(s: VehicleState, at: Long, km: Double) {
        highBeam.set(s.highBeam && s.driving, at, km)
        val sun = sunFor(at, s)
        val isNight = if (sun != null) at < sun.sunrise || at > sun.sunset else s.lowBeam
        night.set(isNight && s.driving, at, km)
        if (sun != null && s.driving && abs(at - sun.sunset) <= SUNSET_WINDOW_MS) sunsetDrive = true
    }

    private fun sunFor(at: Long, s: VehicleState): SunTimes? {
        val lat = s.lat ?: return null
        val lon = s.lon ?: return null
        val day = Instant.ofEpochMilli(at).atZone(SunCalculator.KST).toLocalDate().toEpochDay()
        if (day != sunDay) {
            val t = SunCalculator.sunTimes(at, lat, lon) ?: return null
            sunDay = day; sunrise = t.sunrise; sunset = t.sunset
        }
        return SunTimes(sunrise, sunset)
    }

    fun summary(now: Long, km: Double) = LightSummary(
        highBeamKm = highBeam.km(km),
        highBeamMinutes = highBeam.minutes(now),
        nightKm = night.km(km),
        nightMinutes = night.minutes(now),
        sunsetDrive = sunsetDrive,
    )

    private companion object { const val SUNSET_WINDOW_MS = 20 * 60_000L }
}

@Serializable
class WindAccumulator(
    val side: IntervalTracker = IntervalTracker(),
    val roof: IntervalTracker = IntervalTracker(),
    val opened: MutableSet<WindowPosition> = mutableSetOf(),
) {
    fun sync(s: VehicleState, at: Long, km: Double) {
        val open = s.windows.filter { (_, ratio) -> ratio >= OPEN_RATIO }.keys
        if (s.driving) opened += open
        side.set(s.driving && open.any { it != WindowPosition.SUNROOF }, at, km)
        roof.set(s.driving && WindowPosition.SUNROOF in open, at, km)
    }
    fun summary(now: Long, km: Double) = WindSummary(
        km = side.km(km),
        minutes = side.minutes(now),
        windows = opened.sorted(),
        sunroofKm = roof.km(km),
    )
    private companion object { const val OPEN_RATIO = 0.2f }
}

@Serializable
class WarmthAccumulator(
    val heat: IntervalTracker = IntervalTracker(),
    val vent: IntervalTracker = IntervalTracker(),
    val defrost: IntervalTracker = IntervalTracker(),
    var minC: Float? = null,
    var maxC: Float? = null,
) {
    fun sync(s: VehicleState, at: Long, km: Double) {
        heat.set(s.seatHeat.values.any { it > 0 }, at, km)
        vent.set(s.seatHeat.values.any { it < 0 }, at, km)
        defrost.set(s.defrost, at, km)
        s.outsideC?.let { c ->
            minC = minC?.let { min(it, c) } ?: c
            maxC = maxC?.let { max(it, c) } ?: c
        }
    }
    fun summary(now: Long) = WarmthSummary(heat.minutes(now), vent.minutes(now), defrost.minutes(now), minC, maxC)
}

@Serializable
class CompanionAccumulator(
    val seen: MutableSet<SeatPosition> = mutableSetOf(),
    var rearKnown: Boolean = false,
    var beltKnown: Boolean = false,
    var unbelted: Boolean = false,
) {
    fun sync(s: VehicleState) {
        rearKnown = rearKnown || (s.occupancy.keys + s.belts.keys).any { it.isRear }
        beltKnown = beltKnown || s.belts.isNotEmpty()
        if (!s.driving) return
        SeatPosition.entries.filter { it != SeatPosition.DRIVER && s.seated(it) }.forEach { seen += it }
        // 착석한 좌석 중 벨트를 풀고 달린 순간이 있으면 '모두 안전벨트'가 아니다.
        if (s.belts.isNotEmpty() && SeatPosition.entries.any { (it == SeatPosition.DRIVER || s.occupancy[it] == true) && s.belts[it] == false }) unbelted = true
    }
    fun summary(): CompanionSummary {
        val seats = listOf(SeatPosition.DRIVER) + seen.sorted()
        val passengers = seen
        return CompanionSummary(
            seats = seats,
            frontOnly = passengers == setOf(SeatPosition.FRONT_PASSENGER),
            rearKnown = rearKnown,
            allBelted = if (beltKnown) !unbelted else null,
        )
    }
}

// ---------------------------------------------------------------- 장소

@Serializable
class PlaceAccumulator(
    val provinces: MutableSet<String> = mutableSetOf(),
    val districts: MutableSet<String> = mutableSetOf(),
    var maxKmFromHome: Double? = null,
    var maxAltitude: Double? = null,
    val passCounts: MutableMap<PassPointKind, Int> = mutableMapOf(),
    val passArmed: MutableMap<PassPointKind, Boolean> = mutableMapOf(),
    val stops: MutableList<StopRecord> = mutableListOf(),
    var lastLocateLat: Double? = null,
    var lastLocateLon: Double? = null,
    val path: MutableList<GeoPoint> = mutableListOf(),
) {
    fun onAddress(address: String) {
        val r = RegionParser.parse(address)
        r.province?.let { provinces += it }
        r.district?.let { districts += it }
    }

    fun onLocation(lat: Double, lon: Double, altitude: Double, home: GeoPoint?) {
        home?.let {
            val d = haversineMeters(it.lat, it.lon, lat, lon) / 1000.0
            maxKmFromHome = max(maxKmFromHome ?: 0.0, d)
        }
        if (altitude != 0.0) maxAltitude = max(maxAltitude ?: altitude, altitude)
        val pLat = lastLocateLat
        val pLon = lastLocateLon
        // 시·도 추정과 경로 저장은 300m마다만 한다(연산·저장량 절약).
        if (pLat == null || pLon == null || haversineMeters(pLat, pLon, lat, lon) >= PATH_STEP_M) {
            ProvinceLocator.locate(lat, lon)?.let { provinces += it }
            lastLocateLat = lat; lastLocateLon = lon
            if (path.size < MAX_PATH) path += GeoPoint(lat, lon)
        }
    }

    fun onPassPoint(kind: PassPointKind, remainMeters: Double) {
        val armed = passArmed[kind] ?: true
        if (remainMeters > REARM_M) passArmed[kind] = true
        else if (remainMeters <= PASS_M && armed) {
            passCounts[kind] = (passCounts[kind] ?: 0) + 1
            passArmed[kind] = false
        }
    }

    fun count(kind: PassPointKind) = passCounts[kind] ?: 0

    private companion object {
        const val PATH_STEP_M = 300.0
        const val MAX_PATH = 400
        const val PASS_M = 30.0
        const val REARM_M = 200.0
    }
}

// ---------------------------------------------------------------- 안심(위험 행동이 없었는지)

@Serializable
class SafetyAccumulator(
    var samples: Int = 0,
    var samplesWithLimit: Int = 0,
    var overStart: Long? = null,
    var overCounted: Boolean = false,
    var overspeed: Int = 0,
    var prevKmh: Float? = null,
    var prevAt: Long = 0,
    var lastHarshAt: Long = 0,
    var harsh: Int = 0,
    var driveStartAt: Long? = null,
    var continuousMs: Long = 0,
    var longestMs: Long = 0,
    var restBreaks: Int = 0,
    var schoolArmed: Boolean = true,
    var schoolPasses: Int = 0,
    var schoolCompliant: Int = 0,
) {
    fun onSpeed(kmh: Float, at: Long, limit: Int, driving: Boolean) {
        if (!driving) { prevKmh = kmh; prevAt = at; return }
        samples++
        if (limit > 0) {
            samplesWithLimit++
            if (kmh > limit * OVER_FACTOR) {
                val start = overStart ?: at.also { overStart = it }
                if (!overCounted && at - start >= OVER_SUSTAIN_MS) { overspeed++; overCounted = true }
            } else { overStart = null; overCounted = false }
        }
        val p = prevKmh
        val dt = at - prevAt
        if (p != null && dt in 1..HARSH_MAX_GAP_MS) {
            val rate = abs(kmh - p) / (dt / 1000.0)
            if (rate >= HARSH_KMH_PER_S && at - lastHarshAt >= HARSH_DEBOUNCE_MS) { harsh++; lastHarshAt = at }
        }
        prevKmh = kmh; prevAt = at
    }

    fun onSchoolZone(remainMeters: Int, speedKmh: Float, limit: Int) {
        if (remainMeters > 200) schoolArmed = true
        else if (remainMeters <= 30 && schoolArmed) {
            schoolArmed = false
            schoolPasses++
            if (speedKmh <= (if (limit > 0) limit else SCHOOL_DEFAULT_LIMIT) + 0.5f) schoolCompliant++
        }
    }

    fun onDriveStart(at: Long) { driveStartAt = at }

    fun onPark(at: Long) {
        driveStartAt?.let { continuousMs += max(0, at - it) }
        longestMs = max(longestMs, continuousMs)
        driveStartAt = null
    }

    fun onResume(at: Long, stoppedMs: Long) {
        if (stoppedMs >= REST_MIN_MS) {
            if (continuousMs in REST_COUNT_FROM_MS..REST_COUNT_TO_MS) restBreaks++
            continuousMs = 0
        }
        driveStartAt = at
    }

    fun summary(now: Long): SafetySummary {
        val running = continuousMs + (driveStartAt?.let { max(0, now - it) } ?: 0)
        return SafetySummary(
            speedSamples = samples,
            limitCoverage = if (samples == 0) 0.0 else samplesWithLimit.toDouble() / samples,
            overspeedEpisodes = overspeed,
            harshEvents = harsh,
            restBreaksBefore2h = restBreaks,
            longestContinuousMinutes = (max(longestMs, running) / 60_000).toInt(),
            schoolZonePasses = schoolPasses,
            schoolZoneCompliant = schoolCompliant,
        )
    }

    private companion object {
        const val OVER_FACTOR = 1.1f
        const val OVER_SUSTAIN_MS = 10_000L
        const val HARSH_KMH_PER_S = 10.8 // 약 0.3g
        const val HARSH_MAX_GAP_MS = 3_000L
        const val HARSH_DEBOUNCE_MS = 5_000L
        const val REST_MIN_MS = 15 * 60_000L
        const val REST_COUNT_FROM_MS = 60 * 60_000L
        const val REST_COUNT_TO_MS = 120 * 60_000L
        const val SCHOOL_DEFAULT_LIMIT = 30
    }
}
