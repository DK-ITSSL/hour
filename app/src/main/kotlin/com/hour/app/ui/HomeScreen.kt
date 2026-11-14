package com.hour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hour.app.data.JourneyRepository
import com.hour.app.di.AppContainer
import com.hour.app.recorder.JourneyRecorderService
import com.hour.app.text.Korean
import com.hour.app.ui.art.CharacterStage
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType
import com.hour.app.ui.theme.color
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.TripCategory
import com.hour.domain.unlock.UnlockRule
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen(container: AppContainer, screen: ScreenState, onOpen: (String) -> Unit, onWardrobe: () -> Unit, onSettings: () -> Unit) {
    val journeys by container.journeys.journeys.collectAsState(initial = emptyList())
    val unlocks by container.journeys.unlocks.collectAsState(initial = emptyList())
    val equippedIds by container.prefs.equipped.collectAsState()
    val running by container.recorderRunning.collectAsState()
    val permissionsMissing by screen.permissionsMissing.collectAsState()
    val anchors by container.journeys.anchorRecords.collectAsState(initial = null)
    var filter by remember { mutableStateOf<TripCategory?>(null) }

    val unlockedIds = unlocks.map { it.ruleId }.toSet()
    val equipped = equipped(container.journeys.rules, equippedIds.values.toSet(), unlockedIds)
    val newCount = unlocks.count { !it.seen }

    val context = LocalContext.current
    val compact = LocalCompact.current
    val shown = journeys.filter { filter == null || it.category == filter }

    val characterPanel: @Composable (Modifier) -> Unit = { m ->
        HourCard(m, padding = 0) {
            Box(Modifier.fillMaxSize()) {
                CharacterStage(equipped, Modifier.fillMaxSize())
                Column(Modifier.padding(24.dp)) {
                    Text("아워", fontSize = HourType.Title, fontWeight = FontWeight.Bold, color = HourColors.Ink)
                    Caption(greeting(journeys))
                }
                Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                    Pill(
                        if (newCount > 0) "옷장 · 새 선물 $newCount" else "옷장 열기",
                        if (newCount > 0) HourColors.Companion else HourColors.Accent,
                        filled = newCount > 0,
                        modifier = Modifier.clickable(onClick = onWardrobe),
                    )
                }
            }
        }
    }
    val notices: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (permissionsMissing) {
                NoticeCard(
                    "기록을 시작하려면 권한이 필요해요",
                    "위치·차속(어디를 얼마나 달렸는지), 마이크(이 순간 기억), 알림(도착 선물)",
                    "권한 허용",
                    screen.requestPermissions,
                )
            } else if (!running) {
                NoticeCard("기록이 꺼져 있어요", "켜 두면 시동을 걸 때마다 아워가 알아서 기록해요.", "기록 시작") {
                    JourneyRecorderService.start(context)
                }
            }
            val latest = journeys.firstOrNull()
            if (latest != null && latest.category == null) {
                NoticeCard(
                    "방금 여정, 어떤 길이었나요?",
                    latest.title + (latest.suggestion?.let { " · 추천 ${it.category.korean}" } ?: ""),
                    "고르기",
                    color = HourColors.Companion,
                ) { container.arrival.ask(latest) }
            } else if (!permissionsMissing && running && anchors?.isEmpty() == true) {
                NoticeCard(
                    "집·회사를 알려 주세요",
                    "출퇴근길·여행을 알아보는 기준이에요. 내비 즐겨찾기에 없으면 설정에서 지금 위치로 정할 수 있어요.",
                    "설정",
                    onSettings,
                )
            }
        }
    }
    val emptyCard: @Composable () -> Unit = {
        HourCard(Modifier.fillMaxWidth()) {
            Text(
                if (journeys.isEmpty()) "첫 여정을 기다리고 있어요" else "이 운행타입의 여정은 아직 없어요",
                fontSize = HourType.Heading, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Caption(if (journeys.isEmpty()) "출발하면 비·별빛·바람·동행·목소리를 차가 스스로 기록해요." else "다른 탭을 눌러 보세요.")
        }
    }

    if (compact) {
        // 좁은 화면: 한 줄로 쌓고 전체를 스크롤한다.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { characterPanel(Modifier.fillMaxWidth().height(340.dp)) }
            item { StatsRow(journeys) }
            item { notices() }
            item { CategoryTabs(journeys, filter) { filter = it } }
            if (shown.isEmpty()) item { emptyCard() }
            items(shown, key = { it.id }) { j -> JourneyCard(j) { onOpen(j.id) } }
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        // 왼쪽: 캐릭터와 방, 누적 감각
        Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            characterPanel(Modifier.fillMaxWidth().weight(1f))
            StatsRow(journeys)
        }
        // 오른쪽: 알림 카드, 운행타입 탭, 여정 목록
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            notices()
            CategoryTabs(journeys, filter) { filter = it }
            if (shown.isEmpty()) emptyCard()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(shown, key = { it.id }) { j -> JourneyCard(j) { onOpen(j.id) } }
            }
        }
    }
}

