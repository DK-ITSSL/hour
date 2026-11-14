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
import com.hour.domain.journey.Anchor
import com.hour.domain.journey.GeoPoint
import com.hour.domain.journey.JourneySummary
import com.hour.domain.signal.AnchorType
import com.hour.domain.signal.MomentSource
import com.hour.domain.signal.SignalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 마지막으로 받은 위치. GPS가 들어오면 GPS를 우선한다(거리 판정과 같은 기준). */
data class Position(val lat: Double, val lon: Double, val gps: Boolean, val address: String?)

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

    /** 기록 서비스가 갱신하는 현재 위치(설정의 "지금 위치를 집으로"에 쓴다). */
    val lastPosition = MutableStateFlow<Position?>(null)

    private val _anchorChanges = MutableSharedFlow<Pair<AnchorType, Anchor?>>(extraBufferCapacity = 8)
    /** 집·회사 변경(null이면 지움). 기록 서비스가 엔진에 반영한다. */
    val anchorChanges: SharedFlow<Pair<AnchorType, Anchor?>> = _anchorChanges

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

    /** 지금 위치를 집·회사로 지정한다. 위치를 아직 못 받았으면 false. */
    suspend fun setAnchorHere(type: AnchorType): Boolean {
        val p = lastPosition.value ?: return false
        val anchor = Anchor(type, if (type == AnchorType.HOME) "집" else "회사", GeoPoint(p.lat, p.lon))
        journeys.saveAnchor(anchor, JourneyRepository.SOURCE_MANUAL)
        _anchorChanges.emit(type to anchor)
        log.add("anchor: ${type.name} ← 지금 위치(${if (p.gps) "GPS" else "내비"}) ${p.address ?: ""}")
        return true
    }

    suspend fun clearAnchor(type: AnchorType) {
        journeys.deleteAnchor(type)
        _anchorChanges.emit(type to null)
        log.add("anchor: ${type.name} 지움")
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
