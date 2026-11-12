package com.donghaengrok.domain.unlock

import com.donghaengrok.domain.journey.JourneySummary
import com.donghaengrok.domain.journey.TripCategory
import java.time.Instant
import com.donghaengrok.domain.geo.SunCalculator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 해금 규칙(블루프린트 §9). 퀘스트·애셋 구성은 게임 디자인 단계에서 다시 정하므로,
 * 엔진은 조건 타입만 제공하고 실제 규칙은 assets/unlock_rules.json에서 읽는다.
 */
@Serializable
enum class AssetSlot { HAT, OUTFIT, ACCESSORY, ROOM }

@Serializable
enum class Sense { RAIN, STARLIGHT, WIND, SUNSET, NIGHT, SEAT_HEAT, DEFROST, SUNROOF }

@Serializable
enum class SafetyItem { OVERSPEED, HARSH }

@Serializable
data class UnlockRule(
    val id: String,
    val name: String,
    val slot: AssetSlot,
    /** null이면 모든 운행타입. 지정하면 확정된 운행타입이 같을 때만 평가한다. */
    val category: TripCategory? = null,
    val condition: Condition,
    val hint: String = "",
)

@Serializable
sealed interface Condition {
    /** 이번 여정에 그 감각이 있었다(처음 한 번 열린다). */
    @Serializable @SerialName("FIRST_SENSE")
    data class FirstSense(val sense: Sense) : Condition

    /** 감각의 누적 거리(해당 운행타입 여정 합계). 안전 원칙상 WIND만 허용한다. */
    @Serializable @SerialName("SENSE_KM_TOTAL_GTE")
    data class SenseKmTotalGte(val sense: Sense, val km: Double) : Condition

    @Serializable @SerialName("FIRST_CATEGORY")
    data object FirstCategory : Condition

    @Serializable @SerialName("CATEGORY_COUNT_GTE")
    data class CategoryCountGte(val n: Int) : Condition

    @Serializable @SerialName("CATEGORY_DAYS_GTE")
    data class CategoryDaysGte(val days: Int) : Condition

    @Serializable @SerialName("NEW_PROVINCE")
    data object NewProvince : Condition

    @Serializable @SerialName("PROVINCE_COUNT_GTE")
    data class ProvinceCountGte(val n: Int) : Condition

    /** 지역 제휴 애셋: 그 시·도에서 P단으로 일정 시간 머물렀다. */
    @Serializable @SerialName("REGION_STAY")
    data class RegionStay(val province: String, val minutes: Int) : Condition

    @Serializable @SerialName("MOMENTS_GTE")
    data class MomentsGte(val n: Int) : Condition

    @Serializable @SerialName("TUNNELS_TOTAL_GTE")
    data class TunnelsTotalGte(val n: Int) : Condition

    @Serializable @SerialName("REST_AREAS_GTE")
    data class RestAreasGte(val n: Int) : Condition

    /** 안심 보상: 이번 여정에서 지정한 위험 행동이 없었다(판정 가능한 여정만). */
    @Serializable @SerialName("CLEAN_JOURNEY")
    data class CleanJourney(val items: List<SafetyItem>, val minKm: Double = 5.0) : Condition

    /** 안심 보상: 해당 운행타입의 최근 n개 여정이 모두 깨끗했다. */
    @Serializable @SerialName("CLEAN_STREAK")
    data class CleanStreak(val items: List<SafetyItem>, val n: Int, val minKm: Double = 5.0) : Condition

    @Serializable @SerialName("ALL_BELTED_COUNT")
    data class AllBeltedCount(val n: Int) : Condition

    @Serializable @SerialName("REST_BREAKS_GTE")
    data class RestBreaksGte(val n: Int) : Condition
}

@Serializable
data class RuleBook(val version: Int = 1, val rules: List<UnlockRule>)

/**
 * 안전 원칙: 위험 행동의 '발생'을 보상하지 않는다(속도·주행 시간·야간·빗길 누적 금지).
 * 누적 조건은 바람(창문)만 허용한다.
 */
object RuleSafetyPolicy {
    private val cumulativeAllowed = setOf(Sense.WIND)

    fun violation(rule: UnlockRule): String? = when (val c = rule.condition) {
        is Condition.SenseKmTotalGte ->
            if (c.sense !in cumulativeAllowed) "누적 조건은 ${cumulativeAllowed.joinToString()}만 허용(${c.sense})" else null
        is Condition.CleanJourney -> if (c.items.isEmpty()) "안심 조건 항목이 비어 있음" else null
        is Condition.CleanStreak -> if (c.items.isEmpty() || c.n < 1) "안심 조건이 잘못됨" else null
        else -> null
    }

    fun validate(rules: List<UnlockRule>): Pair<List<UnlockRule>, List<String>> {
        val ok = mutableListOf<UnlockRule>()
        val errors = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        rules.forEach { r ->
            val v = violation(r) ?: if (!ids.add(r.id)) "중복 id" else null
            if (v == null) ok += r else errors += "${r.id}: $v"
        }
        return ok to errors
    }
}

