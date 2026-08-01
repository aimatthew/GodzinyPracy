package pl.godzinypracy.workly.data

import java.time.LocalDate

enum class WorkDayType(val label: String) {
    WORK("Praca"),
    VACATION("Urlop"),
    SICK_LEAVE("Zwolnienie"),
    DAY_OFF("Dzień wolny")
}

data class WorkEntry(
    val date: LocalDate,
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 16 * 60,
    val breakMinutes: Int = 0,
    val type: WorkDayType = WorkDayType.WORK,
    val hourlyRateOverrideCents: Int? = null,
    val note: String = ""
) {
    val workedMinutes: Int
        get() {
            if (type != WorkDayType.WORK) return 0
            val normalizedEnd = if (endMinutes <= startMinutes) endMinutes + 24 * 60 else endMinutes
            return (normalizedEnd - startMinutes - breakMinutes).coerceAtLeast(0)
        }

    val endsNextDay: Boolean
        get() = type == WorkDayType.WORK && endMinutes <= startMinutes
}

fun formatMinutes(minutes: Int, compact: Boolean = false): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val remaining = safeMinutes % 60
    return when {
        compact && remaining == 0 -> "${hours}h"
        compact -> "${hours}h ${remaining}m"
        remaining == 0 -> "$hours godz."
        else -> "$hours godz. $remaining min"
    }
}

fun formatClock(minutes: Int): String = "%02d:%02d".format(
    (minutes / 60).mod(24),
    minutes.mod(60)
)
