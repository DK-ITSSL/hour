package com.hour.car

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** Pleos TTS·STT를 기기 내(ON_DEVICE) 모드로 쓴다. 처음 사용할 때 초기화한다. */
class PleosVoice(private val context: Context) : VoiceIO {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val events: Flow<String> = _events
    private val _transcripts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val transcripts: Flow<String> = _transcripts

    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    @Volatile private var ttsReady = false
    @Volatile private var sttReady = false
    private var pendingSpeech: String? = null
    private var pendingListen = false

    private fun log(msg: String) {
        Log.i(TAG, msg)
        _events.tryEmit(msg)
    }

    @Synchronized
    private fun ensureTts(): TextToSpeech = tts ?: TextToSpeech(context, TtsMode.ON_DEVICE).also { engine ->
        tts = engine
        // TTS는 initialize가 끝난 뒤에 리스너를 붙여야 한다(먼저 붙이면 SDK 내부 lateinit 예외, 에뮬레이터 확인).
        engine.initialize(object : TtsInit {
            override fun onSuccess() {
                engine.addEventListener(object : EventListener {
                    override fun onStart() = log("tts onStart")
                    override fun onReady() = log("tts onReady")
                    override fun onDone() = log("tts onDone")
                    override fun onError(errorType: TtsError) = log("tts onError $errorType")
                    override fun onStop() = log("tts onStop")
                    override fun onUpdatedRms(rms: Double) {}
                })
                ttsReady = true
                engine.setLocale(Locale.KOREA)
                log("tts init ok")
                pendingSpeech?.let { pendingSpeech = null; engine.speak(it, true) }
            }
            override fun onFailure(errorType: TtsError) = log("tts init fail $errorType")
        })
    }

    @Synchronized
    private fun ensureStt(): SpeechToText = stt ?: SpeechToText(context, SttMode.ON_DEVICE).also { engine ->
        stt = engine
        engine.addListener(object : ResultListener {
            override fun onUpdated(stt: String, completed: Boolean) {
                log(if (completed) "stt final: $stt" else "stt partial: $stt")
                if (completed && stt.isNotBlank()) _transcripts.tryEmit(stt.trim())
            }
            override fun onUpdatedEpdData(on: Long, off: Long) {}
            override fun onStartedRecognition() = log("stt started")
            override fun onEndedRecognition() = log("stt ended")
            override fun onError(errorType: SttError) = log("stt onError $errorType")
            override fun onReady() = log("stt ready")
            override fun onUpdatedRms(rms: Double) {}
        })
        engine.initialize(object : SttInit {
            override fun onSuccess() {
                sttReady = true
                engine.setLocale(Locale.KOREA)
                log("stt init ok")
                if (pendingListen) { pendingListen = false; engine.request() }
            }
            override fun onFailure(errorType: SttError) = log("stt init fail $errorType")
        })
    }

    // SDK 초기화·호출은 메인 스레드에서 한다(기록 서비스·알림 수신부는 백그라운드 스레드에서 부른다).
    private val main = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    override fun speak(text: String) = onMain {
        runCatching {
            val engine = ensureTts()
            if (ttsReady) engine.speak(text, true) else pendingSpeech = text
        }.onFailure { log("tts unavailable: $it") }
    }

    override fun listenOnce() = onMain {
        runCatching {
            val engine = ensureStt()
            if (sttReady) engine.request() else pendingListen = true
        }.onFailure { log("stt unavailable: $it") }
    }

    override fun release() {
        runCatching { tts?.stop(); tts?.release() }
        runCatching { stt?.stop(); stt?.release() }
        tts = null
        stt = null
        ttsReady = false
        sttReady = false
    }

    private companion object { const val TAG = "HOUR.voice" }
}
