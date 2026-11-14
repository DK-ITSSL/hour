package com.hour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hour.app.di.AppContainer
import com.hour.app.data.JourneyRepository
import com.hour.app.text.Korean
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType
import com.hour.app.ui.theme.color
import com.hour.domain.journey.JourneySummary
import com.hour.domain.signal.MomentSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyDetailScreen(container: AppContainer, journeyId: String, onBack: () -> Unit) {
    val journeys by container.journeys.journeys.collectAsState(initial = null)
    val j = journeys?.firstOrNull { it.id == journeyId }
    if (j == null) {
        Box(Modifier.fillMaxSize().padding(28.dp)) {
            Caption(if (journeys == null) "불러오는 중…" else "이 여정을 찾을 수 없어요")
        }
        return
    }
    val color = j.category?.color ?: HourColors.Accent

    val compact = LocalCompact.current
    val back: @Composable () -> Unit = {
        Text("‹ 홈", fontSize = HourType.Heading, color = HourColors.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
    }
    val header: @Composable () -> Unit = {
        HourCard(Modifier.fillMaxWidth()) {
            Caption("${Korean.date(j.startedAt)} ${Korean.clock(j.startedAt)} – ${Korean.clock(j.endedAt)}")
            Spacer(Modifier.height(6.dp))
            Text(j.title, fontSize = if (compact) HourType.Title else HourType.Display, fontWeight = FontWeight.Bold, color = HourColors.Ink, lineHeight = HourType.Display * 1.2)
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (j.category != null) Pill(j.category!!.korean, color, filled = true, size = HourType.Body) else Pill("운행타입 확인 전", HourColors.Faint, size = HourType.Body)
                Pill("${Korean.km(j.distanceKm)} · ${Korean.minutes(j.drivingMinutes)}", HourColors.Ink, size = HourType.Body)
                if (j.id.startsWith(JourneyRepository.SAMPLE_PREFIX)) Pill("샘플", HourColors.Faint, size = HourType.Body)
                Text(
                    if (j.category != null) "바꾸기" else "고르기",
                    fontSize = HourType.Body, color = HourColors.Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { container.arrival.change(j) }.padding(8.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(j.narrative, fontSize = HourType.Body, color = HourColors.Ink, lineHeight = HourType.Body * 1.55)
            Spacer(Modifier.height(18.dp))
            BigButton("낭독 듣기", onClick = { container.car.voice.speak(j.narrative) }, color = color, filled = false)
        }
    }
    val pathCard: @Composable () -> Unit = {
        HourCard(Modifier.fillMaxWidth(), padding = 0) {
            if (j.path.size >= 2) {
                PathSketch(j.path, color, Modifier.fillMaxWidth().height(if (compact) 240.dp else 320.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { Caption("경로를 그릴 위치 기록이 없어요") }
            }
        }
    }

    if (compact) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            back(); header(); pathCard(); SenseCard(j); PlacesCard(j)
            if (j.moments.isNotEmpty()) MomentsCard(j)
            SafetyCard(j)
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
            Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            back(); header(); SenseCard(j)
        }
        Column(
            Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(44.dp))
            pathCard(); PlacesCard(j)
            if (j.moments.isNotEmpty()) MomentsCard(j)
            SafetyCard(j)
        }
    }
}

@Composable
private fun SenseCard(j: JourneySummary) {
    val total = j.drivingMinutes.coerceAtLeast(1).toFloat()
    val rows = buildList {
        if (j.rain.present) add(Triple("비", "${j.rain.minutes}분 · ${Korean.km(j.rain.km)}" + if (j.rain.peakLevel >= 3) " · 세찬 비" else "", HourColors.Rain to j.rain.minutes / total))
        if (j.light.sunsetDrive) add(Triple("노을", "해 질 녘을 달렸어요", HourColors.Warm to 1f))
        if (j.light.starlight) add(Triple("별빛", "${j.light.highBeamMinutes}분 · ${Korean.km(j.light.highBeamKm)}", HourColors.Star to j.light.highBeamMinutes / total))
        if (j.wind.present) add(Triple("바람", "${j.wind.minutes}분 · ${Korean.km(j.wind.km)} · 창문 ${j.wind.windows.size}곳", HourColors.Wind to j.wind.minutes / total))
        val warm = j.warmth.seatHeatMinutes + j.warmth.defrostMinutes
        if (warm > 0) add(Triple("온기", "열선 ${j.warmth.seatHeatMinutes}분 · 김서림 ${j.warmth.defrostMinutes}분", HourColors.Warm to warm / total))
        if (j.companions.count >= 2) add(Triple("동행", "${j.companions.count}명이 함께", HourColors.Companion to j.companions.count / 5f))
        if (j.moments.isNotEmpty()) add(Triple("목소리", "순간 ${j.moments.size}개", HourColors.Voice to (j.moments.size / 3f)))
    }
    HourCard(Modifier.fillMaxWidth()) {
        Text("이번 여정의 감각", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) Caption("조용하고 맑은 길이었어요.")
        rows.forEach { (label, value, bar) ->
            val (c, ratio) = bar
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = HourType.Body, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0.04f, 1f)).clip(RoundedCornerShape(8.dp)).background(c))
                }
                Text(value, fontSize = HourType.Small, color = HourColors.Sub, modifier = Modifier.padding(start = 16.dp).width(if (LocalCompact.current) 180.dp else 300.dp))
            }
        }
    }
}

