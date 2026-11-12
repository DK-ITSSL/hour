package com.donghaengrok.domain.signal

/**
 * 차량·내비·위치·음성 신호를 SDK와 무관한 형태로 정규화한 이벤트.
 * 모든 이벤트는 수신 시각(epoch millis)을 가진다. 도메인 엔진은 이 타입만 안다.
 */
sealed interface SignalEvent {
    val at: Long

    // ---- 주행 상태 ----
    data class Gear(override val at: Long, val gear: DriveGear) : SignalEvent
    data class Speed(override val at: Long, val kmh: Float) : SignalEvent
    data class Odometer(override val at: Long, val km: Float) : SignalEvent

    // ---- 감각 ----
    data class Wiper(override val at: Long, val on: Boolean, val level: WiperLevel) : SignalEvent
    data class HighBeam(override val at: Long, val on: Boolean) : SignalEvent
    data class LowBeam(override val at: Long, val on: Boolean) : SignalEvent
    data class Window(override val at: Long, val position: WindowPosition, val openRatio: Float) : SignalEvent
    data class SeatOccupancy(override val at: Long, val seat: SeatPosition, val occupied: Boolean) : SignalEvent
    data class SeatBelt(override val at: Long, val seat: SeatPosition, val buckled: Boolean) : SignalEvent
    data class SeatHeat(override val at: Long, val seat: SeatPosition, val level: Int) : SignalEvent
    data class Defroster(override val at: Long, val on: Boolean) : SignalEvent
    data class OutsideTemperature(override val at: Long, val celsius: Float) : SignalEvent

    // ---- 위치·내비 ----
    data class Location(override val at: Long, val lat: Double, val lon: Double, val altitude: Double) : SignalEvent
    data class NaviAddress(override val at: Long, val address: String, val lat: Double, val lon: Double) : SignalEvent
    data class NaviArrived(override val at: Long, val name: String, val address: String) : SignalEvent
    data class NaviPassPoint(override val at: Long, val kind: PassPointKind, val remainMeters: Double) : SignalEvent
    data class NaviSpeedLimit(override val at: Long, val kmh: Int) : SignalEvent
    data class NaviCaution(override val at: Long, val kind: String, val remainMeters: Int) : SignalEvent
    data class NaviStatus(override val at: Long, val status: String) : SignalEvent
    data class NaviAnchor(override val at: Long, val type: AnchorType, val name: String, val address: String, val lat: Double, val lon: Double) : SignalEvent

    // ---- 사람 ----
    data class Moment(override val at: Long, val text: String, val source: MomentSource) : SignalEvent

    // ---- 진단 ----
    data class Unavailable(override val at: Long, val signal: String, val reason: String) : SignalEvent
}

enum class DriveGear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN;
    val isMoving: Boolean get() = this == REVERSE || this == DRIVE || this == NEUTRAL
}

/** 0 = 꺼짐, 1 = 약(안개·간헐), 2 = 중(연속 1~2단, AUTO), 3 = 강(연속 3~5단). */
enum class WiperLevel(val intensity: Int) { OFF(0), LIGHT(1), MODERATE(2), HEAVY(3), UNKNOWN(2) }

enum class WindowPosition { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, SUNROOF }

enum class SeatPosition { DRIVER, FRONT_PASSENGER, REAR_LEFT, REAR_CENTER, REAR_RIGHT;
    val isRear: Boolean get() = this == REAR_LEFT || this == REAR_CENTER || this == REAR_RIGHT
}

enum class PassPointKind { TUNNEL, SERVICE_AREA, REST_AREA, TOLLGATE }

enum class MomentSource { GLEO, STT, DEBUG }

enum class AnchorType { HOME, OFFICE }
