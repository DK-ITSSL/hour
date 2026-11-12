package com.hour.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.hour.app.di.AppContainer

class HourApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, getString(R.string.notif_channel_recording), NotificationManager.IMPORTANCE_LOW),
        )
        container.notifier.createChannel()
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
    }
}
