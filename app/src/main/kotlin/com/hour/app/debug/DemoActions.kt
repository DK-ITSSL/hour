package com.hour.app.debug

import com.hour.app.di.AppContainer

/** 체험·시연 준비 동작. 설정 화면(모든 빌드), 진단 화면과 adb 인텐트(디버그)가 함께 쓴다. */
object DemoActions {
    suspend fun seed(c: AppContainer) {
        SampleJourneys.pastWeek(System.currentTimeMillis()).forEach { s ->
            val got = c.journeys.saveSample(s)
            c.log.add("demo: sample ${s.category?.korean} \"${s.title}\" → ${got.map { it.name }}")
        }
    }

    suspend fun previewArrival(c: AppContainer) {
        val s = SampleJourneys.arrivalPreview(System.currentTimeMillis())
        c.journeys.saveSample(s)
        c.arrival.onGiftReady(s)
        c.log.add("demo: arrival preview \"${s.title}\"")
    }

    /** 샘플 여정과 샘플로 열린 선물만 지운다(실제 기록은 그대로). */
    suspend fun clearSamples(c: AppContainer) {
        c.journeys.clearSamples()
        c.arrival.clear()
        c.log.add("demo: samples cleared")
    }

    /** 전부 초기화(디버그 전용). */
    suspend fun reset(c: AppContainer) {
        c.journeys.resetAll()
        c.prefs.clearEquipped()
        c.arrival.clear()
        c.log.add("demo: reset")
    }
}
