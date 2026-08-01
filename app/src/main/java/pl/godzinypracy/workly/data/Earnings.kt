package pl.godzinypracy.workly.data

import java.text.NumberFormat
import java.util.Locale

fun calculateEarningsCents(workedMinutes: Int, hourlyRateCents: Int): Long {
    if (workedMinutes <= 0 || hourlyRateCents <= 0) return 0L
    return (workedMinutes.toLong() * hourlyRateCents + 30L) / 60L
}

fun formatMoney(cents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pl-PL"))
    return formatter.format(cents.coerceAtLeast(0L) / 100.0)
}

fun formatHourlyRateInput(cents: Int): String {
    if (cents <= 0) return ""
    val whole = cents / 100
    val fraction = cents % 100
    return if (fraction == 0) whole.toString() else "$whole,${fraction.toString().padStart(2, '0')}"
}

fun parseHourlyRateCents(value: String): Int? {
    val normalized = value.trim().replace(',', '.').removeSuffix(".")
    if (normalized.isEmpty()) return 0
    val amount = normalized.toBigDecimalOrNull() ?: return null
    if (amount.signum() < 0) return null
    return runCatching {
        amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact()
            .coerceIn(0, 1_000_000)
    }.getOrNull()
}

fun WorkEntry.effectiveHourlyRateCents(defaultHourlyRateCents: Int): Int =
    (hourlyRateOverrideCents ?: defaultHourlyRateCents).coerceIn(0, 1_000_000)

fun calculateEntryEarningsCents(entry: WorkEntry, defaultHourlyRateCents: Int): Long =
    calculateEarningsCents(
        workedMinutes = entry.workedMinutes,
        hourlyRateCents = entry.effectiveHourlyRateCents(defaultHourlyRateCents)
    )

fun calculateEntriesEarningsCents(entries: Iterable<WorkEntry>, defaultHourlyRateCents: Int): Long =
    entries.sumOf { entry ->
        calculateEntryEarningsCents(entry, defaultHourlyRateCents)
    }

fun formatMoneyCompact(cents: Long): String {
    val safeCents = cents.coerceAtLeast(0L)
    val whole = safeCents / 100
    val fraction = safeCents % 100
    return if (fraction == 0L) {
        "$whole zł"
    } else {
        "$whole,${fraction.toString().padStart(2, '0')} zł"
    }
}
