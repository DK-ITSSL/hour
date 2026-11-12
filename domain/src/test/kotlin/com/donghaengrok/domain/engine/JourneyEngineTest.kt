package com.donghaengrok.domain.engine

import com.donghaengrok.domain.journey.TripCategory
import com.donghaengrok.domain.signal.AnchorType
import com.donghaengrok.domain.signal.DriveGear
import com.donghaengrok.domain.signal.SeatPosition
import com.donghaengrok.domain.signal.WindowPosition
import com.donghaengrok.domain.signal.WiperLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JourneyEngineTest {
    private val home = 37.4127 to 127.0947      // 성남 수정구
    private val office = 37.5010 to 127.0396    // 강남
    private val gangneung = 37.8055 to 128.9087 // 강릉 경포해변

    @Test
    fun `강릉 여행 - 비·바람·별빛·순간을 기록하고 여행으로 추천한다`() {
        val s = Scenario("2026-11-14T14:00")
        s.anchor(AnchorType.HOME, home.first, home.second)
        s.anchor(AnchorType.OFFICE, office.first, office.second)
        s.address("경기 성남시 수정구 시흥동", home.first, home.second)
        s.loc(home.first, home.second)
        s.seat(SeatPosition.FRONT_PASSENGER, true); s.seat(SeatPosition.REAR_LEFT, true)
        s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE)
        s.drive(1.0, 150.0, home, gangneung, 90f)
        s.at(20.0); s.wiper(true, WiperLevel.HEAVY)
        s.at(55.0); s.wiper(false)
        s.at(60.0); s.window(WindowPosition.REAR_LEFT, 0.6f)
        s.at(66.0); s.window(WindowPosition.REAR_LEFT, 0f)
        s.at(70.0); s.moment("애들이 무지개 봤어")
        s.at(120.0); s.highBeam(true)
        s.at(130.0); s.highBeam(false)
        s.at(150.0); s.address("강원 강릉시 저동", gangneung.first, gangneung.second)
        s.arrive("강릉 경포해변", "강원 강릉시 창해로 514")
        s.at(151.0); s.speed(0f); s.gear(DriveGear.PARK)

        val engine = JourneyEngine { "trip-1" }
        val out = s.run(engine, tickUntilMin = 185.0)

        assertTrue(out.any { it is EngineOutput.Started })
        val gift = out.filterIsInstance<EngineOutput.GiftReady>().single().summary
        val done = out.filterIsInstance<EngineOutput.Finished>().single().summary
        assertTrue(done.final)
        assertTrue(done.distanceKm > 150, "distance ${done.distanceKm}")
        assertEquals(TripCategory.TRIP, done.suggestion?.category)
        assertTrue(done.rain.present && done.rain.peakLevel == 3)
        assertTrue(done.wind.present && WindowPosition.REAR_LEFT in done.wind.windows)
        assertTrue(done.light.starlight)
        assertEquals(1, done.moments.size)
        assertTrue("비" !in done.moments[0].context) // 순간 기록 시점엔 비가 그쳤다
        assertTrue("경기" in done.places.provinces && "강원" in done.places.provinces, "${done.places.provinces}")
        assertEquals(3, done.companions.count)
        assertEquals("강릉 경포해변", done.destinationName)
        assertTrue(done.title.contains("강릉 경포해변 가는 길"), done.title)
        assertTrue(done.endDwellMinutes >= 30)
        assertEquals("강원", done.end.province)
        assertTrue(gift.narrative.isNotBlank())
    }

    @Test
    fun `회사에 도착하면 출퇴근길로 추천한다`() {
        val s = Scenario("2026-11-13T08:10")
        s.anchor(AnchorType.HOME, home.first, home.second)
        s.anchor(AnchorType.OFFICE, office.first, office.second)
        s.loc(home.first, home.second); s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE)
        s.drive(1.0, 40.0, home, office, 40f)
        s.at(41.0); s.speed(0f); s.gear(DriveGear.PARK)
        val out = s.run(JourneyEngine { "c-1" }, tickUntilMin = 75.0)
        val done = out.filterIsInstance<EngineOutput.Finished>().single().summary
        assertEquals(TripCategory.COMMUTE, done.suggestion?.category)
        assertEquals("회사에 도착했어요", done.suggestion?.reason)
    }

    @Test
    fun `앞좌석 두 사람만 탔고 회사와 무관하면 데이트로 추천한다`() {
        val s = Scenario("2026-11-14T19:00")
        s.anchor(AnchorType.HOME, home.first, home.second)
        s.loc(home.first, home.second)
        s.seat(SeatPosition.FRONT_PASSENGER, true); s.seat(SeatPosition.REAR_LEFT, false); s.seat(SeatPosition.REAR_RIGHT, false)
        s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE)
        s.drive(1.0, 25.0, home, 37.5512 to 126.9882, 50f) // 남산
        s.at(26.0); s.speed(0f); s.gear(DriveGear.PARK)
        val out = s.run(JourneyEngine { "d-1" }, tickUntilMin = 60.0)
        val done = out.filterIsInstance<EngineOutput.Finished>().single().summary
        assertEquals(TripCategory.DATE, done.suggestion?.category)
        assertTrue(done.title.contains("둘이서"), done.title)
    }

    @Test
    fun `주차장 이동처럼 300m 전에 P로 돌아오면 버린다`() {
        val s = Scenario()
        s.loc(home.first, home.second); s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE)
        s.at(1.5); s.loc(home.first + 0.0005, home.second) // 약 55m
        s.at(2.0); s.gear(DriveGear.PARK)
        val out = s.run(JourneyEngine { "p-1" })
        assertTrue(out.any { it is EngineOutput.Discarded })
        assertTrue(out.none { it is EngineOutput.Finished })
    }

    @Test
    fun `휴게소에서 30분 안에 다시 출발하면 같은 여정으로 잇고 정차를 기록한다`() {
        val s = Scenario()
        s.loc(home.first, home.second); s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE)
        s.drive(1.0, 60.0, home, 37.60 to 127.80, 80f)
        s.at(61.0); s.speed(0f); s.gear(DriveGear.PARK)
        s.at(76.0); s.gear(DriveGear.DRIVE)
        s.drive(76.0, 120.0, 37.60 to 127.80, gangneung, 80f)
        s.at(121.0); s.speed(0f); s.gear(DriveGear.PARK)
        val out = s.run(JourneyEngine { "m-1" }, tickUntilMin = 160.0)
        val finished = out.filterIsInstance<EngineOutput.Finished>()
        assertEquals(1, finished.size)
        val stops = finished.single().summary.places.stops
        assertEquals(1, stops.size)
        assertEquals(15, stops[0].minutes)
    }

    @Test
    fun `여정이 없을 때 들어온 순간 기록은 따로 내보낸다`() {
        val s = Scenario()
        s.gear(DriveGear.PARK)
        s.moment("주차장에서")
        val out = s.run(JourneyEngine())
        assertNotNull(out.filterIsInstance<EngineOutput.OrphanMoment>().singleOrNull())
    }

    @Test
    fun `체크포인트로 복원하면 이어서 기록하고 오래 끊겼으면 마감한다`() {
        val s = Scenario()
        s.loc(home.first, home.second); s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE)
        s.drive(1.0, 20.0, home, 37.50 to 127.30, 60f)
        s.at(21.0); s.speed(0f); s.gear(DriveGear.PARK)
        val engine = JourneyEngine { "cp-1" }
        s.run(engine)
        val cp = engine.checkpoint(s.now)
        val text = JourneyEngine.json.encodeToString(EngineCheckpoint.serializer(), cp)
        val restored = JourneyEngine()
        val out = restored.restore(JourneyEngine.json.decodeFromString(EngineCheckpoint.serializer(), text), s.now + 40 * 60_000)
        assertEquals("cp-1", out.filterIsInstance<EngineOutput.Finished>().single().summary.id)
    }

    @Test
    fun `과속과 급감속을 센다`() {
        val s = Scenario()
        s.loc(home.first, home.second); s.gear(DriveGear.PARK)
        s.at(1.0); s.gear(DriveGear.DRIVE); s.limit(60)
        // 1초 간격 속도: 80km/h로 15초(과속 1회), 이후 2초 만에 80→40(급감속 1회)
        for (i in 0..15) { s.now = s.t0 + 120_000 + i * 1_000L; s.speed(80f) }
        s.now += 1_000; s.speed(60f); s.now += 1_000; s.speed(40f)
        for (i in 0..20) { s.now += 1_000; s.speed(40f) }
        s.drive(3.0, 20.0, home, 37.45 to 127.15, 40f)
        val engine = JourneyEngine { "s-1" }
        s.run(engine)
        val cur = engine.current()!!
        assertEquals(1, cur.safety.overspeedEpisodes)
        assertTrue(cur.safety.harshEvents >= 1)
    }
}
