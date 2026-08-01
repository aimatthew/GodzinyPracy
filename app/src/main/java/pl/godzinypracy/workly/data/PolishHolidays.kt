package pl.godzinypracy.workly.data

import java.time.LocalDate

fun isPolishPublicHoliday(date: LocalDate): Boolean = polishHolidayName(date) != null

fun polishHolidayName(date: LocalDate): String? {
    val easterSunday = easterSunday(date.year)
    return when (date) {
        LocalDate.of(date.year, 1, 1) -> "Nowy Rok"
        LocalDate.of(date.year, 1, 6) -> "Święto Trzech Króli"
        easterSunday -> "Wielkanoc"
        easterSunday.plusDays(1) -> "Poniedziałek Wielkanocny"
        LocalDate.of(date.year, 5, 1) -> "Święto Pracy"
        LocalDate.of(date.year, 5, 3) -> "Święto Konstytucji 3 Maja"
        easterSunday.plusDays(49) -> "Zielone Świątki"
        easterSunday.plusDays(60) -> "Boże Ciało"
        LocalDate.of(date.year, 8, 15) -> "Wniebowzięcie NMP"
        LocalDate.of(date.year, 11, 1) -> "Wszystkich Świętych"
        LocalDate.of(date.year, 11, 11) -> "Narodowe Święto Niepodległości"
        LocalDate.of(date.year, 12, 24) -> "Wigilia Bożego Narodzenia"
        LocalDate.of(date.year, 12, 25) -> "Boże Narodzenie"
        LocalDate.of(date.year, 12, 26) -> "Drugi dzień Bożego Narodzenia"
        else -> null
    }
}

private fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}
