package com.donghaengrok.app.di

import android.content.Context
import com.donghaengrok.app.data.AppDatabase
import com.donghaengrok.app.data.JourneyRepository
import com.donghaengrok.app.diag.LogStore
import com.donghaengrok.car.CarGatewayFactory
import com.donghaengrok.car.CarGateways
import com.donghaengrok.domain.journey.JourneySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** 수동 의존성 주입 컨테이너. 앱 프로세스당 1개. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val car: CarGateways = CarGatewayFactory.create(appContext)
    val log = LogStore(appContext)
    val db: AppDatabase = AppDatabase.create(appContext)
    val journeys = JourneyRepository(db.journeys()) { log.add(it) }

    val recorderRunning = MutableStateFlow(false)
    /** 진행 중인 여정의 최신 요약(주행 중 미니 화면·진단 화면용). */
    val currentJourney = MutableStateFlow<JourneySummary?>(null)
    /** 도착 선물이 준비된 여정(D3 화면에서 운행타입 확인 → 선물 표시). */
    val giftReady = MutableStateFlow<JourneySummary?>(null)
}
