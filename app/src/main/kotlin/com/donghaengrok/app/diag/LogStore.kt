package com.donghaengrok.app.diag

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 진단용 순환 로그. 화면 표시와 logcat(태그 DHR) 출력을 함께 한다. */
class LogStore(context: Context) {
    private val prefs = context.getSharedPreferences("diag", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Synchronized
    fun add(message: String) {
        val line = "${fmt.format(Date())}  $message"
        Log.i("DHR", message)
        _lines.value = (listOf(line) + _lines.value).take(MAX)
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun remember(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun recall(key: String): String? = prefs.getString(key, null)

    private companion object { const val MAX = 400 }
}
