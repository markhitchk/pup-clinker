package com.harleytg.puppyclicker

import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class SeasonalPuppyEventsTest {
    private val utc = ZoneOffset.UTC
    private val halloween = SeasonalPuppyEvents.find("halloween")!!
    private val christmas = SeasonalPuppyEvents.find("santa")!!
    private val birthday = SeasonalPuppyEvents.find("birthday")!!

    @Test fun halloweenUsesExclusiveUtcEnd() {
        assertNull(SeasonalPuppyEvents.activeWindow(halloween, Instant.parse("2026-10-23T23:59:59Z"), utc, null))
        assertNotNull(SeasonalPuppyEvents.activeWindow(halloween, Instant.parse("2026-10-24T00:00:00Z"), utc, null))
        assertNotNull(SeasonalPuppyEvents.activeWindow(halloween, Instant.parse("2026-10-31T23:59:59Z"), utc, null))
        assertNull(SeasonalPuppyEvents.activeWindow(halloween, Instant.parse("2026-11-01T00:00:00Z"), utc, null))
    }

    @Test fun christmasCrossesNewYearAndRecurs() {
        val now = Instant.parse("2027-01-01T00:00:00Z")
        assertNull(SeasonalPuppyEvents.activeWindow(christmas, now, utc, null))
        assertEquals("santa-2027", SeasonalPuppyEvents.nextWindow(christmas, now, utc, null)!!.cycle)
        assertEquals("santa-2026", SeasonalPuppyEvents.activeWindow(christmas, Instant.parse("2026-12-31T23:59:59Z"), utc, null)!!.cycle)
    }

    @Test fun holidayInstantIsIdenticalAcrossZones() {
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val la = ZoneId.of("America/Los_Angeles")
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(SeasonalPuppyEvents.activeWindow(halloween, now, la, null), SeasonalPuppyEvents.activeWindow(halloween, now, tokyo, null))
        assertEquals("2026-10-23T17:00-07:00[America/Los_Angeles]", now.atZone(la).toString())
    }

    @Test fun birthdayUsesLocalMidnightNotUtcMidnight() {
        val la = ZoneId.of("America/Los_Angeles")
        val date = MonthDay.of(9, 9)
        val window = SeasonalPuppyEvents.windowForYear(birthday, 2026, la, date)!!
        assertEquals(Instant.parse("2026-09-09T07:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-09-10T07:00:00Z"), window.end)
        assertNull(SeasonalPuppyEvents.activeWindow(birthday, window.start.minusNanos(1), la, date))
        assertNotNull(SeasonalPuppyEvents.activeWindow(birthday, window.start, la, date))
        assertNull(SeasonalPuppyEvents.activeWindow(birthday, window.end, la, date))
    }

    @Test fun birthdayMidnightRespectsDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val window = SeasonalPuppyEvents.windowForYear(birthday, 2026, zone, MonthDay.of(3, 8))!!
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), window.end)
    }

    @Test fun leapBirthdayFallsBackToFebruary28() {
        val date = MonthDay.of(2, 29)
        assertEquals(Instant.parse("2027-02-28T00:00:00Z"), SeasonalPuppyEvents.windowForYear(birthday, 2027, utc, date)!!.start)
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"), SeasonalPuppyEvents.windowForYear(birthday, 2028, utc, date)!!.start)
        assertNull(SeasonalPuppyEvents.birthday(2, 30))
        assertNull(SeasonalPuppyEvents.birthday(13, 1))
        assertNull(SeasonalPuppyEvents.activeWindow(birthday, Instant.now(), utc, null))
    }
}
