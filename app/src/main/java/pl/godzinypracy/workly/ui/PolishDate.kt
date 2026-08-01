package pl.godzinypracy.workly.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PolishLocale = Locale.forLanguageTag("pl-PL")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", PolishLocale)

fun LocalDate.formatPolishDate(): String = format(FullDateFormatter).replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(PolishLocale) else it.toString()
}
