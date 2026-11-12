package com.donghaengrok.app.recorder

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.donghaengrok.app.DonghaengApp
import com.donghaengrok.app.R
import com.donghaengrok.domain.signal.SignalEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 여정 기록 포그라운드 서비스(specialUse).
 * D1: 모든 신호를 구독해 진단 로그로 남긴다. D2부터 도메인 엔진(상태 머신·감각 누적)으로 흘려보낸다.
 */
class JourneyRecorderService : LifecycleService() {
    private val container get() = (application as DonghaengApp).container
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val notification = NotificationCompat.Builder(this, DonghaengApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_recording_idle))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onSuccess { container.log.add("service: startForeground(specialUse) ok") }
            .onFailure { container.log.add("service: startForeground FAILED $it") }

        container.recorderRunning.value = true
        container.car.ux.start()
        collectJob = lifecycleScope.launch {
            container.log.add("service: subscribing signals (${container.car.flavor})")
            container.car.signals.signals().collect { event -> container.log.add(describe(event)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        container.car.ux.stop()
        container.recorderRunning.value = false
        container.log.add("service: destroyed")
        super.onDestroy()
    }

    private fun describe(e: SignalEvent): String =
        e.toString().replace(Regex("""at=\d+, ?"""), "").replace("SignalEvent.", "")

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, JourneyRecorderService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JourneyRecorderService::class.java))
        }
    }
}
