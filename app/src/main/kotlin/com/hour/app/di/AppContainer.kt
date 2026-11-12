package com.hour.app.di

import android.content.Context
import com.hour.app.arrival.ArrivalFlow
import com.hour.app.arrival.ArrivalNotifier
import com.hour.app.data.AppDatabase
import com.hour.app.data.JourneyRepository
import com.hour.app.data.Prefs
import com.hour.app.diag.LogStore
import com.hour.car.CarGatewayFactory
import com.hour.car.CarGateways
import com.hour.domain.journey.JourneySummary
import com.hour.domain.signal.MomentSource
import com.hour.domain.signal.SignalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 수동 의존성 주입 컨테이너. 앱 프로세스당 1개. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val car: CarGateways = CarGatewayFactory.create(appContext)
    val log = LogStore(appContext)
    val db: AppDatabase = AppDatabase.create(appContext)
    val journeys = JourneyRepository(db.journeys()) { log.add(it) }
    val prefs = Prefs(appContext)
    val notifier = ArrivalNotifier(appContext)
    val arrival = ArrivalFlow(scope, journeys, notifier, car.voice, prefs, log)

    val recorderRunning = MutableStateFlow(false)
    /** 진행 중인 여정의 최신 요약(주행 중 미니 화면·진단 화면용). */
    val currentJourney = MutableStateFlow<JourneySummary?>(null)

    private val _listening = MutableStateFlow(false)
    /** "이 순간 기억" 버튼으로 STT를 듣는 중인지. */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    private val _lastMoment = MutableStateFlow<String?>(null)
    val lastMoment: StateFlow<String?> = _lastMoment.asStateFlow()

    init {
        // STT 최종 결과를 순간 기록으로 넣는다(버튼을 눌렀을 때만).
        scope.launch {
            car.voice.transcripts.collect { text ->
                if (!_listening.value) return@collect
                _listening.value = false
                val note = text.take(200)
                car.signals.inject(SignalEvent.Moment(System.currentTimeMillis(), note, MomentSource.STT))
                _lastMoment.value = note
                log.add("moment(stt): $note")
                if (prefs.narration.value) car.voice.speak("기억했어요.")
            }
        }
    }

    fun captureMoment() {
        _listening.value = true
        car.voice.listenOnce()
        scope.launch {
            kotlinx.coroutines.delay(12_000)
            _listening.value = false
        }
    }
}
