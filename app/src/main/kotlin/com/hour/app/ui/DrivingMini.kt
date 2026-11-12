package com.hour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hour.app.di.AppContainer
import com.hour.app.text.Korean
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType

/**
 * 주행 중 미니 화면(distractionOptimized). 상태 한 줄, 감각 표시등, 큰 버튼 하나만 둔다.
 * 애니메이션·목록·키보드 입력 없음.
 */
@Composable
fun DrivingMini(container: AppContainer) {
    val running by container.recorderRunning.collectAsState()
    val current by container.currentJourney.collectAsState()
    val listening by container.listening.collectAsState()
    val last by container.lastMoment.collectAsState()
    val j = current

    Column(
        Modifier.fillMaxSize().background(HourColors.Night).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("H:our", color = HourColors.Amber, fontSize = HourType.Title, fontWeight = FontWeight.Black)
        Text(
            when {
                j != null -> "기록 중 · ${Korean.minutes(j.drivingMinutes)} · ${Korean.km(j.distanceKm)}"
                running -> "출발을 기다리는 중"
                else -> "기록이 꺼져 있어요"
            },
            color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Lamp("비", HourColors.Rain, j?.rain?.present == true)
            Lamp("별빛", HourColors.Star, j?.light?.starlight == true)
            Lamp("바람", HourColors.Wind, j?.wind?.present == true)
            Lamp("동행 ${j?.companions?.count ?: 0}", HourColors.Companion, (j?.companions?.count ?: 0) >= 2)
            Lamp("순간 ${j?.moments?.size ?: 0}", HourColors.Voice, (j?.moments?.size ?: 0) > 0)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (listening) HourColors.Companion else HourColors.Amber)
                .clickable(enabled = !listening) { container.captureMoment() },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (listening) "듣고 있어요…" else "이 순간 기억", color = HourColors.Night, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            last?.let { "기억했어요 · “$it”" } ?: "버튼을 누르고 말하면 지금 이 순간을 여정에 남겨요. Gleo에게 말해도 돼요.",
            color = Color.White.copy(alpha = 0.7f), fontSize = HourType.Body,
        )
    }
}

@Composable
private fun Lamp(label: String, color: Color, on: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) color else HourColors.NightCard)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(label, color = if (on) Color.White else Color.White.copy(alpha = 0.45f), fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
    }
}
