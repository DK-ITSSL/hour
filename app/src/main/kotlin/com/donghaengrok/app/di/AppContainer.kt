package com.donghaengrok.app.di

import android.content.Context
import com.donghaengrok.app.diag.LogStore
import com.donghaengrok.car.CarGatewayFactory
import com.donghaengrok.car.CarGateways
import kotlinx.coroutines.flow.MutableStateFlow

/** 수동 의존성 주입 컨테이너. 앱 프로세스당 1개. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val car: CarGateways = CarGatewayFactory.create(appContext)
    val log = LogStore(appContext)
    val recorderRunning = MutableStateFlow(false)
}
