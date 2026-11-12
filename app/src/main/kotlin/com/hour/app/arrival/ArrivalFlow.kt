package com.hour.app.arrival

import com.hour.app.data.JourneyRepository
import com.hour.app.data.Prefs
import com.hour.app.diag.LogStore
import com.hour.app.text.Korean
import com.hour.car.VoiceIO
import com.hour.domain.journey.CategorySource
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.TripCategory
import com.hour.domain.unlock.UnlockRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 화면에 띄울 도착 선물. */
data class Gift(val journey: JourneySummary, val unlocked: List<UnlockRule>)

/**
 * 도착(P) → 운행타입 확인 → 해금 평가 → 도착 선물.
 * 확인은 화면 시트, 알림 버튼, Gleo SET_TRIP_CATEGORY 어느 쪽으로 와도 이 클래스를 거친다.
 * 확인 전에는 해금을 평가하지 않는다(자동 추천값으로 엉뚱한 타입의 애셋이 먼저 열리는 문제 방지).
 */
class ArrivalFlow(
    private val scope: CoroutineScope,
    private val journeys: JourneyRepository,
    private val notifier: ArrivalNotifier,
    private val voice: VoiceIO,
    private val prefs: Prefs,
    private val log: LogStore,
) {
    private val _pending = MutableStateFlow<JourneySummary?>(null)
    /** 운행타입 확인을 기다리는 여정. null이 아니면 확인 시트를 띄운다. */
    val pending: StateFlow<JourneySummary?> = _pending.asStateFlow()

    private val _gift = MutableStateFlow<Gift?>(null)
    val gift: StateFlow<Gift?> = _gift.asStateFlow()

    /** 기록 서비스가 도착(선물 준비)을 알렸다. */
    fun onGiftReady(s: JourneySummary) {
        val already = s.category
        if (already != null) {
            // 운행 중 Gleo로 먼저 정한 경우: 바로 선물을 연다.
            scope.launch { confirm(s.id, already, s.categorySource ?: CategorySource.USER) }
            return
        }
        _pending.value = s
        notifier.arrival(s)
        if (prefs.narration.value) voice.speak(askLine(s))
        log.add("arrival: ask category for ${s.id.take(8)} (추천 ${s.suggestion?.category?.korean})")
    }

    /** 홈의 "어떤 길이었나요?" 배너, 알림 탭 등으로 확인 시트를 다시 연다. */
    fun ask(s: JourneySummary) {
        if (s.category == null) _pending.value = s
    }

    /** 이미 정한 운행타입을 바꾼다(여정 상세). 자동 적용 카운트다운 없이 시트를 연다. */
    fun change(s: JourneySummary) {
        _pending.value = s
    }

    /** 시트를 닫는다(나중에). 확인하지 않으면 최종 마감 때 추천값이 적용된다. */
    fun later() {
        _pending.value = null
    }

    suspend fun confirm(journeyId: String, category: TripCategory, source: CategorySource): List<UnlockRule> {
        val got = journeys.confirmCategory(journeyId, category, source)
        val j = journeys.journey(journeyId) ?: return got
        if (_pending.value?.id == journeyId) _pending.value = null
        notifier.cancelArrival()
        _gift.value = Gift(j, got)
        notifier.gift(j, got)
        if (prefs.narration.value) voice.speak(giftLine(j, got))
        log.add("arrival: ${category.korean} 확정($source) → 해금 ${got.map { it.name }}")
        return got
    }

    /** 최종 마감에서 자동 추천값이 적용됐다. 새로 열린 것이 있으면 다음에 앱을 열 때 선물로 보여준다. */
    suspend fun onFinished(journeyId: String, unlocked: List<UnlockRule>) {
        if (_pending.value?.id == journeyId) _pending.value = null
        notifier.cancelArrival()
        if (unlocked.isEmpty()) return
        val j = journeys.journey(journeyId) ?: return
        _gift.value = Gift(j, unlocked)
        notifier.gift(j, unlocked)
    }

    fun dismissGift() {
        _gift.value = null
        notifier.cancelGift()
    }

    /** 디버그·시연용: 확인 시트와 선물을 비운다. */
    fun clear() {
        _pending.value = null
        _gift.value = null
        notifier.cancelArrival()
        notifier.cancelGift()
    }

    companion object {
        fun askLine(s: JourneySummary): String {
            val sug = s.suggestion ?: return "도착했어요. 이번 운행은 어떤 길이었나요?"
            val reason = sug.reason.trimEnd('.', ' ')
            return "도착했어요. $reason. 이번 여정을 ${Korean.euroRo(sug.category.korean)} 기록할까요?"
        }

        fun giftLine(j: JourneySummary, unlocked: List<UnlockRule>): String =
            if (unlocked.isEmpty()) {
                "${Korean.euroRo(j.category?.korean ?: "오늘의 여정")} 기록했어요. 이번 여정도 아워가 기억해 둘게요."
            } else {
                val names = unlocked.map { it.name }
                val list = if (names.size == 1) {
                    names[0]
                } else {
                    val head = names.dropLast(1)
                    (head.dropLast(1) + Korean.gwaWa(head.last())).joinToString(", ") + " " + names.last()
                }
                "도착 선물로 ${Korean.eulReul(list)} 받았어요. 옷장에서 입혀 보세요."
            }
    }
}
