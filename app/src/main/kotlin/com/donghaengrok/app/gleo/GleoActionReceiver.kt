package com.donghaengrok.app.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.donghaengrok.app.DonghaengApp
import com.donghaengrok.domain.signal.MomentSource
import com.donghaengrok.domain.signal.SignalEvent
import com.donghaengrok.domain.journey.CategorySource
import com.donghaengrok.domain.journey.TripCategory
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Gleo Interface component=service 함수 수신부. 7초 안에 APP_ACTION_RESPONSE로 답한다. */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as DonghaengApp
        val params = GleoParams.parse(intent)
        app.container.log.add("gleo: $action params=$params")
        when (action) {
            ACTION_SAVE_MOMENT -> {
                val note = params["note"]?.trim().orEmpty().take(MAX_NOTE)
                if (note.isEmpty()) {
                    GleoResponder.respond(context, action, "asking", "What would you like me to remember about this moment?")
                } else {
                    app.container.car.signals.inject(SignalEvent.Moment(System.currentTimeMillis(), note, MomentSource.GLEO))
                    GleoResponder.respond(context, action, "success", "I saved this moment to your journey.")
                }
            }
            ACTION_SET_CATEGORY -> {
                val category = TripCategory.fromGleo(params["category"])
                if (category == null) {
                    GleoResponder.respond(context, action, "asking", "Was this drive a commute, a trip, a date, or a daily drive?")
                    return
                }
                // DB 작업이 끝난 뒤 응답한다(Gleo 제한 7초 안).
                val pending = goAsync()
                app.container.scope.launch {
                    try {
                        val id = app.container.journeys.latestJourneyId()
                        if (id == null) {
                            GleoResponder.respond(context, action, "fail", "There is no recorded drive yet.")
                        } else {
                            val unlocked = app.container.journeys.confirmCategory(id, category, CategorySource.GLEO)
                            app.container.log.add("gleo: trip category -> ${category.korean}, 해금 ${unlocked.map { it.name }}")
                            GleoResponder.respond(context, action, "success", "I recorded this drive as a ${category.gleoName}.")
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SAVE_MOMENT = "SAVE_JOURNEY_MOMENT"
        const val ACTION_SET_CATEGORY = "SET_TRIP_CATEGORY"
        const val ACTION_SHOW_TODAY = "SHOW_TODAY_JOURNEY"
        private const val MAX_NOTE = 200
    }
}

object GleoParams {
    /**
     * Gleo는 actionParameters를 ACTION_PARAM(JSON 문자열)과 개별 extra 두 방식으로 넘길 수 있다(문서 예제가 섞여 있음).
     * 따옴표로 한 번 더 감싼 문자열, actionParameters 래퍼까지 모두 받아들인다.
     */
    fun parse(intent: Intent): Map<String, String> {
        val out = linkedMapOf<String, String>()
        intent.extras?.keySet()?.forEach { key ->
            if (key != "ACTION_PARAM") intent.extras?.get(key)?.let { out[key] = it.toString() }
        }
        val raw = intent.getStringExtra("ACTION_PARAM") ?: return out
        val json = runCatching { toJson(raw) }.getOrNull() ?: return out
        val obj = json.optJSONObject("actionParameters") ?: json
        obj.keys().forEach { k -> out[k] = obj.opt(k)?.toString().orEmpty() }
        return out
    }

    private fun toJson(raw: String): JSONObject {
        var s = raw.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1).replace("\\\"", "\"")
        }
        return JSONObject(s)
    }
}

object GleoResponder {
    private const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    fun respond(context: Context, functionName: String, status: String, message: String, mode: String = "voice") {
        val response = JSONObject()
            .put("functionName", functionName)
            .put("status", status)
            .put("message", message)
            .put("mode", mode)
        context.sendBroadcast(
            Intent(RESPONSE_ACTION)
                .putExtra("packageName", context.packageName)
                .putExtra("response", response.toString()),
        )
        (context.applicationContext as DonghaengApp).container.log.add("gleo: responded $response")
    }
}
