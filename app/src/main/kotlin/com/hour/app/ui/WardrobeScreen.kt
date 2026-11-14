package com.hour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hour.app.data.UnlockView
import com.hour.app.di.AppContainer
import com.hour.app.ui.art.CharacterStage
import com.hour.app.ui.art.ItemIcon
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType
import com.hour.app.ui.theme.color
import com.hour.domain.unlock.AssetSlot
import com.hour.domain.unlock.UnlockRule
import kotlinx.coroutines.launch

private val slotNames = linkedMapOf(
    AssetSlot.OUTFIT to "옷",
    AssetSlot.ACCESSORY to "소품",
    AssetSlot.HAT to "모자",
    AssetSlot.ROOM to "방",
)

@Composable
fun WardrobeScreen(container: AppContainer) {
    val unlocks by container.journeys.unlocks.collectAsState(initial = emptyList())
    val equippedIds by container.prefs.equipped.collectAsState()
    val rules = container.journeys.rules
    val slots = slotNames.keys.filter { s -> rules.any { it.slot == s } }
    var slot by remember { mutableStateOf(slots.firstOrNull() ?: AssetSlot.OUTFIT) }

    val byId: Map<String, UnlockView> = unlocks.associateBy { it.ruleId }
    val equipped = equipped(rules, equippedIds.values.toSet(), byId.keys)

    // 옷장을 나갈 때 '새 선물' 표시를 지운다.
    DisposableEffect(Unit) {
        onDispose { container.scope.launch { container.journeys.markUnlocksSeen() } }
    }

    val compact = LocalCompact.current
    val stage: @Composable (Modifier) -> Unit = { m ->
        HourCard(m, padding = 0) {
            Box(Modifier.fillMaxSize()) {
                CharacterStage(equipped, Modifier.fillMaxSize())
                Column(Modifier.padding(24.dp)) {
                    Text("아워", fontSize = HourType.Title, fontWeight = FontWeight.Bold, color = HourColors.Ink)
                    Caption(if (equipped.isEmpty()) "아직 아무것도 입지 않았어요" else equipped.joinToString(" · ") { it.name })
                }
                if (equipped.isNotEmpty()) {
                    Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                        Pill("모두 벗기", HourColors.Sub, modifier = Modifier.clickable { container.prefs.clearEquipped() })
                    }
                }
            }
        }
    }
    val shelf: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("옷장", fontSize = HourType.Display, fontWeight = FontWeight.Bold, color = HourColors.Ink)
            Caption("받은 선물 ${byId.keys.count { id -> rules.any { it.id == id } }} / ${rules.size} · 운행타입마다 다른 선물이 열려요", size = HourType.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.forEach { s ->
                    val on = s == slot
                    val newCount = rules.count { it.slot == s && byId[it.id]?.seen == false }
                    Box(
                        Modifier
                            .height(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) HourColors.Accent else HourColors.Card)
                            .clickable { slot = s }
                            .padding(horizontal = if (compact) 20.dp else 30.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            slotNames[s].orEmpty() + if (newCount > 0) " · 새 $newCount" else "",
                            fontSize = HourType.Body,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) Color.White else HourColors.Sub,
                        )
                    }
                }
            }
            val items = rules.filter { it.slot == slot }
                .sortedWith(compareBy<UnlockRule>({ byId[it.id] == null }, { it.category?.ordinal ?: 9 }))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 190.dp else 230.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.id }) { rule ->
                    val got = byId[rule.id]
                    ItemTile(
                        rule = rule,
                        unlocked = got != null,
                        isNew = got?.seen == false,
                        equipped = equippedIds[rule.slot] == rule.id,
                        onToggle = {
                            container.prefs.equip(rule.slot, if (equippedIds[rule.slot] == rule.id) null else rule.id)
                        },
                    )
                }
            }
        }
    }

    if (compact) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            stage(Modifier.fillMaxWidth().height(300.dp))
            shelf(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            stage(Modifier.weight(0.8f).fillMaxHeight())
            shelf(Modifier.weight(1.2f).fillMaxHeight())
        }
    }
}

@Composable
private fun ItemTile(rule: UnlockRule, unlocked: Boolean, isNew: Boolean, equipped: Boolean, onToggle: () -> Unit) {
    val tint = rule.category?.color ?: HourColors.Amber
    Column(
        Modifier
            .heightIn(min = 300.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (unlocked) HourColors.Card else HourColors.Card.copy(alpha = 0.55f))
            .border(if (equipped) 4.dp else 0.dp, if (equipped) tint else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable(enabled = unlocked, onClick = onToggle)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.CenterStart) {
            when {
                equipped -> Pill("입는 중", tint, filled = true, size = HourType.Tiny)
                isNew -> Pill("새 선물", HourColors.Companion, filled = true, size = HourType.Tiny)
                !unlocked -> Pill("잠김", HourColors.Faint, size = HourType.Tiny)
            }
        }
        ItemIcon(rule, locked = !unlocked, modifier = Modifier.size(130.dp))
        Text(rule.name, fontSize = HourType.Body, fontWeight = FontWeight.Bold, color = if (unlocked) HourColors.Ink else HourColors.Sub, textAlign = TextAlign.Center)
        Text(rule.category?.let { "${it.korean} 전용" } ?: "모든 길", fontSize = HourType.Tiny, color = tint)
        if (!unlocked && rule.hint.isNotBlank()) {
            Text(rule.hint, fontSize = HourType.Tiny, color = HourColors.Sub, textAlign = TextAlign.Center)
        }
    }
}
