package com.hour.domain.geo

import java.time.Instant
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoTest {
    @Test
    fun `서울 11월 중순 일몰은 오후 5시 20분 전후`() {
        val noon = ZonedDateTime.parse("2026-11-14T12:00+09:00[Asia/Seoul]").toInstant().toEpochMilli()
        val t = SunCalculator.sunTimes(noon, 37.5665, 126.9780)!!
        val set = Instant.ofEpochMilli(t.sunset).atZone(SunCalculator.KST)
        val rise = Instant.ofEpochMilli(t.sunrise).atZone(SunCalculator.KST)
        val setMin = set.hour * 60 + set.minute
        val riseMin = rise.hour * 60 + rise.minute
        assertTrue(setMin in (17 * 60 + 5)..(17 * 60 + 35), "sunset $set")
        assertTrue(riseMin in (7 * 60 + 0)..(7 * 60 + 25), "sunrise $rise")
        assertEquals(14, set.dayOfMonth)
    }

    @Test
    fun `주소에서 시도와 시군구를 뽑는다`() {
        assertEquals(Region("경기", "성남시"), RegionParser.parse("경기도 성남시 수정구 시흥동"))
        assertEquals(Region("강원", "강릉시"), RegionParser.parse("강원특별자치도 강릉시 창해로 514"))
        assertEquals(Region(null, "수정구"), RegionParser.parse("수정구 시흥동"))
        assertEquals(Region("서울", "강남구"), RegionParser.parse("서울 강남구 테헤란로"))
    }

    @Test
    fun `좌표로 시도를 추정한다`() {
        assertEquals("서울", ProvinceLocator.locate(37.5665, 126.9780))
        assertEquals("경기", ProvinceLocator.locate(37.4127, 127.0947))
        assertEquals("강원", ProvinceLocator.locate(37.8055, 128.9087))
        assertEquals("제주", ProvinceLocator.locate(33.4996, 126.5312))
        assertNull(ProvinceLocator.locate(37.4220, -122.0840)) // 에뮬레이터 기본 위치(한국 밖)
    }

    @Test
    fun `거리는 위치·주행거리계·속도 중 큰 값을 쓰고 순간 이동은 버린다`() {
        val d = DistanceTracker()
        d.onLocation(37.0, 127.0, 0)
        d.onLocation(37.01, 127.0, 60_000) // 약 1.1km
        d.onLocation(38.0, 127.0, 61_000) // 1초에 110km → 버림
        assertTrue(d.trackKm in 1.0..1.2)
        d.onOdometer(100f); d.onOdometer(103f)
        assertEquals(3.0, d.km(), 0.001)
    }
}
