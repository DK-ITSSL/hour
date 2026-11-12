package com.hour.app.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hour.app.HourApp

/** 부팅·사용자 잠금 해제 시 기록 서비스를 IDLE로 띄운다(최선 노력). D1 검증 항목 2. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HourApp
        val stamp = "${intent.action?.substringAfterLast('.')} @ ${System.currentTimeMillis()}"
        app.container.log.remember("lastBoot", stamp)
        app.container.log.add("boot: $stamp")
        runCatching { JourneyRecorderService.start(context) }
            .onSuccess { app.container.log.add("boot: recorder start requested") }
            .onFailure { app.container.log.add("boot: recorder start FAILED $it") }
    }
}
