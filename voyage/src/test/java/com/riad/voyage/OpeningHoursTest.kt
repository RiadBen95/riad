package com.riad.voyage

import com.riad.voyage.data.OpeningHours
import com.riad.voyage.data.minutesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class OpeningHoursTest {
    @Test fun readsSimpleRules() {
        val spec = "Mo-Fr 10:00-18:00; Sa 10:00-14:00; Su off"
        assertEquals(600..1080, OpeningHours.forDay(spec, DayOfWeek.WEDNESDAY))
        assertEquals(600..840, OpeningHours.forDay(spec, DayOfWeek.SATURDAY))
        assertTrue(OpeningHours.forDay(spec, DayOfWeek.SUNDAY)!!.isEmpty())
        assertNull(OpeningHours.forDay(null, DayOfWeek.MONDAY))
    }

    @Test fun readsTimes() {
        assertEquals(15 * 60 + 5, minutesOf("15:05"))
        assertEquals(9 * 60, minutesOf("9h00"))
        assertNull(minutesOf("demain"))
    }
}
