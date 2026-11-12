package com.donghaengrok.domain.journey

import com.donghaengrok.domain.signal.AnchorType
import com.donghaengrok.domain.signal.MomentSource
import com.donghaengrok.domain.signal.SeatPosition
import com.donghaengrok.domain.signal.WindowPosition
import kotlinx.serialization.Serializable

/** 운행타입. 해금 애셋이 타입별로 다르다(블루프린트 §7.1, §9). */
@Serializable
enum class TripCategory(val korean: String, val gleoName: String) {
    COMMUTE("출퇴근길", "commute"),
    TRIP("여행", "trip"),
    DATE("데이트", "date"),
    DAILY("일상", "daily");

    companion object {
        fun fromGleo(name: String?): TripCategory? = entries.firstOrNull { it.gleoName == name?.lowercase() }
    }
}

@Serializable
enum class CategorySource { USER, GLEO, AUTO_DEFAULT }

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class Anchor(val type: AnchorType, val name: String, val point: GeoPoint)

@Serializable
data class PlaceInfo(
    val point: GeoPoint? = null,
    val address: String? = null,
    val province: String? = null,
    val district: String? = null,
)

@Serializable
data class CategorySuggestion(val category: TripCategory, val reason: String)

@Serializable
data class RainSummary(val minutes: Int = 0, val km: Double = 0.0, val peakLevel: Int = 0, val firstMinute: Int? = null) {
    val present: Boolean get() = minutes > 0 || km > 0.0
}

@Serializable
data class LightSummary(
    val highBeamKm: Double = 0.0,
    val highBeamMinutes: Int = 0,
    val nightKm: Double = 0.0,
    val nightMinutes: Int = 0,
    val sunsetDrive: Boolean = false,
) {
    val starlight: Boolean get() = highBeamMinutes > 0 || highBeamKm > 0.0
}

@Serializable
data class WindSummary(
    val km: Double = 0.0,
    val minutes: Int = 0,
    val windows: List<WindowPosition> = emptyList(),
    val sunroofKm: Double = 0.0,
) {
    val present: Boolean get() = minutes > 0 || km > 0.0
}

@Serializable
data class WarmthSummary(
    val seatHeatMinutes: Int = 0,
    val seatVentMinutes: Int = 0,
    val defrostMinutes: Int = 0,
    val outsideMinC: Float? = null,
    val outsideMaxC: Float? = null,
)

@Serializable
data class CompanionSummary(
    val seats: List<SeatPosition> = emptyList(),
    /** 운전석·조수석만 착석했고 뒷좌석은 비어 있다(뒷좌석 신호가 없으면 rearKnown=false). */
    val frontOnly: Boolean = false,
    val rearKnown: Boolean = false,
    /** 착석한 모든 좌석이 운행 내내 체결됐는지. 벨트 신호가 없으면 null(판정 불가). */
    val allBelted: Boolean? = null,
) {
    val count: Int get() = seats.size
}

@Serializable
data class StopRecord(val at: Long, val minutes: Int, val point: GeoPoint? = null, val address: String? = null, val province: String? = null)

@Serializable
data class PlacesSummary(
    val provinces: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val maxKmFromHome: Double? = null,
    val touchedOffice: Boolean = false,
    val officeReason: String? = null,
    val tunnels: Int = 0,
    val serviceAreas: Int = 0,
    val restAreas: Int = 0,
    val tollgates: Int = 0,
    val stops: List<StopRecord> = emptyList(),
    val maxAltitude: Double? = null,
)

/**
 * 안심 보상 판정용 집계(블루프린트 §9.1). 속도 궤적은 저장하지 않고 횟수만 남긴다.
 * judgeable=false면 판정 불가(안전한 것으로 간주하지 않는다).
 */
@Serializable
data class SafetySummary(
    val speedSamples: Int = 0,
    val limitCoverage: Double = 0.0,
    val overspeedEpisodes: Int = 0,
    val harshEvents: Int = 0,
    val restBreaksBefore2h: Int = 0,
    val longestContinuousMinutes: Int = 0,
    val schoolZonePasses: Int = 0,
    val schoolZoneCompliant: Int = 0,
) {
    val speedJudgeable: Boolean get() = speedSamples >= 10
    val limitJudgeable: Boolean get() = speedJudgeable && limitCoverage >= 0.7
}

@Serializable
data class MomentRecord(
    val at: Long,
    val text: String,
    val source: MomentSource,
    val point: GeoPoint? = null,
    val address: String? = null,
    val context: List<String> = emptyList(),
)

/** 한 여정의 결과. 임시 마감(도착 선물)과 최종 마감 모두 이 형태로 저장한다. */
@Serializable
data class JourneySummary(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val final: Boolean = false,
    val distanceKm: Double = 0.0,
    val drivingMinutes: Int = 0,
    val start: PlaceInfo = PlaceInfo(),
    val end: PlaceInfo = PlaceInfo(),
    val destinationName: String? = null,
    val arrivedAtDestination: Boolean = false,
    val endDwellMinutes: Int = 0,
    val rain: RainSummary = RainSummary(),
    val light: LightSummary = LightSummary(),
    val wind: WindSummary = WindSummary(),
    val warmth: WarmthSummary = WarmthSummary(),
    val companions: CompanionSummary = CompanionSummary(),
    val places: PlacesSummary = PlacesSummary(),
    val safety: SafetySummary = SafetySummary(),
    val moments: List<MomentRecord> = emptyList(),
    val path: List<GeoPoint> = emptyList(),
    val suggestion: CategorySuggestion? = null,
    val category: TripCategory? = null,
    val categorySource: CategorySource? = null,
    val title: String = "",
    val narrative: String = "",
)
