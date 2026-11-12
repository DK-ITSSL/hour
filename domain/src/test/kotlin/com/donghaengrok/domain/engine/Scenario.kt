package com.donghaengrok.domain.engine

import com.donghaengrok.domain.signal.AnchorType
import com.donghaengrok.domain.signal.DriveGear
import com.donghaengrok.domain.signal.MomentSource
import com.donghaengrok.domain.signal.SeatPosition
import com.donghaengrok.domain.signal.SignalEvent
import com.donghaengrok.domain.signal.WindowPosition
import com.donghaengrok.domain.signal.WiperLevel
import java.time.ZonedDateTime
import com.donghaengrok.domain.geo.SunCalculator

/** 테스트용 시나리오 빌더: 시간을 분 단위로 진행하며 신호를 쌓는다. */
class Scenario(startKst: String = "2026-11-14T14:00") {
    val t0: Long = ZonedDateTime.of(java.time.LocalDateTime.parse(startKst), SunCalculator.KST).toInstant().toEpochMilli()
    var now: Long = t0
    val events = mutableListOf<SignalEvent>()

    fun at(minute: Double) { now = t0 + (minute * 60_000).toLong() }
    fun gear(g: DriveGear) { events += SignalEvent.Gear(now, g) }
    fun speed(kmh: Float) { events += SignalEvent.Speed(now, kmh) }
    fun loc(lat: Double, lon: Double, alt: Double = 50.0) { events += SignalEvent.Location(now, lat, lon, alt) }
    fun address(a: String, lat: Double, lon: Double) { events += SignalEvent.NaviAddress(now, a, lat, lon) }
    fun wiper(on: Boolean, level: WiperLevel = if (on) WiperLevel.MODERATE else WiperLevel.OFF) { events += SignalEvent.Wiper(now, on, level) }
    fun highBeam(on: Boolean) { events += SignalEvent.HighBeam(now, on) }
    fun window(p: WindowPosition, ratio: Float) { events += SignalEvent.Window(now, p, ratio) }
    fun seat(s: SeatPosition, occupied: Boolean) { events += SignalEvent.SeatOccupancy(now, s, occupied) }
    fun belt(s: SeatPosition, buckled: Boolean) { events += SignalEvent.SeatBelt(now, s, buckled) }
    fun moment(text: String) { events += SignalEvent.Moment(now, text, MomentSource.GLEO) }
    fun arrive(name: String, address: String) { events += SignalEvent.NaviArrived(now, name, address) }
    fun anchor(type: AnchorType, lat: Double, lon: Double, name: String = type.name) {
        events += SignalEvent.NaviAnchor(now, type, name, "", lat, lon)
    }
    fun limit(kmh: Int) { events += SignalEvent.NaviSpeedLimit(now, kmh) }

    /** from→to를 steps번에 나눠 이동하며 위치·속도를 넣는다. */
    fun drive(fromMin: Double, toMin: Double, from: Pair<Double, Double>, to: Pair<Double, Double>, kmh: Float, steps: Int = 30) {
        for (i in 0..steps) {
            at(fromMin + (toMin - fromMin) * i / steps)
            val f = i.toDouble() / steps
            loc(from.first + (to.first - from.first) * f, from.second + (to.second - from.second) * f)
            speed(kmh)
        }
    }

    fun run(engine: JourneyEngine, tickUntilMin: Double? = null): List<EngineOutput> {
        val out = mutableListOf<EngineOutput>()
        events.sortedBy { it.at }.forEach { out += engine.onEvent(it) }
        tickUntilMin?.let { until ->
            var m = (now - t0) / 60_000.0
            while (m <= until) { out += engine.onTick(t0 + (m * 60_000).toLong()); m += 1.0 }
        }
        return out
    }
}
