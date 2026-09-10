package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyBirthdayTest {
    @Test fun januaryFirstIsValid() = assertTrue(PuppyBirthday.isValid(1, 1))
    @Test fun aprilThirtyFirstIsInvalid() = assertFalse(PuppyBirthday.isValid(4, 31))
    @Test fun februaryTwentyNinthIsValidWithoutYear() = assertTrue(PuppyBirthday.isValid(2, 29))
    @Test fun februaryThirtiethIsInvalid() = assertFalse(PuppyBirthday.isValid(2, 30))
    @Test fun invalidMonthIsRejected() = assertFalse(PuppyBirthday.isValid(0, 1))
}
