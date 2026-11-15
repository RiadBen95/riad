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

    @Test
    fun googleDayTextBecomesASpec() {
        assertEquals("11:00-18:00", OpeningHours.specOf("11:00 – 18:00"))
        assertEquals("12:00-15:00,19:00-23:30", OpeningHours.specOf("12:00 – 3:00 PM, 7:00 – 11:30 PM"))
        assertEquals("18:30-00:00", OpeningHours.specOf("18:30 – 00:00"))
        assertEquals(null, OpeningHours.specOf("Ouvert 24h/24"))
        assertEquals(listOf(11 * 60), OpeningHours.ranges(OpeningHours.specOf("11:00 – 18:00"), java.time.DayOfWeek.SUNDAY)?.map { it.first })
    }
}
