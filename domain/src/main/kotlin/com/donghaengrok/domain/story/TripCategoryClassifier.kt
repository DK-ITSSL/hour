package com.donghaengrok.domain.story

import com.donghaengrok.domain.journey.CategorySuggestion
import com.donghaengrok.domain.journey.JourneySummary
import com.donghaengrok.domain.journey.TripCategory
import kotlin.math.roundToInt

/**
 * 운행타입 추천(블루프린트 §7.1). 위에서부터 먼저 맞는 것이 1순위이고, 확정은 사용자가 한다.
 * 1) 출발지·도착지가 회사 300m 안 → 출퇴근길
 * 2) 집에서 직선거리 100km 이상 벗어남 → 여행
 * 3) 운전석·조수석만 착석(회사와 무관) → 데이트
 * 4) 그 외 → 일상
 */
object TripCategoryClassifier {
    const val TRIP_KM = 100.0

    fun suggest(s: JourneySummary): CategorySuggestion {
        if (s.places.touchedOffice) {
            return CategorySuggestion(TripCategory.COMMUTE, s.places.officeReason ?: "회사를 오간 운행이에요")
        }
        val far = s.places.maxKmFromHome
        if (far != null && far >= TRIP_KM) {
            return CategorySuggestion(TripCategory.TRIP, "집에서 ${far.roundToInt()}km 떨어진 곳까지 왔어요")
        }
        if (s.companions.frontOnly) {
            return CategorySuggestion(TripCategory.DATE, "앞좌석에 두 분이 함께했어요")
        }
        return CategorySuggestion(TripCategory.DAILY, "가까운 곳을 다녀왔어요")
    }
}
