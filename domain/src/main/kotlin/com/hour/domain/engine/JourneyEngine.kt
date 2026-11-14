package com.hour.domain.engine

import com.hour.domain.geo.DistanceTracker
import com.hour.domain.geo.ProvinceLocator
import com.hour.domain.geo.RegionParser
import com.hour.domain.geo.haversineMeters
import com.hour.domain.journey.Anchor
import com.hour.domain.journey.GeoPoint
import com.hour.domain.journey.JourneyStateMachine
import com.hour.domain.journey.JourneyStateMachine.Transition
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.MomentRecord
import com.hour.domain.journey.PlaceInfo
import com.hour.domain.journey.PlacesSummary
import com.hour.domain.journey.StopRecord
import com.hour.domain.sense.CompanionAccumulator
import com.hour.domain.sense.LightAccumulator
import com.hour.domain.sense.PlaceAccumulator
import com.hour.domain.sense.RainAccumulator
import com.hour.domain.sense.SafetyAccumulator
import com.hour.domain.sense.VehicleState
import com.hour.domain.sense.WarmthAccumulator
import com.hour.domain.sense.WindAccumulator
import com.hour.domain.signal.AnchorType
import com.hour.domain.signal.PassPointKind
import com.hour.domain.signal.SignalEvent
import com.hour.domain.story.Storyteller
import com.hour.domain.story.TripCategoryClassifier
import java.util.UUID
import kotlin.math.max
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 진행 중인 여정의 누적 상태. 체크포인트로 저장해 프로세스가 죽어도 이어 간다. */
@Serializable
class JourneyDraft(
    val id: String,
    val startedAt: Long,
    var confirmed: Boolean = false,
    val distance: DistanceTracker = DistanceTracker(),
    val rain: RainAccumulator = RainAccumulator(startedAt = startedAt),
    val light: LightAccumulator = LightAccumulator(),
    val wind: WindAccumulator = WindAccumulator(),
    val warmth: WarmthAccumulator = WarmthAccumulator(),
    val companions: CompanionAccumulator = CompanionAccumulator(),
    val places: PlaceAccumulator = PlaceAccumulator(),
    val safety: SafetyAccumulator = SafetyAccumulator(),
    val moments: MutableList<MomentRecord> = mutableListOf(),
    var startPlace: PlaceInfo = PlaceInfo(),
    var endPlace: PlaceInfo = PlaceInfo(),
    var destinationName: String? = null,
    var arrived: Boolean = false,
    var drivingMs: Long = 0,
    var driveSegStart: Long? = null,
    var parkedAt: Long? = null,
)

@Serializable
class EngineCheckpoint(
    val vehicle: VehicleState,
    val machine: JourneyStateMachine,
    val draft: JourneyDraft?,
    val anchors: List<Anchor>,
    val lastOdometer: Float?,
    val savedAt: Long,
)

sealed interface EngineOutput {
    data class Started(val id: String, val at: Long) : EngineOutput
    data class Discarded(val id: String) : EngineOutput
    /** 주차(P) 등 상태가 바뀌어 중간 결과를 저장할 시점. */
    data class Progress(val summary: JourneySummary) : EngineOutput
    data class GiftReady(val summary: JourneySummary) : EngineOutput
    data class Finished(val summary: JourneySummary) : EngineOutput
    /** 진행 중인 여정이 없을 때 들어온 순간 기록. 저장소가 가장 최근 여정에 붙인다. */
    data class OrphanMoment(val moment: MomentRecord) : EngineOutput
}

/**
 * 신호 → 여정. 단일 스레드에서만 호출한다(기록 서비스의 이벤트 루프).
 */
