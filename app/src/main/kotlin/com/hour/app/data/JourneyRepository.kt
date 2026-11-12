package com.hour.app.data

import com.hour.domain.engine.EngineCheckpoint
import com.hour.domain.engine.JourneyEngine
import com.hour.domain.journey.Anchor
import com.hour.domain.journey.CategorySource
import com.hour.domain.journey.GeoPoint
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.MomentRecord
import com.hour.domain.journey.TripCategory
import com.hour.domain.signal.AnchorType
import com.hour.domain.unlock.RuleLoader
import com.hour.domain.unlock.UnlockEngine
import com.hour.domain.unlock.UnlockRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UnlockView(val rule: UnlockRule?, val ruleId: String, val unlockedAt: Long, val journeyId: String)

/** 여정·해금·집/회사·체크포인트 저장소. 쓰기는 Mutex로 직렬화한다. */
class JourneyRepository(private val dao: JourneyDao, private val log: (String) -> Unit) {
    private val json = JourneyEngine.json
    private val mutex = Mutex()
    val rules: List<UnlockRule>

    init {
        val (ok, errors) = RuleLoader.loadDefault()
        rules = ok
        errors.forEach { log("rules: rejected $it") }
    }

    val journeys: Flow<List<JourneySummary>> =
        dao.observeAll().map { list -> list.mapNotNull { decode(it.summaryJson) } }

    val unlocks: Flow<List<UnlockView>> = dao.observeUnlocks().map { list ->
        list.map { u -> UnlockView(rules.firstOrNull { it.id == u.ruleId }, u.ruleId, u.unlockedAt, u.journeyId) }
    }

    /** 진행 중·임시 마감 결과 저장(운행타입이 이미 확정됐으면 유지한다). */
    suspend fun saveProgress(summary: JourneySummary) = mutex.withLock {
        val prev = dao.get(summary.id)?.let { decode(it.summaryJson) }
        val merged = if (prev?.category != null) summary.copy(category = prev.category, categorySource = prev.categorySource) else summary
        dao.upsert(entityOf(merged))
    }

    /**
     * 최종 마감. 사용자가 아직 운행타입을 고르지 않았으면 추천값을 AUTO_DEFAULT로 적용하고 해금을 평가한다.
     * (도착 시점 확인 화면은 D3에서 붙인다.)
     */
    suspend fun finish(summary: JourneySummary): List<UnlockRule> {
        val prev = mutex.withLock { dao.get(summary.id)?.let { decode(it.summaryJson) } }
        val category = prev?.category ?: summary.suggestion?.category
        val source = prev?.categorySource ?: CategorySource.AUTO_DEFAULT
        val done = summary.copy(category = category, categorySource = if (category != null) source else null)
        mutex.withLock { dao.upsert(entityOf(done)) }
        return evaluateUnlocks(done.id)
    }

    /** 사용자(화면·Gleo)가 운행타입을 확정한다. 해금을 다시 평가한다(이미 연 애셋은 유지). */
    suspend fun confirmCategory(journeyId: String, category: TripCategory, source: CategorySource): List<UnlockRule> {
        mutex.withLock {
            val cur = dao.get(journeyId)?.let { decode(it.summaryJson) } ?: return emptyList()
            dao.upsert(entityOf(cur.copy(category = category, categorySource = source)))
        }
        return evaluateUnlocks(journeyId)
    }

    suspend fun latestJourneyId(): String? = dao.latest()?.id

    suspend fun discard(journeyId: String) = mutex.withLock { dao.delete(journeyId) }

    suspend fun attachMoment(m: MomentRecord): String? = mutex.withLock {
        val latest = dao.latest() ?: return null
        val s = decode(latest.summaryJson) ?: return null
        dao.upsert(entityOf(s.copy(moments = s.moments + m)))
        latest.id
    }

    private suspend fun evaluateUnlocks(journeyId: String): List<UnlockRule> = mutex.withLock {
        val all = dao.confirmed().mapNotNull { decode(it.summaryJson) }
        val current = all.firstOrNull { it.id == journeyId } ?: return emptyList()
        val already = dao.unlockedIds().toSet()
        val now = System.currentTimeMillis()
        val got = UnlockEngine.evaluate(rules, current, all.filter { it.id != journeyId }, already)
        if (got.isNotEmpty()) dao.insertUnlocks(got.map { UnlockEntity(it.rule.id, now, it.journeyId) })
        got.map { it.rule }
    }

    suspend fun saveAnchor(a: Anchor, source: String) =
        dao.upsertAnchor(AnchorEntity(a.type.name, a.name, a.point.lat, a.point.lon, source))

    suspend fun anchors(): List<Anchor> = dao.anchors().mapNotNull { e ->
        runCatching { Anchor(AnchorType.valueOf(e.type), e.name, GeoPoint(e.lat, e.lon)) }.getOrNull()
    }

    suspend fun saveCheckpoint(cp: EngineCheckpoint) =
        dao.saveCheckpoint(CheckpointEntity(json = json.encodeToString(EngineCheckpoint.serializer(), cp), savedAt = cp.savedAt))

    suspend fun loadCheckpoint(): EngineCheckpoint? = dao.checkpoint()?.let {
        runCatching { json.decodeFromString(EngineCheckpoint.serializer(), it.json) }
            .onFailure { e -> log("checkpoint: decode failed $e") }.getOrNull()
    }

    suspend fun clearCheckpoint() = dao.clearCheckpoint()

    private fun entityOf(s: JourneySummary) = JourneyEntity(
        id = s.id, startedAt = s.startedAt, endedAt = s.endedAt, final = s.final,
        category = s.category?.name, categorySource = s.categorySource?.name,
        title = s.title, distanceKm = s.distanceKm,
        summaryJson = json.encodeToString(JourneySummary.serializer(), s),
    )

    private fun decode(text: String): JourneySummary? =
        runCatching { json.decodeFromString(JourneySummary.serializer(), text) }.getOrNull()
}
