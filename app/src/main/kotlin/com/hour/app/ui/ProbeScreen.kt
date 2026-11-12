package com.hour.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hour.app.debug.DemoActions
import com.hour.app.di.AppContainer
import com.hour.app.recorder.JourneyRecorderService
import com.hour.car.ProbeItem
import com.hour.car.ProbeStatus
import kotlinx.coroutines.launch

/** D1 진단 화면. 주행 규제 중에는 최소 화면(주행 중 미니)만 보여준다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProbeScreen(container: AppContainer, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ux by container.car.ux.state.collectAsState()
    val running by container.recorderRunning.collectAsState()
    val lines by container.log.lines.collectAsState()
    var probe by remember { mutableStateOf<List<ProbeItem>>(emptyList()) }
    val current by container.currentJourney.collectAsState()
    val journeys by container.journeys.journeys.collectAsState(initial = emptyList())
    val unlocks by container.journeys.unlocks.collectAsState(initial = emptyList())

    LaunchedVoiceLog(container)

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(24.dp)) {
            Text("H:our · D1 진단 (${container.car.flavor})", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "주행 규제: DO=${ux.requiresDistractionOptimization} active=${ux.activeRestrictions} (${ux.source})  ·  기록 서비스: ${if (running) "실행 중" else "정지"}  ·  마지막 부팅 수신: ${container.log.recall("lastBoot") ?: "없음"}",
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermissions) { Text("권한 요청", fontSize = 20.sp) }
                Button(onClick = {
                    if (running) JourneyRecorderService.stop(context) else JourneyRecorderService.start(context)
                }) { Text(if (running) "기록 서비스 중지" else "기록 서비스 시작", fontSize = 20.sp) }
                Button(onClick = {
                    scope.launch {
                        container.log.add("probe: start")
                        probe = container.car.signals.probe()
                        probe.forEach { container.log.add("probe: ${it.status} ${it.name} ${it.detail}") }
                    }
                }) { Text("진단 실행", fontSize = 20.sp) }
                OutlinedButton(onClick = { container.car.voice.speak("이번 여정, 그 시간을 함께 기억할게요.") }) { Text("TTS 테스트", fontSize = 20.sp) }
                OutlinedButton(onClick = { container.car.voice.listenOnce() }) { Text("STT 테스트", fontSize = 20.sp) }
                OutlinedButton(onClick = { container.log.clear() }) { Text("로그 지우기", fontSize = 20.sp) }
            }
            DebugPanel(container)
            if (com.hour.app.BuildConfig.DEBUG) DemoButtons(container)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    item { Text("현재 여정", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    item {
                        Text(
                            current?.let { c ->
                                "${c.title}\n${"%.1f".format(c.distanceKm)}km · ${c.drivingMinutes}분 · 비 ${c.rain.minutes}분 · 바람 ${"%.1f".format(c.wind.km)}km · 별빛 ${c.light.highBeamMinutes}분 · 동행 ${c.companions.count}명 · 순간 ${c.moments.size}\n추천: ${c.suggestion?.category?.korean} (${c.suggestion?.reason})"
                            } ?: "없음",
                            fontSize = 16.sp,
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)); Text("최근 여정", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    items(journeys.take(5)) { j ->
                        Text(
                            "${if (j.final) "■" else "□"} ${j.title} · ${"%.1f".format(j.distanceKm)}km · ${j.category?.korean ?: "미확정"}${j.categorySource?.let { "($it)" } ?: ""}",
                            fontSize = 15.sp,
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)); Text("해금", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    items(unlocks) { u -> Text("★ ${u.rule?.name ?: u.ruleId}", fontSize = 15.sp) }
                    item { Spacer(Modifier.height(8.dp)); Text("진단 결과", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    items(probe) { p ->
                        Text(
                            "${mark(p.status)} ${p.name}  ${p.detail}",
                            fontSize = 15.sp,
                            color = when (p.status) {
                                ProbeStatus.OK -> Color(0xFF00843D)
                                ProbeStatus.UNSUPPORTED -> Color(0xFF8A6D00)
                                ProbeStatus.FAILED -> Color(0xFFC62828)
                                ProbeStatus.INFO -> Color.DarkGray
                            },
                        )
                    }
                }
                LazyColumn(Modifier.weight(1.4f)) {
                    item { Text("실시간 로그", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    items(lines) { Text(it, fontSize = 14.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

@Composable
private fun LaunchedVoiceLog(container: AppContainer) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        container.car.voice.events.collect { container.log.add("voice: $it") }
    }
}

private fun mark(s: ProbeStatus) = when (s) {
    ProbeStatus.OK -> "✔"
    ProbeStatus.UNSUPPORTED -> "–"
    ProbeStatus.FAILED -> "✘"
    ProbeStatus.INFO -> "ℹ"
}

/** 시연 준비 버튼(디버그 빌드 전용). adb로도 같은 동작을 부를 수 있다(MainActivity의 DEBUG_* 액션). */
@Composable
private fun DemoButtons(container: AppContainer) {
    val scope = rememberCoroutineScope()
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { scope.launch { DemoActions.seed(container) } }) { Text("시연용 샘플 넣기", fontSize = 18.sp) }
        OutlinedButton(onClick = { scope.launch { DemoActions.previewArrival(container) } }) { Text("도착 흐름 미리보기", fontSize = 18.sp) }
        OutlinedButton(onClick = { scope.launch { DemoActions.reset(container) } }) { Text("시연 초기화", fontSize = 18.sp) }
    }
}
