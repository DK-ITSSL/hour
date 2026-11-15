package com.hour.app.di

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 사용자가 허용해야 하는 권한(위험 권한)의 허용 상태.
 * 위치·차속은 권한 없이 등록하면 실패한 채로 남으므로, 기록 서비스가 [granted]를 보고 허용된 뒤에 다시 구독한다.
 */
class RuntimePermissions(private val context: Context) {
    /** 매니페스트에 선언한 권한 중 위험 권한만. 일반·시스템 권한은 제외한다. */
    private val runtime: List<String> by lazy {
        val pm = context.packageManager
        val declared = runCatching {
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        }.getOrNull().orEmpty()
        declared.filter { p ->
            runCatching { pm.getPermissionInfo(p, 0).protection == PermissionInfo.PROTECTION_DANGEROUS }.getOrDefault(false)
        }
    }

    private val _granted = MutableStateFlow(read())
    val granted: StateFlow<Set<String>> = _granted.asStateFlow()

    /** 아직 허용되지 않은 권한. */
    fun missing(): List<String> = runtime.filterNot(::isGranted)

    /** 허용 상태를 다시 읽는다. 달라졌으면 [granted]가 바뀐다. */
    fun refresh(): List<String> {
        _granted.value = read()
        return missing()
    }

    private fun read(): Set<String> = runtime.filter(::isGranted).toSet()

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
