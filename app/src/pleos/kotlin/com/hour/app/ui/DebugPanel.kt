package com.hour.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import com.hour.app.recorder.JourneyRecorderService
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hour.app.di.AppContainer
import com.hour.domain.signal.MomentSource
import com.hour.domain.signal.SignalEvent

/** pleos 빌드: 실제 차량 신호는 Virtual Console로 넣는다. 여기서는 순간 기록만 흉내 낸다. */
@Composable
fun DebugPanel(container: AppContainer) {
    val context = LocalContext.current
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { container.car.signals.inject(SignalEvent.Moment(System.currentTimeMillis(), "디버그 순간 기록", MomentSource.DEBUG)) }) {
            Text("순간 기록 넣기(디버그)", fontSize = 18.sp)
        }
        OutlinedButton(onClick = { JourneyRecorderService.finalizeNow(context) }) {
            Text("기록 마감(디버그)", fontSize = 18.sp)
        }
    }
}
