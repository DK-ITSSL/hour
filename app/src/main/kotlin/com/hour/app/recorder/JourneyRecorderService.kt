package com.hour.app.recorder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hour.app.HourApp
import com.hour.app.R
import com.hour.app.data.JourneyRepository
import com.hour.app.di.Position
import com.hour.domain.engine.EngineOutput
import com.hour.domain.engine.JourneyEngine
import com.hour.domain.journey.Anchor
import com.hour.domain.journey.GeoPoint
import com.hour.domain.signal.SignalEvent
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 여정 기록 포그라운드 서비스(specialUse).
 * 차량 신호 → JourneyEngine(단일 스레드) → 저장소. 30초마다 시간 경과(P단 타이머)와 체크포인트를 처리한다.
 */
class JourneyRecorderService : LifecycleService() {
    private val container get() = (application as HourApp).container
    private val engine = JourneyEngine()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loop = Dispatchers.Default.limitedParallelism(1)
    private var lastCheckpointAt = 0L
    private var lastPublishAt = 0L

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(getString(R.string.notif_recording_idle)), type) }
            .onSuccess { container.log.add("service: startForeground(specialUse) ok") }
            .onFailure { container.log.add("service: startForeground FAILED $it") }

        container.recorderRunning.value = true
        container.car.ux.start()

        lifecycleScope.launch(loop) {
            restore()
            launch {
                // 설정에서 바꾼 집·회사를 엔진에 반영한다(엔진은 이 단일 스레드에서만 만진다).
                container.anchorChanges.collect { (type, anchor) ->
                    if (anchor != null) engine.setAnchor(anchor) else engine.clearAnchor(type)
                }
            }
            launch {
                // 위치·차속은 권한 없이 등록하면 실패한 채로 남는다. 허용된 권한이 바뀌면 구독을 끊고 처음부터 다시 한다.
                container.permissions.granted.collectLatest { granted ->
                    container.log.add("service: subscribing signals (${container.car.flavor}, 허용된 권한 ${granted.size}개)")
                    // 처리 중이던 신호는 끝까지 처리한 뒤에 끊는다(엔진과 저장소가 어긋나지 않게).
                    container.car.signals.signals().collect { e -> withContext(NonCancellable) { onSignal(e) } }
                }
            }
            while (isActive) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                handle(engine.onTick(now))
                publish(now, force = true)
                checkpoint(now, force = false)
                // 화면 밖(설정 앱 등)에서 허용한 권한도 알아챈다.
                container.permissions.refresh()
            }
        }
    }

    private suspend fun onSignal(e: SignalEvent) {
        when (e) {
            is SignalEvent.Location ->
                container.lastPosition.value = Position(e.lat, e.lon, gps = true, address = container.lastPosition.value?.address)
            is SignalEvent.NaviAddress ->
                container.lastPosition.value = container.lastPosition.value?.takeIf { it.gps }?.copy(address = e.address)
                    ?: Position(e.lat, e.lon, gps = false, address = e.address)
            is SignalEvent.NaviAnchor -> {
                // 앱에서 직접 지정한 집·회사는 내비 즐겨찾기로 덮어쓰지 않는다.
                if (container.journeys.anchorSource(e.type) == JourneyRepository.SOURCE_MANUAL) return
                container.journeys.saveAnchor(Anchor(e.type, e.name, GeoPoint(e.lat, e.lon)), JourneyRepository.SOURCE_NAVI)
            }
            else -> Unit
        }
        if (e is SignalEvent.Moment) container.log.add("moment: ${e.text} (${e.source})")
        handle(engine.onEvent(e))
        publish(e.at, force = false)
    }

    private suspend fun restore() {
        val anchors = container.journeys.anchors()
        anchors.forEach(engine::setAnchor)
        val cp = container.journeys.loadCheckpoint() ?: return
        val out = engine.restore(cp, System.currentTimeMillis())
        // 체크포인트의 집·회사는 오래됐을 수 있으니 저장소 값을 다시 덮는다.
        anchors.forEach(engine::setAnchor)
        container.log.add("service: restored checkpoint (state=${engine.state}, journey=${engine.activeJourneyId})")
        handle(out)
    }

    private suspend fun handle(outputs: List<EngineOutput>) {
        for (o in outputs) when (o) {
            is EngineOutput.Started -> {
                container.log.add("journey: started ${o.id.take(8)}")
                engine.current()?.let { container.journeys.saveProgress(it) }
                checkpoint(o.at, force = true)
            }
            is EngineOutput.Discarded -> {
                container.log.add("journey: discarded ${o.id.take(8)} (300m 미만)")
                container.journeys.discard(o.id)
                container.currentJourney.value = null
                container.journeys.clearCheckpoint()
            }
            is EngineOutput.Progress -> {
                container.journeys.saveProgress(o.summary)
                container.currentJourney.value = o.summary
                checkpoint(System.currentTimeMillis(), force = true)
            }
            is EngineOutput.GiftReady -> {
                container.journeys.saveProgress(o.summary)
                container.arrival.onGiftReady(o.summary)
                container.log.add("journey: gift ready — ${o.summary.title} · ${km(o.summary.distanceKm)} · 추천 ${o.summary.suggestion?.category?.korean}(${o.summary.suggestion?.reason})")
            }
            is EngineOutput.Finished -> {
                val unlocked = container.journeys.finish(o.summary)
                container.arrival.onFinished(o.summary.id, unlocked)
                container.currentJourney.value = null
                container.journeys.clearCheckpoint()
                container.log.add("journey: finished — ${o.summary.title} · ${km(o.summary.distanceKm)} · 해금 ${unlocked.map { it.name }}")
            }
            is EngineOutput.OrphanMoment -> {
                val id = container.journeys.attachMoment(o.moment)
                container.log.add("moment: attached to ${id?.take(8) ?: "(여정 없음)"}")
            }
        }
    }

    private fun publish(now: Long, force: Boolean) {
        if (!force && now - lastPublishAt < PUBLISH_MS) return
        lastPublishAt = now
        val cur = engine.current(now)
        container.currentJourney.value = cur
        val text = cur?.let { "기록 중 · ${it.drivingMinutes}분 · ${km(it.distanceKm)}" } ?: getString(R.string.notif_recording_idle)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private suspend fun checkpoint(now: Long, force: Boolean) {
        if (engine.activeJourneyId == null) return
        if (!force && now - lastCheckpointAt < CHECKPOINT_MS) return
        lastCheckpointAt = now
        container.journeys.saveCheckpoint(engine.checkpoint(now))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, HourApp.CHANNEL_RECORDING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_FINALIZE_NOW && com.hour.app.BuildConfig.DEBUG) {
            lifecycleScope.launch(loop) {
                container.log.add("debug: finalize now")
                handle(engine.finalizeNow(System.currentTimeMillis()))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val now = System.currentTimeMillis()
        if (engine.activeJourneyId != null) {
            // 서비스가 내려가기 전에 마지막 상태를 남긴다(복구용).
            kotlinx.coroutines.runBlocking { container.journeys.saveCheckpoint(engine.checkpoint(now)) }
        }
        container.car.ux.stop()
        container.recorderRunning.value = false
        container.log.add("service: destroyed")
        super.onDestroy()
    }

    private fun km(v: Double) = if (v < 10) "${(v * 10).roundToInt() / 10.0}km" else "${v.roundToInt()}km"

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 30_000L
        private const val PUBLISH_MS = 2_000L
        private const val CHECKPOINT_MS = 30_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, JourneyRecorderService::class.java))
        }

        const val ACTION_FINALIZE_NOW = "com.hour.app.action.FINALIZE_NOW"

        fun finalizeNow(context: Context) {
            context.startService(Intent(context, JourneyRecorderService::class.java).setAction(ACTION_FINALIZE_NOW))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JourneyRecorderService::class.java))
        }
    }
}
