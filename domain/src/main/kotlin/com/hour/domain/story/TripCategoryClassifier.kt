package com.hour.domain.story

import com.hour.domain.journey.CategorySuggestion
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.TripCategory
import kotlin.math.roundToInt

/**
 * 운행타입 추천(블루프린트 §7.1). 위에서부터 먼저 맞는 것이 1순위이고, 확정은 사용자가 한다.
 * 1) 출발지·도착지가 회사 300m 안 → 출퇴근길
 * 2) 집에서 직선거리 100km 이상 벗어남 → 여행 (집을 모르면 출발 지점 기준)
 * 3) 운전석·조수석만 착석(회사와 무관) → 데이트
 * 4) 그 외 → 일상
 */
object TripCategoryClassifier {
    const val TRIP_KM = 100.0

    fun suggest(s: JourneySummary): CategorySuggestion {
        if (s.places.touchedOffice) {
            return CategorySuggestion(TripCategory.COMMUTE, s.places.officeReason ?: "회사를 오간 운행이에요")
        }
        val fromHome = s.places.maxKmFromHome
        val fromStart = s.places.maxKmFromStart
        if (fromHome != null && fromHome >= TRIP_KM) {
            return CategorySuggestion(TripCategory.TRIP, "집에서 ${fromHome.roundToInt()}km 떨어진 곳까지 왔어요")
        }
        // 집 위치를 모르면(내비 즐겨찾기·앱 설정 모두 없음) 출발 지점을 기준으로 본다.
        if (fromHome == null && fromStart != null && fromStart >= TRIP_KM) {
            return CategorySuggestion(TripCategory.TRIP, "출발한 곳에서 ${fromStart.roundToInt()}km 떨어진 곳까지 왔어요")
        }
        if (s.companions.frontOnly) {
            return CategorySuggestion(TripCategory.DATE, "앞좌석에 두 분이 함께했어요")
        }
        return CategorySuggestion(TripCategory.DAILY, "가까운 곳을 다녀왔어요")
    }
}
