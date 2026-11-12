package com.hour.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hour.app.di.AppContainer
import com.hour.car.FakeUxRestrictionSource
import com.hour.domain.signal.DriveGear
import com.hour.domain.signal.MomentSource
import com.hour.domain.signal.SignalEvent
import com.hour.domain.signal.WindowPosition
import com.hour.domain.signal.WiperLevel

/** sim 빌드 디버그 패널: 버튼으로 차량 신호를 직접 넣는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(container: AppContainer) {
    val signals = container.car.signals
    fun now() = System.currentTimeMillis()
    val ux = container.car.ux as? FakeUxRestrictionSource
    FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Btn("P") { signals.inject(SignalEvent.Gear(now(), DriveGear.PARK)); ux?.setDriving(false) }
        Btn("D") { signals.inject(SignalEvent.Gear(now(), DriveGear.DRIVE)); ux?.setDriving(true) }
        Btn("와이퍼 강") { signals.inject(SignalEvent.Wiper(now(), true, WiperLevel.HEAVY)) }
        Btn("와이퍼 끔") { signals.inject(SignalEvent.Wiper(now(), false, WiperLevel.OFF)) }
        Btn("상향등 켬") { signals.inject(SignalEvent.HighBeam(now(), true)) }
        Btn("뒷창문 60%") { signals.inject(SignalEvent.Window(now(), WindowPosition.REAR_LEFT, 0.6f)) }
        Btn("순간 기록") { signals.inject(SignalEvent.Moment(now(), "애들이 무지개 봤어", MomentSource.DEBUG)) }
    }
}

@Composable
private fun Btn(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) { Text(label, fontSize = 18.sp) }
}
