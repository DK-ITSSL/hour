package com.donghaengrok.car

import com.donghaengrok.domain.signal.SignalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 앱이 차량·음성 기능을 쓰는 유일한 창구. pleos/sim 빌드가 각각 구현을 제공한다. */
class CarGateways(
    val signals: CarSignalSource,
    val ux: UxRestrictionSource,
    val voice: VoiceIO,
    val flavor: String,
)

interface CarSignalSource {
    /** 지원되는 신호를 모두 구독한다. 수집을 멈추면 모든 리스너를 해제한다. */
    fun signals(): Flow<SignalEvent>

    /** 권한·capability·서비스 연결 상태를 점검한다(D1 검증용, 이후 SenseAvailability 산출에 사용). */
    suspend fun probe(): List<ProbeItem>

    /** 시연·디버그용으로 신호를 직접 넣는다. pleos 빌드에서는 Moment 같은 앱 내부 이벤트만 의미가 있다. */
    fun inject(event: SignalEvent)
}

data class ProbeItem(val name: String, val status: ProbeStatus, val detail: String = "")

enum class ProbeStatus { OK, UNSUPPORTED, FAILED, INFO }

interface UxRestrictionSource {
    val state: StateFlow<UxState>
    fun start()
    fun stop()
}

data class UxState(
    val requiresDistractionOptimization: Boolean = false,
    val activeRestrictions: Int = 0,
    val source: String = "unknown",
)

interface VoiceIO {
    val events: Flow<String>
    fun speak(text: String)
    fun listenOnce()
    fun release()
}
