package com.harleytg.puppyclicker

internal object PuppyBirthday {
    fun maxDay(month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> 29
        else -> 0
    }

    fun isValid(month: Int, day: Int): Boolean {
        val maximum = maxDay(month)
        return maximum > 0 && day in 1..maximum
    }
}
