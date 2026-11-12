package com.hour.app

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    /** 사용자가 허용해야 하는 권한(위험 권한) 중 아직 허용되지 않은 것. 일반·시스템 권한은 제외한다. */
    private fun missingRuntimePermissions(): List<String> {
        val declared = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        return declared.filter { p ->
            val dangerous = runCatching { packageManager.getPermissionInfo(p, 0).protection == PermissionInfo.PROTECTION_DANGEROUS }.getOrDefault(false)
            dangerous && ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun refreshPermissions() {
        permissionsMissing.value = missingRuntimePermissions().isNotEmpty()
    }

    private fun requestMissingPermissions() {
        val missing = missingRuntimePermissions()
        container.log.add("permission request: ${missing.map { it.substringAfterLast('.') }}")
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    companion object {
        const val ACTION_OPEN_ARRIVAL = "com.hour.app.action.OPEN_ARRIVAL"
        const val ACTION_OPEN_GIFT = "com.hour.app.action.OPEN_GIFT"
        const val DEBUG_SEED = "com.hour.app.debug.SEED"
        const val DEBUG_PREVIEW_ARRIVAL = "com.hour.app.debug.PREVIEW_ARRIVAL"
        const val DEBUG_RESET = "com.hour.app.debug.RESET"
    }
}
