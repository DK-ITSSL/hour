package com.hour.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.hour.domain.journey.TripCategory

/** 색 토큰. 차량 화면에서 읽기 쉽도록 대비를 높게 잡았다. */
object HourColors {
    val Paper = Color(0xFFF5F2EC)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1F2329)
    val Sub = Color(0xFF6B6F76)
    val Faint = Color(0xFF9A9DA3)
    val Line = Color(0xFFE6E1D8)
    val Night = Color(0xFF14161B)
    val NightCard = Color(0xFF22252C)
    val Accent = Color(0xFF1F4E79)
    val Amber = Color(0xFFF2A541)
    val Locked = Color(0xFFD9D4CA)

    val Rain = Color(0xFF4A90D9)
    val Star = Color(0xFF7B61C9)
    val Wind = Color(0xFF2FA59A)
    val Warm = Color(0xFFE9824A)
    val Companion = Color(0xFFE0607E)
    val Voice = Color(0xFFD99A2B)
    val Safe = Color(0xFF3E9B5F)
}

/** 글자 크기 토큰(Figma 기준 Title 40/36/32, Body 30/28/26을 앱 영역 크기에 맞춰 줄였다). */
object HourType {
    val Display = 40.sp
    val Title = 32.sp
    val Heading = 26.sp
    val Body = 22.sp
    val Small = 18.sp
    val Tiny = 15.sp
}

val TripCategory.color: Color
    get() = when (this) {
        TripCategory.COMMUTE -> Color(0xFF3D6FB6)
        TripCategory.TRIP -> Color(0xFF1F9E89)
        TripCategory.DATE -> Color(0xFFE0607E)
        TripCategory.DAILY -> Color(0xFF6A9F3D)
    }

@Composable
fun HourTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = HourColors.Accent,
            onPrimary = Color.White,
            background = HourColors.Paper,
            surface = HourColors.Card,
            onSurface = HourColors.Ink,
            onBackground = HourColors.Ink,
        ),
        content = content,
    )
}
