package com.hour.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hour.app.BuildConfig
import com.hour.app.di.AppContainer
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourTheme
import com.hour.app.ui.theme.HourType
import kotlinx.coroutines.flow.StateFlow

sealed interface Route {
    data object Home : Route
    data class Detail(val id: String) : Route
    data object Wardrobe : Route
    data object Diagnostics : Route
}

/** 액티비티가 화면에 요청하는 이동(알림 탭, Gleo SHOW_TODAY_JOURNEY 등). */
sealed interface OpenRequest {
    data object Today : OpenRequest
    data class Arrival(val journeyId: String) : OpenRequest
    data object Gift : OpenRequest
}

class ScreenState(
    val permissionsMissing: StateFlow<Boolean>,
    val openRequest: StateFlow<OpenRequest?>,
    val consumeOpen: () -> Unit,
    val requestPermissions: () -> Unit,
    val backAtRoot: () -> Unit,
)

@Composable
fun AppRoot(container: AppContainer, screen: ScreenState) {
    val ux by container.car.ux.state.collectAsState()
    val pending by container.arrival.pending.collectAsState()
    val gift by container.arrival.gift.collectAsState()
    val open by screen.openRequest.collectAsState()
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val top = stack.last()

    BackHandler {
        when {
            ux.requiresDistractionOptimization -> screen.backAtRoot()
            gift != null -> container.arrival.dismissGift()
            pending != null -> container.arrival.later()
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            else -> screen.backAtRoot()
        }
    }

    LaunchedEffect(open) {
        when (val r = open) {
            OpenRequest.Today -> {
                container.journeys.latestJourneyId()?.let { id -> stack.clear(); stack.add(Route.Home); stack.add(Route.Detail(id)) }
            }
            is OpenRequest.Arrival -> container.journeys.journey(r.journeyId)?.let { container.arrival.ask(it) }
            OpenRequest.Gift, null -> Unit
        }
        if (open != null) screen.consumeOpen()
    }

    HourTheme {
        // 주행 중에는 최소 화면만(애니메이션·목록·입력 없음).
        if (ux.requiresDistractionOptimization) {
            DrivingMini(container)
            return@HourTheme
        }
        Row(Modifier.fillMaxSize().background(HourColors.Paper)) {
            NavRail(
                current = top,
                onSelect = { r -> stack.clear(); stack.add(r) },
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (val r = top) {
                    Route.Home -> HomeScreen(
                        container = container,
                        screen = screen,
                        onOpen = { id -> stack.add(Route.Detail(id)) },
                        onWardrobe = { stack.clear(); stack.add(Route.Wardrobe) },
                    )
                    is Route.Detail -> JourneyDetailScreen(container, r.id, onBack = { stack.removeAt(stack.lastIndex) })
                    Route.Wardrobe -> WardrobeScreen(container)
                    Route.Diagnostics -> ProbeScreen(container, onRequestPermissions = screen.requestPermissions)
                }
            }
        }
        pending?.let { p -> CategorySheet(container, p) }
        gift?.let { g ->
            GiftReveal(
                container = container,
                gift = g,
                onWardrobe = { container.arrival.dismissGift(); stack.clear(); stack.add(Route.Wardrobe) },
                onDetail = { container.arrival.dismissGift(); stack.clear(); stack.add(Route.Home); stack.add(Route.Detail(g.journey.id)) },
            )
        }
    }
}

@Composable
private fun NavRail(current: Route, onSelect: (Route) -> Unit) {
    val items = buildList {
        add("홈" to Route.Home)
        add("옷장" to Route.Wardrobe)
        if (BuildConfig.DEBUG) add("진단" to Route.Diagnostics)
    }
    Column(
        Modifier.width(150.dp).fillMaxHeight().background(HourColors.Card).padding(vertical = 28.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("H:our", fontSize = HourType.Title, fontWeight = FontWeight.Black, color = HourColors.Accent)
        Spacer(Modifier.height(18.dp))
        items.forEach { (label, route) ->
            val selected = when (current) {
                is Route.Detail -> route == Route.Home
                else -> current == route
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) HourColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(route) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = HourType.Heading,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) HourColors.Accent else HourColors.Sub,
                )
            }
        }
    }
}