@Composable
private fun PlacesCard(j: JourneySummary) {
    HourCard(Modifier.fillMaxWidth()) {
        Text("다녀온 곳", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val from = j.start.address ?: j.start.district ?: "출발지"
        val to = j.destinationName ?: j.end.address ?: j.end.district ?: "도착지"
        Text("$from  →  $to", fontSize = HourType.Body, color = HourColors.Ink)
        val extras = buildList {
            if (j.places.provinces.isNotEmpty()) add(j.places.provinces.joinToString(" · "))
            if (j.places.tunnels > 0) add("터널 ${j.places.tunnels}")
            val rest = j.places.serviceAreas + j.places.restAreas
            if (rest > 0) add("휴게소 $rest")
            if (j.places.stops.isNotEmpty()) add("머문 곳 ${j.places.stops.size}")
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Caption(extras.joinToString("  ·  "))
        }
    }
}

@Composable
private fun MomentsCard(j: JourneySummary) {
    HourCard(Modifier.fillMaxWidth()) {
        Text("기억한 순간", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        j.moments.forEach { m ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Caption(Korean.clock(m.at), size = HourType.Small)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("“${m.text}”", fontSize = HourType.Body, color = HourColors.Ink)
                    Caption(
                        listOfNotNull(
                            when (m.source) { MomentSource.GLEO -> "Gleo"; MomentSource.STT -> "음성"; MomentSource.DEBUG -> "시연" },
                            m.address,
                        ).joinToString(" · "),
                        size = HourType.Tiny,
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyCard(j: JourneySummary) {
    val s = j.safety
    val judgeable = s.speedJudgeable && j.distanceKm >= 5.0
    val clean = judgeable && s.harshEvents == 0 && (!s.limitJudgeable || s.overspeedEpisodes == 0)
    val color = if (clean) HourColors.Safe else HourColors.Faint
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.10f)).padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                when {
                    clean -> "안심 운행"
                    judgeable -> "안심 기록은 다음 여정에서"
                    else -> "안심 기록 판정 전"
                },
                fontSize = HourType.Heading, fontWeight = FontWeight.Bold, color = if (clean) HourColors.Safe else HourColors.Sub,
            )
            Caption(
                when {
                    clean -> "급감속·과속 없이 편안하게 다녀왔어요."
                    judgeable -> "속도와 감속을 부드럽게 하면 안심 기념품이 열려요."
                    else -> "5km 이상, 속도 신호가 충분한 여정만 판정해요."
                },
                color = HourColors.Sub,
            )
        }
    }
}
