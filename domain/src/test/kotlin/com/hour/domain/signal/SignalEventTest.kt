package com.hour.domain.signal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SignalEventTest {
    @Test
    fun `P단만 정지 상태로 본다`() {
        assertFalse(DriveGear.PARK.isMoving)
        assertTrue(DriveGear.DRIVE.isMoving)
        assertTrue(DriveGear.REVERSE.isMoving)
        assertFalse(DriveGear.UNKNOWN.isMoving)
    }

    @Test
    fun `뒷좌석 판정`() {
        assertTrue(SeatPosition.REAR_LEFT.isRear)
        assertFalse(SeatPosition.FRONT_PASSENGER.isRear)
    }

    @Test
    fun `알 수 없는 와이퍼 단계는 중간 강도로 본다`() {
        assertEquals(2, WiperLevel.UNKNOWN.intensity)
    }
}
