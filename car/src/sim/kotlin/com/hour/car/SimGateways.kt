package com.hour.car

import android.content.Context
import android.util.Log
import com.hour.domain.signal.SignalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** sim 빌드: 디버그 패널이나 시나리오 재생이 [inject]로 신호를 넣는다. */
class FakeCarSignalSource : CarSignalSource {
    private val bus = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 256)
    override fun signals(): Flow<SignalEvent> = bus
    override suspend fun probe(): List<ProbeItem> =
        listOf(ProbeItem("sim build", ProbeStatus.INFO, "가짜 신호 빌드입니다. 디버그 패널로 신호를 넣으세요."))
    override fun inject(event: SignalEvent) {
        bus.tryEmit(event)
    }
}

class FakeUxRestrictionSource : UxRestrictionSource {
    private val _state = MutableStateFlow(UxState(source = "sim"))
    override val state: StateFlow<UxState> = _state.asStateFlow()
    override fun start() {}
    override fun stop() {}
    fun setDriving(driving: Boolean) {
        _state.value = UxState(requiresDistractionOptimization = driving, activeRestrictions = if (driving) 0xFF else 0, source = "sim")
    }
}

class FakeVoice : VoiceIO {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val events: Flow<String> = _events
    override fun speak(text: String) {
        Log.i("HOUR.voice", "sim speak: $text")
        _events.tryEmit("TTS(sim): $text")
    }
    override fun listenOnce() {
        _events.tryEmit("STT(sim): 음성 입력은 sim 빌드에서 동작하지 않습니다")
    }
    override fun release() {}
}

object CarGatewayFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): CarGateways =
        CarGateways(FakeCarSignalSource(), FakeUxRestrictionSource(), FakeVoice(), flavor = "sim")
}
