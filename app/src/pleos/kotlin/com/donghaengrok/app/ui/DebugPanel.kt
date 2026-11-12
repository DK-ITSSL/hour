package com.donghaengrok.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donghaengrok.app.di.AppContainer
import com.donghaengrok.domain.signal.MomentSource
import com.donghaengrok.domain.signal.SignalEvent

/** pleos 빌드: 실제 차량 신호는 Virtual Console로 넣는다. 여기서는 순간 기록만 흉내 낸다. */
@Composable
fun DebugPanel(container: AppContainer) {
    OutlinedButton(
        modifier = Modifier.padding(top = 12.dp),
        onClick = { container.car.signals.inject(SignalEvent.Moment(System.currentTimeMillis(), "디버그 순간 기록", MomentSource.DEBUG)) },
    ) { Text("순간 기록 넣기(디버그)", fontSize = 18.sp) }
}
