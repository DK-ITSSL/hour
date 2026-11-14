package com.hour.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hour.app.arrival.Gift
import com.hour.app.di.AppContainer
import com.hour.app.text.Korean
import com.hour.app.ui.art.CharacterStage
import com.hour.app.ui.art.ItemIcon
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType
import com.hour.app.ui.theme.color
import com.hour.domain.journey.CategorySource
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.TripCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // 뒤 화면으로 터치가 새지 않게 막는다.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 도착 후 운행타입 확인. 추천이 먼저 선택돼 있고, 처음 묻는 경우 10초 동안 손대지 않으면 추천대로 기록한다.
 * 이미 정한 타입을 바꾸는 경우(여정 상세의 '바꾸기')에는 자동 적용하지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySheet(container: AppContainer, p: JourneySummary) {
    val scope = rememberCoroutineScope()
    val suggested = p.suggestion?.category
    val changing = p.category != null
    var selected by remember(p.id) { mutableStateOf(p.category ?: suggested ?: TripCategory.DAILY) }
    var touched by remember(p.id) { mutableStateOf(changing) }
    var remain by remember(p.id) { mutableIntStateOf(AUTO_SECONDS) }
    var busy by remember(p.id) { mutableStateOf(false) }
    val compact = LocalCompact.current

    fun confirm(category: TripCategory, source: CategorySource) {
        if (busy) return
        busy = true
        scope.launch { container.arrival.confirm(p.id, category, source) }
    }

    LaunchedEffect(p.id, touched) {
        if (touched || suggested == null) return@LaunchedEffect
        while (remain > 0) {
            delay(1_000)
            remain -= 1
        }
        confirm(suggested, CategorySource.AUTO_DEFAULT)
    }

    Scrim {
        Column(
            Modifier.widthIn(max = 1180.dp).fillMaxWidth(0.92f).clip(RoundedCornerShape(32.dp)).background(HourColors.Card).padding(if (compact) 24.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Caption(if (changing) "운행타입 바꾸기" else "도착했어요 · ${Korean.km(p.distanceKm)} · ${Korean.minutes(p.drivingMinutes)}")
            Text(
                if (changing) "이 여정은 어떤 길이었나요?" else "이번 운행은 어떤 길이었나요?",
                fontSize = if (compact) HourType.Title else HourType.Display, fontWeight = FontWeight.Bold, color = HourColors.Ink,
            )
            p.suggestion?.let { Caption("${it.reason} · 운행타입마다 받는 선물이 달라요", size = HourType.Body) }
            // 넓은 화면은 한 줄 4개, 좁은 화면은 2×2
            TripCategory.entries.chunked(if (compact) 2 else 4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEach { c ->
                        CategoryChip(
                            category = c,
                            selected = c == selected,
                            recommended = c == suggested,
                            onClick = { selected = c; touched = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Caption(
                    when {
                        changing -> "바꿔도 이미 받은 선물은 그대로 남아요."
                        !touched && suggested != null -> "${remain}초 뒤 추천대로 기록할게요."
                        else -> "Gleo에게 \"여행으로 기록해줘\"라고 말해도 돼요."
                    },
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                BigButton("나중에", onClick = { container.arrival.later() }, color = HourColors.Sub, filled = false)
                BigButton(
                    "${Korean.euroRo(selected.korean)} 기록",
                    onClick = { confirm(selected, CategorySource.USER) },
                    color = selected.color,
                    enabled = !busy,
                )
            }
        }
    }
}

/** 도착 선물: 여정 카드가 뒤집히고, 새로 열린 애셋이 하나씩 나타난다. P단에서만 보인다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftReveal(container: AppContainer, gift: Gift, onWardrobe: () -> Unit, onDetail: () -> Unit) {
    val j = gift.journey
    val color = j.category?.color ?: HourColors.Accent
    var flipped by remember(gift) { mutableStateOf(false) }
    var shown by remember(gift) { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(if (flipped) 0f else 180f, tween(900), label = "flip")
    val compact = LocalCompact.current

    LaunchedEffect(gift) {
        delay(250)
        flipped = true
        delay(1_000)
        repeat(gift.unlocked.size) {
            shown += 1
            delay(450)
        }
    }

    Scrim {
        Column(
            Modifier.widthIn(max = 1180.dp).fillMaxWidth(0.92f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 여정 카드(뒤집기)
            Box(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .height(if (compact) 260.dp else 300.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 14f * density
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (rotation > 90f) HourColors.Accent else HourColors.Card),
                contentAlignment = Alignment.Center,
            ) {
                if (rotation > 90f) {
                    Text("H:our", fontSize = HourType.Display * 1.6f, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.9f))
                } else {
                    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            j.category?.let { Pill(it.korean, color, filled = true) }
                            Text("${Korean.date(j.startedAt)} · ${Korean.km(j.distanceKm)}", fontSize = HourType.Small, color = HourColors.Sub)
                        }
                        Text(j.title, fontSize = HourType.Title, fontWeight = FontWeight.Bold, color = HourColors.Ink, maxLines = 2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) { senseTags(j).forEach { SenseDot(it) } }
                        Text(j.narrative, fontSize = HourType.Small, color = HourColors.Sub, maxLines = 3, lineHeight = HourType.Small * 1.5)
                    }
                }
            }

            // 새로 열린 애셋
            if (gift.unlocked.isEmpty()) {
                Row(
                    Modifier.clip(RoundedCornerShape(28.dp)).background(HourColors.Card).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CharacterStage(emptyList(), Modifier.size(120.dp), room = false)
                    Text("이번 여정도 아워가 기억해 둘게요.", fontSize = HourType.Heading, fontWeight = FontWeight.Bold, color = HourColors.Ink)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    gift.unlocked.forEachIndexed { i, rule ->
                        AnimatedVisibility(visible = shown > i, enter = scaleIn(tween(380)) + fadeIn(tween(380))) {
                            Column(
                                Modifier.width(if (compact) 170.dp else 210.dp).clip(RoundedCornerShape(28.dp)).background(HourColors.Card).padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Pill("새 선물", HourColors.Companion, filled = true, size = HourType.Tiny)
                                ItemIcon(rule, locked = false, modifier = Modifier.size(if (compact) 100.dp else 130.dp))
                                Text(rule.name, fontSize = HourType.Body, fontWeight = FontWeight.Bold, color = HourColors.Ink, textAlign = TextAlign.Center)
                                rule.category?.let { Text("${it.korean} 전용", fontSize = HourType.Tiny, color = it.color) }
                            }
                        }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("낭독 듣기", onClick = { container.car.voice.speak(j.narrative) }, color = Color.White, filled = false)
                BigButton("여정 보기", onClick = onDetail, color = Color.White, filled = false)
                if (gift.unlocked.isNotEmpty()) BigButton("옷장에서 입혀 보기", onClick = onWardrobe, color = HourColors.Companion)
                BigButton("닫기", onClick = { container.arrival.dismissGift() }, color = HourColors.Accent)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private const val AUTO_SECONDS = 10