fun equipped(rules: List<UnlockRule>, equippedIds: Set<String>, unlockedIds: Set<String>): List<UnlockRule> =
    rules.filter { it.id in equippedIds && it.id in unlockedIds }

private fun greeting(journeys: List<JourneySummary>): String {
    if (journeys.isEmpty()) return "첫 여정을 함께할 준비가 됐어요"
    val minutes = journeys.sumOf { it.drivingMinutes }
    val km = journeys.sumOf { it.distanceKm }
    return "함께한 시간 ${Korean.minutes(minutes)} · ${Korean.km(km)}"
}

@Composable
private fun StatsRow(journeys: List<JourneySummary>) {
    val stats = listOf(
        "여정" to "${journeys.size}",
        "비 만난 길" to "${journeys.count { it.rain.present }}",
        "별빛 길" to "${journeys.count { it.light.starlight }}",
        "바람" to Korean.km(journeys.sumOf { it.wind.km }),
        "순간" to "${journeys.sumOf { it.moments.size }}",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { (label, value) ->
            HourCard(Modifier.weight(1f), padding = 16) {
                val compact = LocalCompact.current
                Text(value, fontSize = if (compact) HourType.Body else HourType.Heading, fontWeight = FontWeight.Bold, color = HourColors.Ink, maxLines = 1, softWrap = false)
                Caption(label, size = if (compact) HourType.Tiny else HourType.Small)
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, action: String, onAction: () -> Unit) =
    NoticeCard(title, body, action, HourColors.Accent, onAction)

@Composable
private fun NoticeCard(title: String, body: String, action: String, color: Color, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.10f)).padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = HourType.Heading, fontWeight = FontWeight.Bold, color = HourColors.Ink)
            Caption(body, size = HourType.Small)
        }
        BigButton(action, onAction, color = color)
    }
}

@Composable
private fun CategoryTabs(journeys: List<JourneySummary>, selected: TripCategory?, onSelect: (TripCategory?) -> Unit) {
    val compact = LocalCompact.current
    Row(
        Modifier.fillMaxWidth().then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tabs = listOf<TripCategory?>(null) + TripCategory.entries
        tabs.forEach { c ->
            val on = c == selected
            val color = c?.color ?: HourColors.Ink
            val count = if (c == null) journeys.size else journeys.count { it.category == c }
            Box(
                (if (compact) Modifier.widthIn(min = 120.dp).padding(horizontal = 0.dp) else Modifier.weight(1f))
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) color else HourColors.Card)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${c?.korean ?: "전체"} $count",
                    fontSize = HourType.Body,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else HourColors.Sub,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyCard(j: JourneySummary, onClick: () -> Unit) {
    val color = j.category?.color ?: HourColors.Faint
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HourColors.Card).clickable(onClick = onClick),
    ) {
        Box(Modifier.width(10.dp).height(150.dp).background(color))
        Column(Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Caption("${Korean.date(j.startedAt)} ${Korean.clock(j.startedAt)} · ${Korean.minutes(j.drivingMinutes)} · ${Korean.km(j.distanceKm)}")
            Text(j.title, fontSize = HourType.Heading, fontWeight = FontWeight.Bold, color = HourColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                senseTags(j).forEach { SenseDot(it) }
            }
        }
        Box(Modifier.padding(18.dp).align(Alignment.CenterVertically)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (j.category != null) Pill(j.category!!.korean, color) else Pill("확인 전", HourColors.Faint)
                if (j.id.startsWith(JourneyRepository.SAMPLE_PREFIX)) Pill("샘플", HourColors.Faint, size = HourType.Tiny)
            }
        }
    }
}
