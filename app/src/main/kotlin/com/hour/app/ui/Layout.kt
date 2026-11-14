package com.hour.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 좁은 화면 여부. 앱 창 너비가 COMPACT_DP보다 좁으면 한 줄(세로) 레이아웃을 쓴다.
 * 기준 화면: Connect-S 가로 1700 / 분할 840, Connect-L 가로 1274 / 세로 628 (dp).
 */
val LocalCompact = staticCompositionLocalOf { false }

const val COMPACT_DP = 1100
