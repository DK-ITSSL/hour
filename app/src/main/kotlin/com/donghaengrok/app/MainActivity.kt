package com.donghaengrok.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.donghaengrok.app.gleo.GleoActionReceiver
import com.donghaengrok.app.gleo.GleoResponder
import com.donghaengrok.app.ui.ProbeScreen

class MainActivity : ComponentActivity() {
    private val container get() = (application as DonghaengApp).container

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result.forEach { (perm, ok) -> container.log.add("permission ${perm.substringAfterLast('.')} -> ${if (ok) "granted" else "denied"}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.car.ux.start()
        handleGleo(intent)
        setContent {
            // 최상위 화면 Back은 종료하지 않고 뒤로 숨긴다(Pleos 필수 정책).
            BackHandler {
                val moved = moveTaskToBack(true)
                container.log.add("back: moveTaskToBack=$moved")
            }
            ProbeScreen(
                container = container,
                onRequestPermissions = ::requestMissingPermissions,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

    private fun handleGleo(intent: Intent?) {
        if (intent?.action != GleoActionReceiver.ACTION_SHOW_TODAY) return
        val driving = container.car.ux.state.value.requiresDistractionOptimization
        container.log.add("gleo: SHOW_TODAY_JOURNEY (driving=$driving)")
        if (driving) {
            GleoResponder.respond(this, GleoActionReceiver.ACTION_SHOW_TODAY, "fail", "You can view it after parking.")
        } else {
            GleoResponder.respond(this, GleoActionReceiver.ACTION_SHOW_TODAY, "success", "Here is today's journey.")
        }
    }

    /** manifest에 선언한 권한 중 아직 허용되지 않은 것을 모두 요청한다(일반 권한은 무시된다). */
    private fun requestMissingPermissions() {
        val declared = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        val missing = declared.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        container.log.add("permission request: ${missing.map { it.substringAfterLast('.') }}")
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
