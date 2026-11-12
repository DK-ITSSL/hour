package com.donghaengrok.app.ui

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
import com.donghaengrok.app.di.AppContainer
import com.donghaengrok.app.recorder.JourneyRecorderService
import com.donghaengrok.car.ProbeItem
import com.donghaengrok.car.ProbeStatus
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

    LaunchedVoiceLog(container)

    MaterialTheme {
        if (ux.requiresDistractionOptimization) {
            DrivingMini(running = running, onMoment = { container.car.voice.listenOnce() })
            return@MaterialTheme
        }
        Column(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(24.dp)) {
            Text("동행록 · D1 진단 (${container.car.flavor})", fontSize = 32.sp, fontWeight = FontWeight.Bold)
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
                OutlinedButton(onClick = { container.car.voice.speak("동행록이 이번 여정을 기억할게요.") }) { Text("TTS 테스트", fontSize = 20.sp) }
                OutlinedButton(onClick = { container.car.voice.listenOnce() }) { Text("STT 테스트", fontSize = 20.sp) }
                OutlinedButton(onClick = { container.log.clear() }) { Text("로그 지우기", fontSize = 20.sp) }
            }
            DebugPanel(container)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    item { Text("진단 결과", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
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
private fun DrivingMini(running: Boolean, onMoment: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF131417)).padding(32.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(if (running) "기록 중" else "기록 대기", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onMoment, modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Text("이 순간 기억", fontSize = 32.sp)
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
