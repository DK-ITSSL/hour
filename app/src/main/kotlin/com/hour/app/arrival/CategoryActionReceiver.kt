package com.hour.app.arrival

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hour.app.HourApp
import com.hour.domain.journey.CategorySource
import com.hour.domain.journey.TripCategory
import kotlinx.coroutines.launch

/** 도착 알림의 운행타입 버튼. 앱 내부 전용(exported=false). */
class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ArrivalNotifier.EXTRA_JOURNEY_ID) ?: return
        val category = intent.getStringExtra(ArrivalNotifier.EXTRA_CATEGORY)
            ?.let { runCatching { TripCategory.valueOf(it) }.getOrNull() } ?: return
        val container = (context.applicationContext as HourApp).container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.arrival.confirm(id, category, CategorySource.USER)
            } finally {
                pending.finish()
            }
        }
    }
}
