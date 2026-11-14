package com.hour.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hour.app.BuildConfig
import com.hour.app.data.AnchorRecord
import com.hour.app.debug.DemoActions
import com.hour.app.di.AppContainer
import com.hour.app.recorder.JourneyRecorderService
import com.hour.app.text.Korean
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType
import com.hour.domain.signal.AnchorType
import kotlinx.coroutines.launch

/**
 * 설정: 집·회사, 낭독, 기록 켜기·끄기, 체험용 샘플, 개인정보 안내.
 * 집·회사는 운행타입 추천(출퇴근길·여행)의 기준이다. 내비 즐겨찾기에서 자동으로 가져오고, 여기서 직접 정할 수도 있다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val anchors by container.journeys.anchorRecords.collectAsState(initial = emptyList())
    val position by container.lastPosition.collectAsState()
    val narration by container.prefs.narration.collectAsState()
    val running by container.recorderRunning.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("설정", fontSize = HourType.Display, fontWeight = FontWeight.Bold, color = HourColors.Ink)

        // 집·회사
        HourCard(Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
            Text("집·회사", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Caption("회사를 오간 운행은 출퇴근길로, 집에서 100km 넘게 떠나면 여행으로 추천해요. 내비 즐겨찾기의 집·회사를 자동으로 가져오고, 여기서 직접 정할 수도 있어요. 직접 정한 위치가 먼저예요.")
            Spacer(Modifier.height(10.dp))
            Caption(
                position?.let { p -> "지금 위치: ${p.address ?: "%.4f, %.4f".format(p.lat, p.lon)} (${if (p.gps) "GPS" else "내비"})" }
                    ?: if (running) "지금 위치를 아직 받지 못했어요. 잠시 뒤 다시 시도해 주세요." else "기록이 꺼져 있어 지금 위치를 알 수 없어요.",
                color = HourColors.Accent,
            )
            Spacer(Modifier.height(8.dp))
            listOf(AnchorType.HOME to "집", AnchorType.OFFICE to "회사").forEach { (type, label) ->
                AnchorRow(
                    label = label,
                    record = anchors.firstOrNull { it.anchor.type == type },
                    canSet = position != null,
                    onSetHere = {
                        scope.launch {
                            notice = if (container.setAnchorHere(type)) "지금 위치를 ${Korean.euroRo(label)} 정했어요." else "지금 위치를 아직 받지 못했어요."
                        }
                    },
                    onClear = { scope.launch { container.clearAnchor(type); notice = "${label} 위치를 지웠어요." } },
                )
            }
            notice?.let { Caption(it, color = HourColors.Safe) }
        }

        // 음성·기록
        HourCard(Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
            Text("음성과 기록", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("도착 안내·낭독", fontSize = HourType.Body, fontWeight = FontWeight.SemiBold)
                    Caption("도착하면 운행타입을 묻고, 선물과 여정 이야기를 읽어 줘요. 주행 중에는 짧은 확인만 해요.")
                }
                Switch(
                    checked = narration,
                    onCheckedChange = { container.prefs.setNarration(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = HourColors.Accent),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (running) "기록 중" else "기록 꺼짐", fontSize = HourType.Body, fontWeight = FontWeight.SemiBold)
                    Caption("켜 두면 시동을 걸 때마다 자동으로 기록해요. 끄면 다음에 앱을 열 때까지 기록하지 않아요.")
                }
                BigButton(
                    if (running) "기록 끄기" else "기록 켜기",
                    onClick = { if (running) JourneyRecorderService.stop(context) else JourneyRecorderService.start(context) },
                    color = if (running) HourColors.Sub else HourColors.Accent,
                    filled = !running,
                )
            }
        }

        // 체험용 샘플
        HourCard(Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
            Text("체험용 샘플", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Caption("실제로 운행하지 않아도 화면을 둘러볼 수 있게 지난 며칠의 샘플 여정과 도착 장면을 넣어요. 샘플 여정에는 \"샘플\" 표시가 붙고, 지우면 샘플과 샘플로 받은 선물만 사라져요.")
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BigButton("샘플 여정 넣기", onClick = { scope.launch { DemoActions.seed(container) } }, color = HourColors.Accent, filled = false)
                BigButton("도착 장면 보기", onClick = { scope.launch { DemoActions.previewArrival(container) } }, color = HourColors.Companion, filled = false)
                BigButton("샘플 지우기", onClick = { scope.launch { DemoActions.clearSamples(container) } }, color = HourColors.Sub, filled = false)
            }
        }

        // 정보
        HourCard(Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
            Text("기록은 이 차 안에만", fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Caption("H:our는 인터넷 권한이 없어요. 여정·위치·음성 메모는 차 안에만 저장되고 밖으로 보내지 않아요. 속도는 안심 기록 판정에만 쓰고 궤적은 남기지 않아요.")
            Spacer(Modifier.height(8.dp))
            Caption("버전 ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (디버그)" else ""}", color = HourColors.Faint)
        }
    }
}

@Composable
private fun AnchorRow(label: String, record: AnchorRecord?, canSet: Boolean, onSetHere: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, fontSize = HourType.Heading, fontWeight = FontWeight.Bold, color = HourColors.Ink, modifier = Modifier.widthIn(min = 80.dp))
        Column(Modifier.weight(1f)) {
            if (record == null) {
                Text("아직 몰라요", fontSize = HourType.Body, color = HourColors.Sub)
            } else {
                Text(
                    "%.4f, %.4f".format(record.anchor.point.lat, record.anchor.point.lon),
                    fontSize = HourType.Body, color = HourColors.Ink,
                )
                Caption(if (record.manual) "직접 정함" else "내비 즐겨찾기 · ${record.anchor.name}", size = HourType.Tiny)
            }
        }
        if (!LocalCompact.current) {
            BigButton("지금 위치로", onClick = onSetHere, enabled = canSet)
            if (record != null) BigButton("지우기", onClick = onClear, color = HourColors.Sub, filled = false)
        }
    }
    if (LocalCompact.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("지금 위치로", onClick = onSetHere, enabled = canSet)
            if (record != null) BigButton("지우기", onClick = onClear, color = HourColors.Sub, filled = false)
        }
    }
}
