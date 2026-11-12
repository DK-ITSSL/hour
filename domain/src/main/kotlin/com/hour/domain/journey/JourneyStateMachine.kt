package com.hour.domain.journey

import com.hour.domain.geo.haversineMeters
import com.hour.domain.signal.DriveGear
import kotlinx.serialization.Serializable

/**
 * 여정 경계 판정(블루프린트 §6).
 *
 * IDLE ─gear≠P→ PROVISIONAL ─거리≥0.3km→ DRIVING ─P→ STOPPED
 *  - PROVISIONAL에서 0.3km 전에 P가 되면 주차장 이동으로 보고 버린다.
 *  - STOPPED에서 30분·500m 안에 다시 출발하면 같은 여정으로 잇는다(휴게소 등).
 *  - STOPPED: 도착 이벤트 뒤 P, 또는 2km 이상 달린 뒤 P 3분이면 도착 선물. P 30분이면 최종 마감.
 */
@Serializable
class JourneyStateMachine(
    var state: State = State.IDLE,
    var parkedAt: Long = 0,
    var parkedLat: Double? = null,
    var parkedLon: Double? = null,
    var arrived: Boolean = false,
    var giftSent: Boolean = false,
) {
    @Serializable
    enum class State { IDLE, PROVISIONAL, DRIVING, STOPPED }

    sealed interface Transition {
        data class Start(val at: Long) : Transition
        data object Confirm : Transition
        data object Discard : Transition
        data class Park(val at: Long) : Transition
        data class Resume(val at: Long, val stoppedMs: Long) : Transition
        data object GiftReady : Transition
        data class Finalize(val at: Long) : Transition
    }

    fun onGear(gear: DriveGear, at: Long, km: Double, lat: Double?, lon: Double?): List<Transition> {
        if (gear == DriveGear.UNKNOWN) return emptyList()
        val moving = gear.isMoving
        return when (state) {
            State.IDLE -> if (moving) {
                state = State.PROVISIONAL
                listOf(Transition.Start(at))
            } else emptyList()

            State.PROVISIONAL -> when {
                moving -> emptyList()
                km < MIN_KM -> { reset(); listOf(Transition.Discard) }
                else -> listOf(Transition.Confirm) + park(at, lat, lon)
            }

            State.DRIVING -> if (!moving) park(at, lat, lon) else emptyList()

            State.STOPPED -> if (moving) {
                val stoppedMs = at - parkedAt
                val near = parkedLat == null || lat == null || lon == null ||
                    haversineMeters(parkedLat!!, parkedLon!!, lat, lon) <= MERGE_METERS
                if (stoppedMs <= MERGE_MS && near) {
                    state = State.DRIVING
                    arrived = false
                    giftSent = false
                    listOf(Transition.Resume(at, stoppedMs))
                } else {
                    val out = listOf(Transition.Finalize(parkedAt), Transition.Start(at))
                    reset()
                    state = State.PROVISIONAL
                    out
                }
            } else emptyList()
        }
    }

    /** 거리가 늘 때마다 호출한다. 임시 여정을 확정한다. */
    fun onProgress(km: Double): List<Transition> =
        if (state == State.PROVISIONAL && km >= MIN_KM) {
            state = State.DRIVING
            listOf(Transition.Confirm)
        } else emptyList()

    fun onArrived(): List<Transition> {
        arrived = true
        return if (state == State.STOPPED && !giftSent) {
            giftSent = true
            listOf(Transition.GiftReady)
        } else emptyList()
    }

    fun onTick(now: Long, km: Double): List<Transition> {
        if (state != State.STOPPED) return emptyList()
        val out = mutableListOf<Transition>()
        val parkedFor = now - parkedAt
        if (!giftSent && (arrived || (parkedFor >= GIFT_AFTER_PARK_MS && km >= GIFT_MIN_KM))) {
            giftSent = true
            out += Transition.GiftReady
        }
        if (parkedFor >= FINALIZE_AFTER_PARK_MS) {
            out += Transition.Finalize(parkedAt)
            reset()
        }
        return out
    }

    /** 프로세스 재시작 등으로 오래 끊겼을 때: P 상태로 30분 넘게 지났으면 마감한다. */
    fun onResumeAfterGap(now: Long): List<Transition> =
        if (state == State.STOPPED && now - parkedAt >= FINALIZE_AFTER_PARK_MS) {
            val out = listOf(Transition.Finalize(parkedAt))
            reset()
            out
        } else emptyList()

    /** 사용자가 직접 기록을 끝낼 때(디버그·이후 '기록 끝내기' 기능). */
    fun forceFinalize(now: Long): List<Transition> {
        if (state == State.IDLE) return emptyList()
        val at = if (state == State.STOPPED) parkedAt else now
        reset()
        return listOf(Transition.Finalize(at))
    }

    private fun park(at: Long, lat: Double?, lon: Double?): List<Transition> {
        state = State.STOPPED
        parkedAt = at
        parkedLat = lat
        parkedLon = lon
        val out = mutableListOf<Transition>(Transition.Park(at))
        if (arrived && !giftSent) {
            giftSent = true
            out += Transition.GiftReady
        }
        return out
    }

    private fun reset() {
        state = State.IDLE
        parkedAt = 0
        parkedLat = null
        parkedLon = null
        arrived = false
        giftSent = false
    }

    companion object {
        const val MIN_KM = 0.3
        const val MERGE_MS = 30 * 60_000L
        const val MERGE_METERS = 500.0
        const val GIFT_AFTER_PARK_MS = 3 * 60_000L
        const val GIFT_MIN_KM = 2.0
        const val FINALIZE_AFTER_PARK_MS = 30 * 60_000L
    }
}
