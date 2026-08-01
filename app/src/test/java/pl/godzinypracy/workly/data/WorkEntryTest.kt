package pl.godzinypracy.workly.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkEntryTest {
    @Test
    fun calculatesRegularShiftWithBreak() {
        val entry = WorkEntry(
            date = LocalDate.of(2026, 7, 20),
            startMinutes = 8 * 60,
            endMinutes = 16 * 60 + 30,
            breakMinutes = 30
        )

        assertEquals(8 * 60, entry.workedMinutes)
    }

    @Test
    fun calculatesShiftEndingNextDay() {
        val entry = WorkEntry(
            date = LocalDate.of(2026, 7, 20),
            startMinutes = 22 * 60,
            endMinutes = 6 * 60,
            breakMinutes = 30
        )

        assertTrue(entry.endsNextDay)
        assertEquals(7 * 60 + 30, entry.workedMinutes)
    }

    @Test
    fun nonWorkDayDoesNotAddWorkedMinutes() {
        val entry = WorkEntry(
            date = LocalDate.of(2026, 7, 20),
            type = WorkDayType.VACATION
        )

        assertEquals(0, entry.workedMinutes)
    }
}