class JourneyEngine(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private var vehicle = VehicleState()
    private var machine = JourneyStateMachine()
    private var draft: JourneyDraft? = null
    private val anchors = mutableMapOf<AnchorType, Anchor>()
    private var lastOdometer: Float? = null
    private var lastAt: Long = 0

    val activeJourneyId: String? get() = draft?.id
    val state: JourneyStateMachine.State get() = machine.state

    fun setAnchor(anchor: Anchor) { anchors[anchor.type] = anchor }
    fun clearAnchor(type: AnchorType) { anchors.remove(type) }
    fun anchors(): List<Anchor> = anchors.values.toList()

    fun onEvent(e: SignalEvent): List<EngineOutput> {
        lastAt = max(lastAt, e.at)
        val out = mutableListOf<EngineOutput>()
        val d = draft
        when (e) {
            is SignalEvent.Gear -> {
                vehicle.gear = e.gear
                sync(e.at)
                handle(machine.onGear(e.gear, e.at, d?.distance?.km() ?: 0.0, vehicle.lat, vehicle.lon), e.at, out)
            }
            is SignalEvent.Speed -> {
                vehicle.speedKmh = e.kmh
                d?.distance?.onSpeed(e.kmh, e.at)
                d?.safety?.onSpeed(e.kmh, e.at, vehicle.speedLimit, vehicle.driving)
            }
            is SignalEvent.Odometer -> {
                lastOdometer = e.km
                d?.distance?.onOdometer(e.km)
            }
            is SignalEvent.Wiper -> { vehicle.wiperOn = e.on; vehicle.wiperLevel = e.level }
            is SignalEvent.HighBeam -> vehicle.highBeam = e.on
            is SignalEvent.LowBeam -> vehicle.lowBeam = e.on
            is SignalEvent.Window -> vehicle.windows[e.position] = e.openRatio
            is SignalEvent.SeatOccupancy -> vehicle.occupancy[e.seat] = e.occupied
            is SignalEvent.SeatBelt -> vehicle.belts[e.seat] = e.buckled
            is SignalEvent.SeatHeat -> vehicle.seatHeat[e.seat] = e.level
            is SignalEvent.Defroster -> vehicle.defrost = e.on
            is SignalEvent.OutsideTemperature -> vehicle.outsideC = e.celsius
            is SignalEvent.Location -> {
                vehicle.lat = e.lat; vehicle.lon = e.lon; vehicle.altitude = e.altitude; vehicle.gpsFix = true
                if (d != null) {
                    d.distance.onLocation(e.lat, e.lon, e.at)
                    d.places.onLocation(e.lat, e.lon, e.altitude, anchors[AnchorType.HOME]?.point)
                    updateEndPlace(d)
                }
            }
            is SignalEvent.NaviAddress -> {
                vehicle.address = e.address
                // 내비 위치는 내비 즐겨찾기(집·회사)와 같은 좌표계라서 장소 판정에 함께 쓴다.
                if (vehicle.lat == null) { vehicle.lat = e.lat; vehicle.lon = e.lon }
                if (d != null) {
                    d.places.onAddress(e.address)
                    d.places.onLocation(e.lat, e.lon, 0.0, anchors[AnchorType.HOME]?.point, fromGps = false)
                    if (d.startPlace.address == null) d.startPlace = d.startPlace.copy(address = e.address, point = d.startPlace.point ?: GeoPoint(e.lat, e.lon))
                    d.endPlace = PlaceInfo(GeoPoint(e.lat, e.lon), e.address)
                }
            }
            is SignalEvent.NaviArrived -> {
                if (d != null) {
                    d.destinationName = e.name.ifBlank { d.destinationName }
                    d.arrived = true
                    if (e.address.isNotBlank()) d.endPlace = d.endPlace.copy(address = e.address)
                }
                handle(machine.onArrived(), e.at, out)
            }
            is SignalEvent.NaviPassPoint -> d?.places?.onPassPoint(e.kind, e.remainMeters)
            is SignalEvent.NaviSpeedLimit -> vehicle.speedLimit = e.kmh
            is SignalEvent.NaviCaution -> if (e.kind == "SCHOOL_ZONE") d?.safety?.onSchoolZone(e.remainMeters, vehicle.speedKmh, vehicle.speedLimit)
            is SignalEvent.NaviAnchor -> anchors[e.type] = Anchor(e.type, e.name, GeoPoint(e.lat, e.lon))
            is SignalEvent.Moment -> {
                val m = MomentRecord(e.at, e.text, e.source, vehicle.point, vehicle.address, momentContext())
                if (d != null) d.moments += m else out += EngineOutput.OrphanMoment(m)
            }
            is SignalEvent.NaviStatus, is SignalEvent.Unavailable -> Unit
        }
        if (e !is SignalEvent.Gear) sync(e.at)
        draft?.let { handle(machine.onProgress(it.distance.km()), e.at, out) }
        return out
    }

    fun onTick(now: Long): List<EngineOutput> {
        lastAt = max(lastAt, now)
        val out = mutableListOf<EngineOutput>()
        sync(now)
        handle(machine.onTick(now, draft?.distance?.km() ?: 0.0), now, out)
        return out
    }

    /** 지금 여정을 바로 마감한다(주차 30분을 기다리지 않음). */
    fun finalizeNow(now: Long): List<EngineOutput> {
        val out = mutableListOf<EngineOutput>()
        sync(now)
        draft?.let { d -> if (d.parkedAt == null) { d.driveSegStart?.let { d.drivingMs += max(0, now - it) }; d.driveSegStart = null; d.parkedAt = now } }
        handle(machine.forceFinalize(now), now, out)
        return out
    }

    /** 진행 중인 여정의 현재 요약(화면·체크포인트용). */
    fun current(now: Long = lastAt): JourneySummary? = draft?.let { summarize(it, now, final = false) }

    fun checkpoint(now: Long): EngineCheckpoint =
        EngineCheckpoint(vehicle, machine, draft, anchors.values.toList(), lastOdometer, now)

    fun restore(cp: EngineCheckpoint, now: Long): List<EngineOutput> {
        vehicle = cp.vehicle
        machine = cp.machine
        draft = cp.draft
        lastOdometer = cp.lastOdometer
        anchors.clear()
        cp.anchors.forEach { anchors[it.type] = it }
        lastAt = cp.savedAt
        val out = mutableListOf<EngineOutput>()
        handle(machine.onResumeAfterGap(now), now, out)
        return out
    }

    // ---------------------------------------------------------------- 내부

    private fun sync(at: Long) {
        val d = draft ?: return
        val km = d.distance.km()
        d.rain.sync(vehicle, at, km)
        d.light.sync(vehicle, at, km)
        d.wind.sync(vehicle, at, km)
        d.warmth.sync(vehicle, at, km)
        d.companions.sync(vehicle)
    }

    private fun handle(transitions: List<Transition>, at: Long, out: MutableList<EngineOutput>) {
        for (t in transitions) when (t) {
            is Transition.Start -> {
                val d = JourneyDraft(newId(), t.at)
                lastOdometer?.let { d.distance.onOdometer(it) }
                if (vehicle.gpsFix) vehicle.lat?.let { lat -> d.distance.onLocation(lat, vehicle.lon!!, t.at) }
                d.startPlace = placeOf(vehicle.point, vehicle.address)
                d.endPlace = d.startPlace
                d.driveSegStart = t.at
                d.safety.onDriveStart(t.at)
                vehicle.point?.let { d.places.onLocation(it.lat, it.lon, vehicle.altitude ?: 0.0, anchors[AnchorType.HOME]?.point, fromGps = vehicle.gpsFix) }
                vehicle.address?.let { d.places.onAddress(it) }
                draft = d
                sync(t.at)
                out += EngineOutput.Started(d.id, t.at)
            }
            Transition.Confirm -> draft?.confirmed = true
            Transition.Discard -> {
                draft?.let { out += EngineOutput.Discarded(it.id) }
                draft = null
            }
            is Transition.Park -> draft?.let { d ->
                d.driveSegStart?.let { d.drivingMs += max(0, t.at - it) }
                d.driveSegStart = null
                d.parkedAt = t.at
                d.safety.onPark(t.at)
                updateEndPlace(d)
                out += EngineOutput.Progress(summarize(d, t.at, final = false))
            }
            is Transition.Resume -> draft?.let { d ->
                if (t.stoppedMs >= 60_000) {
                    val p = placeOf(vehicle.point, vehicle.address)
                    d.places.stops += StopRecord(d.parkedAt ?: t.at, (t.stoppedMs / 60_000).toInt(), p.point, p.address, p.province)
                }
                d.safety.onResume(t.at, t.stoppedMs)
                d.driveSegStart = t.at
                d.parkedAt = null
                d.arrived = false
            }
            Transition.GiftReady -> draft?.let { out += EngineOutput.GiftReady(summarize(it, at, final = false)) }
            is Transition.Finalize -> draft?.let { d ->
                out += EngineOutput.Finished(summarize(d, at, final = true))
                draft = null
            }
        }
    }

    private fun updateEndPlace(d: JourneyDraft) {
        d.endPlace = placeOf(vehicle.point, vehicle.address ?: d.endPlace.address)
    }

    private fun placeOf(point: GeoPoint?, address: String?): PlaceInfo {
        val r = RegionParser.parse(address)
        return PlaceInfo(point, address, r.province ?: point?.let { ProvinceLocator.locate(it.lat, it.lon) }, r.district)
    }

    private fun momentContext(): List<String> = buildList {
        if (vehicle.wiperOn) add("비")
        if (vehicle.highBeam) add("별빛")
        if (vehicle.windows.values.any { it >= 0.2f }) add("바람")
        val seated = 1 + com.hour.domain.signal.SeatPosition.entries.count { it != com.hour.domain.signal.SeatPosition.DRIVER && vehicle.seated(it) }
        if (seated >= 2) add("${seated}명")
        vehicle.address?.let { add(it) }
    }

    private fun summarize(d: JourneyDraft, now: Long, final: Boolean): JourneySummary {
        val km = d.distance.km()
        val endedAt = d.parkedAt ?: now
        val drivingMs = d.drivingMs + (d.driveSegStart?.let { max(0, now - it) } ?: 0)
        val office = anchors[AnchorType.OFFICE]?.point
        val officeReason = when {
            office == null -> null
            near(d.startPlace.point, office) -> "회사에서 출발했어요"
            near(d.endPlace.point, office) -> "회사에 도착했어요"
            d.destinationName != null && d.destinationName == anchors[AnchorType.OFFICE]?.name -> "회사에 도착했어요"
            else -> null
        }
        val pl = d.places
        val base = JourneySummary(
            id = d.id,
            startedAt = d.startedAt,
            endedAt = endedAt,
            final = final,
            distanceKm = km,
            drivingMinutes = (drivingMs / 60_000).toInt(),
            start = d.startPlace,
            end = d.endPlace,
            destinationName = d.destinationName,
            arrivedAtDestination = d.arrived,
            endDwellMinutes = d.parkedAt?.let { ((now - it) / 60_000).toInt() } ?: 0,
            rain = d.rain.summary(now, km),
            light = d.light.summary(now, km),
            wind = d.wind.summary(now, km),
            warmth = d.warmth.summary(now),
            companions = d.companions.summary(),
            places = PlacesSummary(
                provinces = pl.provinces.sorted(),
                districts = pl.districts.sorted(),
                maxKmFromHome = pl.maxKmFromHome,
                maxKmFromStart = pl.maxKmFromStart,
                touchedOffice = officeReason != null,
                officeReason = officeReason,
                tunnels = pl.count(PassPointKind.TUNNEL),
                serviceAreas = pl.count(PassPointKind.SERVICE_AREA),
                restAreas = pl.count(PassPointKind.REST_AREA),
                tollgates = pl.count(PassPointKind.TOLLGATE),
                stops = pl.stops.toList(),
                maxAltitude = pl.maxAltitude,
            ),
            safety = d.safety.summary(now),
            moments = d.moments.toList(),
            path = pl.path.toList(),
        )
        val withSuggestion = base.copy(suggestion = TripCategoryClassifier.suggest(base))
        return withSuggestion.copy(title = Storyteller.title(withSuggestion), narrative = Storyteller.narrative(withSuggestion))
    }

    private fun near(p: GeoPoint?, anchor: GeoPoint): Boolean =
        p != null && haversineMeters(p.lat, p.lon, anchor.lat, anchor.lon) <= OFFICE_RADIUS_M

    companion object {
        const val OFFICE_RADIUS_M = 300.0
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }
    }
}