object RuleLoader {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    /** domain 모듈 리소스의 기본 규칙(임시). 게임 디자인이 확정되면 이 파일만 바꾼다. */
    fun loadDefault(): Pair<List<UnlockRule>, List<String>> {
        val text = RuleLoader::class.java.getResourceAsStream("/unlock_rules.json")?.bufferedReader()?.use { it.readText() }
            ?: return emptyList<UnlockRule>() to listOf("unlock_rules.json 없음")
        return load(text)
    }

    /** JSON을 읽고 안전 원칙을 어기는 규칙은 빼고 돌려준다. 걸러진 규칙은 errors로 알린다. */
    fun load(text: String): Pair<List<UnlockRule>, List<String>> =
        RuleSafetyPolicy.validate(json.decodeFromString(RuleBook.serializer(), text).rules)
}

/** 확정된 여정들로 계산한 누적 통계. */
class ArchiveStats(private val journeys: List<JourneySummary>) {
    fun of(category: TripCategory?): List<JourneySummary> =
        if (category == null) journeys else journeys.filter { it.category == category }
}

data class Unlocked(val rule: UnlockRule, val journeyId: String)

/**
 * 여정 하나가 확정(운행타입 포함)될 때 평가한다.
 * history: 이번 여정을 제외한 과거 확정 여정, alreadyUnlocked: 이미 연 규칙 id.
 */
object UnlockEngine {
    fun evaluate(
        rules: List<UnlockRule>,
        current: JourneySummary,
        history: List<JourneySummary>,
        alreadyUnlocked: Set<String>,
    ): List<Unlocked> {
        val category = current.category ?: return emptyList()
        val all = ArchiveStats(history.filter { it.id != current.id } + current)
        return rules.filter { it.id !in alreadyUnlocked }
            .filter { it.category == null || it.category == category }
            .filter { matches(it, current, all) }
            .map { Unlocked(it, current.id) }
    }

    private fun matches(rule: UnlockRule, j: JourneySummary, all: ArchiveStats): Boolean {
        val scoped = all.of(rule.category)
        return when (val c = rule.condition) {
            is Condition.FirstSense -> hasSense(j, c.sense)
            is Condition.SenseKmTotalGte -> scoped.sumOf { senseKm(it, c.sense) } >= c.km
            Condition.FirstCategory -> true // 운행타입 필터를 통과했으면 그 타입의 첫 여정에서 열린다
            is Condition.CategoryCountGte -> scoped.size >= c.n
            is Condition.CategoryDaysGte -> scoped.map { day(it.startedAt) }.distinct().size >= c.days
            Condition.NewProvince -> {
                val before = all.of(null).filter { it.id != j.id }.flatMap { it.places.provinces }.toSet()
                j.places.provinces.any { it !in before }
            }
            is Condition.ProvinceCountGte -> all.of(null).flatMap { it.places.provinces }.toSet().size >= c.n
            is Condition.RegionStay ->
                (j.end.province == c.province && j.endDwellMinutes >= c.minutes) ||
                    j.places.stops.any { it.province == c.province && it.minutes >= c.minutes }
            is Condition.MomentsGte -> all.of(null).sumOf { it.moments.size } >= c.n
            is Condition.TunnelsTotalGte -> scoped.sumOf { it.places.tunnels } >= c.n
            is Condition.RestAreasGte -> scoped.sumOf { it.places.serviceAreas + it.places.restAreas } >= c.n
            is Condition.CleanJourney -> clean(j, c.items, c.minKm)
            is Condition.CleanStreak -> {
                val recent = scoped.sortedByDescending { it.startedAt }.filter { it.distanceKm >= c.minKm }.take(c.n)
                recent.size == c.n && recent.all { clean(it, c.items, c.minKm) }
            }
            is Condition.AllBeltedCount -> scoped.count { it.companions.allBelted == true } >= c.n
            is Condition.RestBreaksGte -> scoped.sumOf { it.safety.restBreaksBefore2h } >= c.n
        }
    }

    private fun hasSense(j: JourneySummary, sense: Sense): Boolean = when (sense) {
        Sense.RAIN -> j.rain.present
        Sense.STARLIGHT -> j.light.starlight
        Sense.WIND -> j.wind.present
        Sense.SUNSET -> j.light.sunsetDrive
        Sense.NIGHT -> j.light.nightMinutes > 0
        Sense.SEAT_HEAT -> j.warmth.seatHeatMinutes > 0
        Sense.DEFROST -> j.warmth.defrostMinutes > 0
        Sense.SUNROOF -> j.wind.sunroofKm > 0
    }

    private fun senseKm(j: JourneySummary, sense: Sense): Double = when (sense) {
        Sense.WIND -> j.wind.km
        Sense.SUNROOF -> j.wind.sunroofKm
        Sense.RAIN -> j.rain.km
        Sense.STARLIGHT -> j.light.highBeamKm
        Sense.NIGHT -> j.light.nightKm
        else -> 0.0
    }

    /** 판정 불가한 여정은 깨끗한 것으로 보지 않는다. */
    private fun clean(j: JourneySummary, items: List<SafetyItem>, minKm: Double): Boolean {
        if (j.distanceKm < minKm) return false
        return items.all { item ->
            when (item) {
                SafetyItem.OVERSPEED -> j.safety.limitJudgeable && j.safety.overspeedEpisodes == 0
                SafetyItem.HARSH -> j.safety.speedJudgeable && j.safety.harshEvents == 0
            }
        }
    }

    private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(SunCalculator.KST).toLocalDate()
}
