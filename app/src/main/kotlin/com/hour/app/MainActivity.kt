package com.hour.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.hour.app.arrival.ArrivalNotifier
import com.hour.app.debug.DemoActions
import com.hour.app.gleo.GleoActionReceiver
import com.hour.app.gleo.GleoResponder
import com.hour.app.recorder.JourneyRecorderService
import com.hour.app.ui.AppRoot
import com.hour.app.ui.OpenRequest
import com.hour.app.ui.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as HourApp).container
    private val permissionsMissing = MutableStateFlow(false)
    private val openRequest = MutableStateFlow<OpenRequest?>(null)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result.forEach { (perm, ok) -> container.log.add("permission ${perm.substringAfterLast('.')} -> ${if (ok) "granted" else "denied"}") }
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.car.ux.start()
        refreshPermissions()
        // 앱을 열면 기록 서비스도 켠다(평소에는 부팅 때 자동으로 켜진다).
        if (!container.recorderRunning.value) JourneyRecorderService.start(this)
        handleIntent(intent)
        setContent {
            AppRoot(
                container = container,
                screen = ScreenState(
                    permissionsMissing = permissionsMissing,
                    openRequest = openRequest,
                    consumeOpen = { openRequest.value = null },
                    requestPermissions = ::requestMissingPermissions,
                    // 최상위 화면 Back은 종료하지 않고 뒤로 숨긴다(Pleos 필수 정책).
                    backAtRoot = {
                        val moved = moveTaskToBack(true)
                        container.log.add("back: moveTaskToBack=$moved")
                    },
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            GleoActionReceiver.ACTION_SHOW_TODAY -> {
                val driving = container.car.ux.state.value.requiresDistractionOptimization
                container.log.add("gleo: SHOW_TODAY_JOURNEY (driving=$driving)")
                if (driving) {
                    GleoResponder.respond(this, GleoActionReceiver.ACTION_SHOW_TODAY, "fail", "You can view it after parking.")
                } else {
                    openRequest.value = OpenRequest.Today
                    GleoResponder.respond(this, GleoActionReceiver.ACTION_SHOW_TODAY, "success", "Here is today's journey.")
                }
            }
            ACTION_OPEN_ARRIVAL -> intent.getStringExtra(ArrivalNotifier.EXTRA_JOURNEY_ID)?.let { openRequest.value = OpenRequest.Arrival(it) }
            ACTION_OPEN_GIFT -> openRequest.value = OpenRequest.Gift
            DEBUG_ROUTE -> if (BuildConfig.DEBUG) {
                val ok = container.car.signals.requestRoute(
                    intent.getStringExtra("name") ?: "성남시청",
                    intent.getStringExtra("address") ?: "경기 성남시 중원구 성남대로 997",
                    intent.getStringExtra("lat")?.toDoubleOrNull() ?: 37.4201,
                    intent.getStringExtra("lon")?.toDoubleOrNull() ?: 127.1265,
                )
                container.log.add("debug: requestRoute -> $ok")
            }
            DEBUG_SEED, DEBUG_PREVIEW_ARRIVAL, DEBUG_RESET -> if (BuildConfig.DEBUG) {
                lifecycleScope.launch {
                    when (intent.action) {
                        DEBUG_SEED -> DemoActions.seed(container)
                        DEBUG_PREVIEW_ARRIVAL -> DemoActions.previewArrival(container)
                        DEBUG_RESET -> DemoActions.reset(container)
                    }
                }
            }
        }
    }

    /** 권한 상태를 다시 읽는다. 새로 허용된 권한이 있으면 기록 서비스가 차량 신호를 다시 구독한다. */
    private fun refreshPermissions() {
        permissionsMissing.value = container.permissions.refresh().isNotEmpty()
    }

    private fun requestMissingPermissions() {
        val missing = container.permissions.missing()
        container.log.add("permission request: ${missing.map { it.substringAfterLast('.') }}")
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    companion object {
        const val ACTION_OPEN_ARRIVAL = "com.hour.app.action.OPEN_ARRIVAL"
        const val ACTION_OPEN_GIFT = "com.hour.app.action.OPEN_GIFT"
        const val DEBUG_SEED = "com.hour.app.debug.SEED"
        const val DEBUG_PREVIEW_ARRIVAL = "com.hour.app.debug.PREVIEW_ARRIVAL"
        const val DEBUG_RESET = "com.hour.app.debug.RESET"
        const val DEBUG_ROUTE = "com.hour.app.debug.ROUTE"
    }
}
