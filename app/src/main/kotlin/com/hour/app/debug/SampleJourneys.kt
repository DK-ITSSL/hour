package com.hour.app.debug

import com.hour.app.data.JourneyRepository
import com.hour.domain.geo.SunCalculator
import com.hour.domain.journey.CategorySource
import com.hour.domain.journey.CategorySuggestion
import com.hour.domain.journey.CompanionSummary
import com.hour.domain.journey.GeoPoint
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.LightSummary
import com.hour.domain.journey.MomentRecord
import com.hour.domain.journey.PlaceInfo
import com.hour.domain.journey.PlacesSummary
import com.hour.domain.journey.RainSummary
import com.hour.domain.journey.SafetySummary
import com.hour.domain.journey.TripCategory
import com.hour.domain.journey.WarmthSummary
import com.hour.domain.journey.WindSummary
import com.hour.domain.signal.MomentSource
import com.hour.domain.signal.SeatPosition
import com.hour.domain.signal.WindowPosition
import com.hour.domain.story.Storyteller
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.sin

/**
 * 체험·시연용 샘플 데이터. id가 "sample-"로 시작해 실제 기록과 구분되고, 화면에는 "샘플" 표시가 붙는다.
 * 설정 화면(모든 빌드)과 진단 화면·adb 인텐트(디버그)에서 넣는다.
 * - pastWeek: 지난 며칠의 출퇴근·일상·데이트 여정 5건(여행은 실시간 시연용으로 남겨 둔다)
 * - arrivalPreview: 강릉 가족 여행 도착 직전 상태(운행타입 확인 → 도착 선물 흐름 확인용)
 */
object SampleJourneys {
    private val home = PlaceInfo(GeoPoint(37.3670, 127.1080), "경기 성남시 분당구 정자동", "경기", "분당구")
    private val office = PlaceInfo(GeoPoint(37.4979, 127.0276), "서울 강남구 역삼동", "서울", "강남구")
    private val mart = PlaceInfo(GeoPoint(37.3595, 127.1140), "경기 성남시 분당구 금곡동", "경기", "분당구")
    private val fortress = PlaceInfo(GeoPoint(37.4785, 127.1820), "경기 광주시 남한산성면", "경기", "광주시")
    private val gyeongpo = PlaceInfo(GeoPoint(37.8055, 128.9080), "강원 강릉시 강문동", "강원", "강릉시")

    private val clean = SafetySummary(speedSamples = 600, limitCoverage = 0.9)
    private val solo = CompanionSummary(listOf(SeatPosition.DRIVER), rearKnown = true, allBelted = true)

    fun pastWeek(now: Long): List<JourneySummary> {
        val today = Instant.ofEpochMilli(now).atZone(SunCalculator.KST).toLocalDate()
        fun at(daysAgo: Long, h: Int, m: Int): ZonedDateTime = today.minusDays(daysAgo).atTime(h, m).atZone(SunCalculator.KST)
        return listOf(
            make(
                at(4, 8, 5), 41, 18.4, home, office, "회사", TripCategory.COMMUTE,
                rain = RainSummary(minutes = 22, km = 9.5, peakLevel = 2, firstMinute = 6),
                places = PlacesSummary(provinces = listOf("경기", "서울"), touchedOffice = true, maxKmFromHome = 16.0),
            ),
            make(
                at(4, 19, 10), 42, 18.9, office, home, "집", TripCategory.COMMUTE,
                light = LightSummary(nightKm = 18.9, nightMinutes = 42),
                places = PlacesSummary(provinces = listOf("서울", "경기"), touchedOffice = true, maxKmFromHome = 16.0),
            ),
            make(
                at(3, 14, 0), 12, 3.2, home, mart, "동네 마트", TripCategory.DAILY,
                warmth = WarmthSummary(seatHeatMinutes = 10),
            ),
            make(
                at(2, 16, 40), 45, 22.3, home, fortress, "남한산성", TripCategory.DATE,
                light = LightSummary(sunsetDrive = true, nightMinutes = 8, nightKm = 3.0),
                wind = WindSummary(km = 4.1, minutes = 8, windows = listOf(WindowPosition.FRONT_RIGHT)),
                companions = CompanionSummary(listOf(SeatPosition.DRIVER, SeatPosition.FRONT_PASSENGER), frontOnly = true, rearKnown = true, allBelted = true),
                moments = listOf("노을이 진짜 예뻤어"),
            ),
            make(
                at(1, 8, 10), 40, 18.2, home, office, "회사", TripCategory.COMMUTE,
                places = PlacesSummary(provinces = listOf("경기", "서울"), touchedOffice = true, maxKmFromHome = 16.0),
            ),
        )
    }

