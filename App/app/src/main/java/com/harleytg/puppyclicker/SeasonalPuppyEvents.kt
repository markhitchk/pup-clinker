package com.harleytg.puppyclicker

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class SeasonalWindow(val start: Instant, val end: Instant, val cycle: String) {
    fun contains(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
}

data class SeasonalPuppyEvent(
    val puppyId: String,
    val title: String,
    val emoji: String,
    val description: String,
    val startMonth: Int = 0,
    val startDay: Int = 0,
    val endMonth: Int = 0,
    val endDay: Int = 0
) {
    val isBirthday: Boolean get() = puppyId == "birthday"
}

/** Calendar rules are independent of Android, the renderer, and the game save. */
object SeasonalPuppyEvents {
    val events = listOf(
        SeasonalPuppyEvent("halloween", "Pumpkin Pup", "🎃", "Halloween celebration", 10, 24, 11, 1),
        SeasonalPuppyEvent("santa", "Santa Paws", "🎄", "Christmas celebration", 12, 1, 1, 1),
        SeasonalPuppyEvent("birthday", "Birthday Buddy", "🎂", "Your birthday celebration")
    )

    fun find(puppyId: String): SeasonalPuppyEvent? = events.firstOrNull { it.puppyId == puppyId }
    fun isSeasonal(puppyId: String): Boolean = find(puppyId) != null

    fun birthday(month: Int, day: Int): MonthDay? =
        try { MonthDay.of(month, day) } catch (_: RuntimeException) { null }

    private fun birthdayDate(year: Int, birthday: MonthDay): LocalDate =
        if (birthday.monthValue == 2 && birthday.dayOfMonth == 29 && !LocalDate.of(year, 1, 1).isLeapYear) {
            LocalDate.of(year, 2, 28)
        } else birthday.atYear(year)

    fun windowForYear(event: SeasonalPuppyEvent, year: Int, zone: ZoneId, birthday: MonthDay?): SeasonalWindow? {
        if (event.isBirthday) {
            val date = birthday?.let { birthdayDate(year, it) } ?: return null
            return SeasonalWindow(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
                "${event.puppyId}-$year"
            )
        }
        val startDate = LocalDate.of(year, event.startMonth, event.startDay)
        val endYear = if (event.endMonth < event.startMonth ||
            (event.endMonth == event.startMonth && event.endDay <= event.startDay)) year + 1 else year
        val endDate = LocalDate.of(endYear, event.endMonth, event.endDay)
        return SeasonalWindow(
            startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            endDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            "${event.puppyId}-$year"
        )
    }

    fun activeWindow(event: SeasonalPuppyEvent, now: Instant, zone: ZoneId, birthday: MonthDay?): SeasonalWindow? {
        val year = now.atZone(zone).year
        return (year - 1..year + 1).asSequence()
            .mapNotNull { windowForYear(event, it, zone, birthday) }
            .firstOrNull { it.contains(now) }
    }

    fun nextWindow(event: SeasonalPuppyEvent, now: Instant, zone: ZoneId, birthday: MonthDay?): SeasonalWindow? {
        val year = now.atZone(zone).year
        return (year - 1..year + 2).asSequence()
            .mapNotNull { windowForYear(event, it, zone, birthday) }
            .filter { it.start.isAfter(now) }
            .minByOrNull { it.start }
    }

    fun formatLocal(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale).withZone(zone).format(instant)

    fun availability(event: SeasonalPuppyEvent, now: Instant, zone: ZoneId, birthday: MonthDay?): String {
        if (event.isBirthday && birthday == null) return "Add your birthday to activate this event."
        val active = activeWindow(event, now, zone, birthday)
        if (active != null) return "Available now · ends ${formatLocal(active.end, zone)}"
        val next = nextWindow(event, now, zone, birthday) ?: return "Schedule unavailable"
        return "Next: ${formatLocal(next.start, zone)} – ${formatLocal(next.end, zone)}"
    }
}
