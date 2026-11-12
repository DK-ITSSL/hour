package com.hour.app.debug

import com.hour.app.di.AppContainer

/** 시연 준비 동작(디버그 빌드 전용). 진단 화면 버튼과 adb 인텐트가 함께 쓴다. */
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

    suspend fun reset(c: AppContainer) {
        c.journeys.resetAll()
        c.prefs.clearEquipped()
        c.arrival.clear()
        c.log.add("demo: reset")
    }
}