    /** 강릉 경포해변 도착 직전(운행타입 미확정). */
    fun arrivalPreview(now: Long): JourneySummary {
        val start = Instant.ofEpochMilli(now - 165 * 60_000L).atZone(SunCalculator.KST)
        return make(
            start, 158, 212.4, home, gyeongpo, "경포해변", category = null,
            rain = RainSummary(minutes = 25, km = 31.0, peakLevel = 3, firstMinute = 52),
            wind = WindSummary(km = 12.3, minutes = 14, windows = listOf(WindowPosition.REAR_LEFT, WindowPosition.REAR_RIGHT)),
            light = LightSummary(highBeamKm = 8.4, highBeamMinutes = 9, nightKm = 20.0, nightMinutes = 22, sunsetDrive = true),
            companions = CompanionSummary(
                listOf(SeatPosition.DRIVER, SeatPosition.FRONT_PASSENGER, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT),
                rearKnown = true, allBelted = true,
            ),
            places = PlacesSummary(
                provinces = listOf("경기", "강원"), districts = listOf("분당구", "강릉시"),
                maxKmFromHome = 165.0, tunnels = 6, serviceAreas = 1,
            ),
            moments = listOf("애들이 무지개 봤어"),
            final = false,
            endDwellMinutes = 12,
            suggestion = CategorySuggestion(TripCategory.TRIP, "집에서 165km 떨어진 곳까지 왔어요"),
        )
    }

    private fun make(
        start: ZonedDateTime,
        minutes: Int,
        km: Double,
        from: PlaceInfo,
        to: PlaceInfo,
        destination: String,
        category: TripCategory?,
        rain: RainSummary = RainSummary(),
        light: LightSummary = LightSummary(),
        wind: WindSummary = WindSummary(),
        warmth: WarmthSummary = WarmthSummary(),
        companions: CompanionSummary = solo,
        places: PlacesSummary = PlacesSummary(provinces = listOfNotNull(from.province, to.province).distinct()),
        moments: List<String> = emptyList(),
        final: Boolean = true,
        endDwellMinutes: Int = 30,
        suggestion: CategorySuggestion? = category?.let { CategorySuggestion(it, "시연용 샘플") },
    ): JourneySummary {
        val startMs = start.toInstant().toEpochMilli()
        val endMs = startMs + minutes * 60_000L
        val path = path(from.point!!, to.point!!)
        val draft = JourneySummary(
            id = JourneyRepository.SAMPLE_PREFIX + UUID.randomUUID().toString(),
            startedAt = startMs,
            endedAt = endMs,
            final = final,
            distanceKm = km,
            drivingMinutes = minutes,
            start = from,
            end = to,
            destinationName = destination,
            arrivedAtDestination = true,
            endDwellMinutes = endDwellMinutes,
            rain = rain,
            light = light,
            wind = wind,
            warmth = warmth,
            companions = companions,
            places = places,
            safety = clean,
            moments = moments.mapIndexed { i, text ->
                MomentRecord(startMs + (minutes * 60_000L) * (i + 1) / (moments.size + 1), text, MomentSource.GLEO, path[path.size / 2], to.address)
            },
            path = path,
            suggestion = suggestion,
            category = category,
            categorySource = category?.let { CategorySource.USER },
        )
        return draft.copy(title = Storyteller.title(draft), narrative = Storyteller.narrative(draft))
    }

    private fun path(a: GeoPoint, b: GeoPoint, n: Int = 32): List<GeoPoint> = (0..n).map { i ->
        val t = i / n.toDouble()
        val wiggle = sin(t * Math.PI * 3) * 0.08 * (b.lon - a.lon).let { if (kotlin.math.abs(it) < 0.05) 0.05 else it }
        GeoPoint(a.lat + (b.lat - a.lat) * t + wiggle * 0.3, a.lon + (b.lon - a.lon) * t)
    }
}
