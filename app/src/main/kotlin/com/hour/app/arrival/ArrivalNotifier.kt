package com.hour.app.arrival

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hour.app.MainActivity
import com.hour.app.R
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.TripCategory
import com.hour.domain.unlock.UnlockRule

/**
 * 도착 알림. 화면이 꺼져 있거나 다른 앱을 보고 있어도 운행타입을 고를 수 있게 한다.
 * - 도착: "이번 운행은 어떤 길이었나요?" + 운행타입 버튼(추천이 첫 번째)
 * - 선물: 해금된 애셋 이름. 누르면 앱이 열리고 도착 선물 화면이 뜬다.
 */
class ArrivalNotifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ARRIVAL, context.getString(R.string.notif_channel_arrival), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun arrival(s: JourneySummary) {
        val suggestion = s.suggestion
        val order = buildList {
            suggestion?.let { add(it.category) }
            TripCategory.entries.filter { it != suggestion?.category }.forEach(::add)
        }
        val builder = base("도착했어요 · 이번 운행은 어떤 길이었나요?")
            .setContentText(suggestion?.let { "추천: ${it.category.korean} — ${it.reason}" } ?: s.title)
            .setContentIntent(openApp(MainActivity.ACTION_OPEN_ARRIVAL, s.id, REQ_OPEN_ARRIVAL))
        order.take(3).forEachIndexed { i, c ->
            val label = if (i == 0 && suggestion != null) "${c.korean} (추천)" else c.korean
            builder.addAction(0, label, categoryAction(s.id, c, REQ_CATEGORY + c.ordinal))
        }
        notify(ID_ARRIVAL, builder)
    }

    fun gift(j: JourneySummary, unlocked: List<UnlockRule>) {
        val text = if (unlocked.isEmpty()) j.title else unlocked.joinToString(", ") { it.name }
        val builder = base(if (unlocked.isEmpty()) "이번 여정을 기록했어요" else "도착 선물이 왔어요")
            .setContentText(text)
            .setContentIntent(openApp(MainActivity.ACTION_OPEN_GIFT, j.id, REQ_OPEN_GIFT))
        notify(ID_GIFT, builder)
    }

    fun cancelArrival() = nm.cancel(ID_ARRIVAL)
    fun cancelGift() = nm.cancel(ID_GIFT)

    private fun base(title: String) = NotificationCompat.Builder(context, CHANNEL_ARRIVAL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        .setAutoCancel(true)

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        // 알림 권한이 없으면 조용히 넘어간다(화면·Gleo·음성으로도 고를 수 있다).
        runCatching { nm.notify(id, builder.build()) }
    }

    private fun openApp(action: String, journeyId: String, req: Int): PendingIntent = PendingIntent.getActivity(
        context, req,
        Intent(context, MainActivity::class.java).setAction(action).putExtra(EXTRA_JOURNEY_ID, journeyId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun categoryAction(journeyId: String, c: TripCategory, req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req,
        Intent(context, CategoryActionReceiver::class.java)
            .putExtra(EXTRA_JOURNEY_ID, journeyId)
            .putExtra(EXTRA_CATEGORY, c.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ARRIVAL = "arrival"
        const val EXTRA_JOURNEY_ID = "journeyId"
        const val EXTRA_CATEGORY = "category"
        private const val ID_ARRIVAL = 2001
        private const val ID_GIFT = 2002
        private const val REQ_OPEN_ARRIVAL = 10
        private const val REQ_OPEN_GIFT = 11
        private const val REQ_CATEGORY = 20
    }
}
