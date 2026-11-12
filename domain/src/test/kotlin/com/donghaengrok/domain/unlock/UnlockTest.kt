package com.donghaengrok.domain.unlock

import com.donghaengrok.domain.journey.JourneySummary
import com.donghaengrok.domain.journey.LightSummary
import com.donghaengrok.domain.journey.PlaceInfo
import com.donghaengrok.domain.journey.PlacesSummary
import com.donghaengrok.domain.journey.RainSummary
import com.donghaengrok.domain.journey.SafetySummary
import com.donghaengrok.domain.journey.TripCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockTest {
    private val rules = RuleLoader.loadDefault().also { assertTrue(it.second.isEmpty(), "rule errors ${it.second}") }.first

    private fun trip(id: String, category: TripCategory, start: Long = 1_000_000L) = JourneySummary(
        id = id, startedAt = start, endedAt = start + 3_600_000, final = true, distanceKm = 160.0,
        rain = RainSummary(minutes = 30, km = 40.0, peakLevel = 3),
        light = LightSummary(highBeamKm = 5.0, highBeamMinutes = 10),
        end = PlaceInfo(province = "강원"), endDwellMinutes = 35,
        places = PlacesSummary(provinces = listOf("경기", "강원")),
        safety = SafetySummary(speedSamples = 500, limitCoverage = 0.9),
        category = category,
    )

    @Test
    fun `기본 규칙 파일을 읽고 안전 원칙을 통과한다`() {
        assertTrue(rules.size >= 13)
    }

    @Test
    fun `비 오는 강릉 여행을 확정하면 여행 애셋이 열린다`() {
        val got = UnlockEngine.evaluate(rules, trip("t1", TripCategory.TRIP), emptyList(), emptySet()).map { it.rule.id }.toSet()
        assertTrue(setOf("trip_raincoat", "trip_star_cape", "trip_passport", "trip_gangwon_raincoat", "trip_safe_charm").all { it in got }, "$got")
        assertTrue(got.none { it.startsWith("commute_") || it.startsWith("date_") })
    }

    @Test
    fun `이미 연 애셋은 다시 열지 않고 운행타입이 다르면 그 타입 애셋만 열린다`() {
        val got = UnlockEngine.evaluate(rules, trip("t2", TripCategory.COMMUTE), emptyList(), setOf("commute_umbrella")).map { it.rule.id }
        assertTrue("commute_umbrella" !in got)
        assertTrue(got.none { it.startsWith("trip_") })
    }

    @Test
    fun `운행타입이 확정되지 않으면 아무것도 열리지 않는다`() {
        assertEquals(emptyList<Unlocked>(), UnlockEngine.evaluate(rules, trip("t3", TripCategory.TRIP).copy(category = null), emptyList(), emptySet()))
    }

    @Test
    fun `판정 불가한 여정은 안심 보상을 주지 않는다`() {
        val noLimit = trip("t4", TripCategory.TRIP).copy(safety = SafetySummary(speedSamples = 500, limitCoverage = 0.1))
        val got = UnlockEngine.evaluate(rules, noLimit, emptyList(), emptySet()).map { it.rule.id }
        assertTrue("trip_safe_charm" !in got)
    }

    @Test
    fun `안전 원칙 - 빗길·속도 누적 보상 규칙은 거부한다`() {
        val bad = UnlockRule("bad", "빗길 100km", AssetSlot.HAT, condition = Condition.SenseKmTotalGte(Sense.RAIN, 100.0))
        val (ok, errors) = RuleSafetyPolicy.validate(listOf(bad))
        assertTrue(ok.isEmpty() && errors.size == 1)
    }

    @Test
    fun `출퇴근 5일이면 사원증`() {
        val day = 86_400_000L
        val history = (0 until 4).map { trip("c$it", TripCategory.COMMUTE, start = it * day) }
        val got = UnlockEngine.evaluate(rules, trip("c4", TripCategory.COMMUTE, start = 4 * day), history, emptySet()).map { it.rule.id }
        assertTrue("commute_badge" in got, "$got")
    }
}
